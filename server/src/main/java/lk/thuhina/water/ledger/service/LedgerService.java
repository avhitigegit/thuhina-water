package lk.thuhina.water.ledger.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.ledger.model.Posting;
import lk.thuhina.water.ledger.model.SourceType;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ledger Engine (design 5.1). Every change to bottle stock or product stock is a {@link Posting} of {@link Effect}s:
 * <ol>
 *   <li>lock the affected rows in a fixed order – stock by bottle type + bucket, then products by code – with
 *       {@code SELECT … FOR UPDATE}, so two users can never both take the last bottle;</li>
 *   <li>check that nothing goes below zero (BR-12) – otherwise {@code STOCK_NEGATIVE} with a clear message and no change;</li>
 *   <li>apply the changes and write {@code ledger_effect} and {@code stock_movement} rows.</li>
 * </ol>
 * It always runs inside the caller's transaction (all or nothing); called without one it fails.
 * Uses SQL directly: JPA entities of products loaded earlier in the same transaction are not refreshed.
 */
@Service
public class LedgerService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public LedgerService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Posts the effects (see the class comment). Zero effects are ignored; effects on the same row are added up. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void post(Posting posting) {
        Map<StockKey, Integer> stock = new TreeMap<>();
        Map<String, Integer> products = new TreeMap<>();
        for (Effect e : posting.effects()) {
            if (e.delta() == 0) {
                continue;
            }
            if (e.target() == Effect.Target.STOCK) {
                stock.merge(new StockKey(e.bottleTypeCode(), e.bucket()), e.delta(), Integer::sum);
            } else {
                products.merge(e.productCode(), e.delta(), Integer::sum);
            }
        }
        stock.values().removeIf(d -> d == 0);
        products.values().removeIf(d -> d == 0);
        if (stock.isEmpty() && products.isEmpty()) {
            return;
        }

        // 1. Lock in a fixed order: stock rows (bottle type, bucket), then products (code).
        Map<StockKey, Integer> stockNow = lockStock(stock.keySet());
        Map<String, ProductRow> productNow = lockProducts(products.keySet());

        // 2. Nothing may go below zero.
        Map<String, BigDecimal> litres = litres(stock.keySet());
        List<LedgerRules.Line> lines = new ArrayList<>();
        stock.forEach((k, d) -> lines.add(new LedgerRules.Line(
                k.bucket().label() + " (" + LedgerRules.bottleLabel(litres.get(k.bottleTypeCode())) + ")", stockNow.get(k), d)));
        products.forEach((code, d) -> {
            ProductRow p = productNow.get(code);
            lines.add(new LedgerRules.Line(p.name() + " stock", p.qty(), d));
        });
        List<String> problems = LedgerRules.problems(lines);
        if (!problems.isEmpty()) {
            throw new BusinessException(LedgerRules.STOCK_NEGATIVE, LedgerRules.message(problems),
                    Map.of("problems", problems));
        }

        // 3. Apply, then write the effects and the movement history.
        String user = CurrentUser.usernameOrSystem();
        Timestamp now = Timestamp.from(Instant.now(clock));
        stock.forEach((k, d) -> jdbc.update(
                "update stock_balance set qty = qty + :d where bottle_type_code = :bt and bucket = :bucket",
                new MapSqlParameterSource("d", d).addValue("bt", k.bottleTypeCode()).addValue("bucket", k.bucket().name())));
        products.forEach((code, d) -> jdbc.update("update product set stock_qty = stock_qty + :d where code = :code",
                new MapSqlParameterSource("d", d).addValue("code", code)));

        stock.forEach((k, d) -> {
            insertEffect(posting, "STOCK", k.bottleTypeCode(), null, k.bucket().name(), d, user, now);
            insertMovement(posting, k.bottleTypeCode(), k.bucket().name(), d, user, now);
        });
        products.forEach((code, d) -> {
            insertEffect(posting, "PRODUCT", null, code, "STOCK", d, user, now);
            insertMovement(posting, code, "STOCK", d, user, now);
        });
    }

    /**
     * Posts the opposite of everything a source posted (design 5.1 reverse), under the reversal's own source and
     * description, through the same checks.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverse(SourceType originalType, long originalId, Posting reversal) {
        List<Effect> original = effectsOf(originalType, originalId);
        if (original.isEmpty()) {
            throw new BusinessException("NOTHING_TO_REVERSE", "Nothing was posted for this document.");
        }
        List<Effect> negated = new ArrayList<>(reversal.effects());
        original.forEach(e -> negated.add(e.negated()));
        post(new Posting(reversal.sourceType(), reversal.sourceId(), reversal.docNo(), reversal.date(),
                reversal.description(), negated));
    }

    /** The effects a source posted (stock and product). */
    @Transactional(readOnly = true)
    public List<Effect> effectsOf(SourceType type, long sourceId) {
        return jdbc.query("select target, bottle_type_code, product_code, field, delta from ledger_effect "
                        + "where source_type = :type and source_id = :id order by id",
                new MapSqlParameterSource("type", type.name()).addValue("id", sourceId),
                (rs, i) -> "PRODUCT".equals(rs.getString("target"))
                        ? Effect.product(rs.getString("product_code"), rs.getInt("delta"))
                        : Effect.stock(rs.getString("bottle_type_code"), Bucket.valueOf(rs.getString("field")), rs.getInt("delta")));
    }

    /** Current bottles in a bucket, locked until the end of the transaction (e.g. a physical count compares with it). */
    @Transactional(propagation = Propagation.MANDATORY)
    public int lockedStock(String bottleTypeCode, Bucket bucket) {
        return lockStock(List.of(new StockKey(bottleTypeCode, bucket))).get(new StockKey(bottleTypeCode, bucket));
    }

    /** Current product stock, locked until the end of the transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public int lockedProductStock(String productCode) {
        return lockProducts(List.of(productCode)).get(productCode).qty();
    }

    /** All five buckets of a bottle type (not locked). */
    @Transactional(readOnly = true)
    public Map<Bucket, Integer> balances(String bottleTypeCode) {
        Map<Bucket, Integer> out = new LinkedHashMap<>();
        jdbc.query("select bucket, qty from stock_balance where bottle_type_code = :bt",
                new MapSqlParameterSource("bt", bottleTypeCode),
                rs -> {
                    out.put(Bucket.valueOf(rs.getString("bucket")), rs.getInt("qty"));
                });
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private record StockKey(String bottleTypeCode, Bucket bucket) implements Comparable<StockKey> {
        @Override
        public int compareTo(StockKey o) {
            int c = bottleTypeCode.compareTo(o.bottleTypeCode);
            return c != 0 ? c : bucket.name().compareTo(o.bucket.name());
        }
    }

    private record ProductRow(String name, int qty) {
    }

    private Map<StockKey, Integer> lockStock(Iterable<StockKey> keys) {
        List<String> wanted = new ArrayList<>();
        keys.forEach(k -> wanted.add(k.bottleTypeCode() + "|" + k.bucket().name()));
        Map<StockKey, Integer> out = new HashMap<>();
        if (wanted.isEmpty()) {
            return out;
        }
        jdbc.query("select bottle_type_code, bucket, qty from stock_balance "
                        + "where bottle_type_code || '|' || bucket in (:keys) order by bottle_type_code, bucket for update",
                new MapSqlParameterSource("keys", wanted),
                rs -> {
                    out.put(new StockKey(rs.getString("bottle_type_code"), Bucket.valueOf(rs.getString("bucket"))), rs.getInt("qty"));
                });
        keys.forEach(k -> {
            if (!out.containsKey(k)) {
                throw new ValidationException("bottleTypeCode", "Unknown bottle type " + k.bottleTypeCode() + ".");
            }
        });
        return out;
    }

    private Map<String, ProductRow> lockProducts(Iterable<String> codes) {
        List<String> wanted = new ArrayList<>();
        codes.forEach(wanted::add);
        Map<String, ProductRow> out = new HashMap<>();
        if (wanted.isEmpty()) {
            return out;
        }
        jdbc.query("select code, name, stock_qty from product where code in (:codes) order by code for update",
                new MapSqlParameterSource("codes", wanted),
                rs -> {
                    out.put(rs.getString("code"), new ProductRow(rs.getString("name"), rs.getInt("stock_qty")));
                });
        wanted.forEach(c -> {
            if (!out.containsKey(c)) {
                throw new ValidationException("productCode", "Unknown product " + c + ".");
            }
        });
        return out;
    }

    private Map<String, BigDecimal> litres(Iterable<StockKey> keys) {
        List<String> codes = new ArrayList<>();
        keys.forEach(k -> codes.add(k.bottleTypeCode()));
        Map<String, BigDecimal> out = new HashMap<>();
        if (codes.isEmpty()) {
            return out;
        }
        jdbc.query("select code, litres from bottle_type where code in (:codes)", new MapSqlParameterSource("codes", codes),
                rs -> {
                    out.put(rs.getString("code"), rs.getBigDecimal("litres"));
                });
        return out;
    }

    private void insertEffect(Posting p, String target, String bottle, String product, String field, int delta,
                              String user, Timestamp now) {
        jdbc.update("insert into ledger_effect (source_type, source_id, doc_no, target, bottle_type_code, product_code, field, "
                        + "delta, created_at, created_by) values (:st, :sid, :doc, :target, :bt, :pc, :field, :d, :at, :by)",
                new MapSqlParameterSource("st", p.sourceType().name()).addValue("sid", p.sourceId()).addValue("doc", p.docNo())
                        .addValue("target", target).addValue("bt", bottle).addValue("pc", product).addValue("field", field)
                        .addValue("d", delta).addValue("at", now).addValue("by", user));
    }

    private void insertMovement(Posting p, String item, String bucket, int delta, String user, Timestamp now) {
        String description = p.description().length() > 300 ? p.description().substring(0, 300) : p.description();
        jdbc.update("insert into stock_movement (movement_date, source_type, source_id, doc_no, item_code, bucket, qty_delta, "
                        + "description, created_at, created_by) values (:date, :st, :sid, :doc, :item, :bucket, :d, :desc, :at, :by)",
                new MapSqlParameterSource("date", p.date()).addValue("st", p.sourceType().name()).addValue("sid", p.sourceId())
                        .addValue("doc", p.docNo()).addValue("item", item).addValue("bucket", bucket).addValue("d", delta)
                        .addValue("desc", description).addValue("at", now).addValue("by", user));
    }
}
