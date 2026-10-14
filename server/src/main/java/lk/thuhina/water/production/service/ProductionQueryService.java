package lk.thuhina.water.production.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.production.dto.ProductionDtos.BatchView;
import lk.thuhina.water.production.dto.ProductionDtos.DispatchView;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryBottleStock;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryLink;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryMonth;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryOwed;
import lk.thuhina.water.production.dto.ProductionDtos.FactoryPaymentRow;
import lk.thuhina.water.production.dto.ProductionDtos.ProductionSummary;
import lk.thuhina.water.production.dto.ProductionDtos.ReturnView;
import lk.thuhina.water.production.rules.ProductionRules;
import lk.thuhina.water.sales.model.PayMethod;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filling Factory reads (FR-25, FR-26): batches with what came back, dispatches (for the dispatch note), page KPIs, filling
 * cost by factory and month, payments; and the "At factory now" / "Owed" columns of the Factories tab. Plain SQL with
 * aggregates – a few batches a week.
 */
@Service
public class ProductionQueryService {

    private static final String BATCHES = "select b.id, b.batch_no, b.dispatch_id, d.dispatch_no, b.dispatch_date, b.factory_id, f.code as f_code, "
            + "f.name as f_name, b.bottle_type_code, t.name as bt_name, t.litres, b.qty_sent, b.charge_per_bottle, b.vehicle_no, b.note, "
            + "coalesce(r.filled, 0) as filled, coalesce(r.rejected, 0) as rejected, coalesce(r.cost, 0) as cost "
            + "from factory_batch b join factory_dispatch d on d.id = b.dispatch_id join factory f on f.id = b.factory_id "
            + "join bottle_type t on t.code = b.bottle_type_code "
            + "left join (select batch_id, sum(filled_qty) as filled, sum(rejected_qty) as rejected, sum(cost) as cost "
            + "from factory_return group by batch_id) r on r.batch_id = b.id ";

    private final NamedParameterJdbcTemplate jdbc;

    public ProductionQueryService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Batches newest first; {@code factoryId} and {@code open} (still at the factory) optional. */
    @Transactional(readOnly = true)
    public List<BatchView> batches(Long factoryId, boolean open) {
        StringBuilder sql = new StringBuilder(BATCHES).append("where true");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (factoryId != null) {
            sql.append(" and b.factory_id = :factory");
            p.addValue("factory", factoryId);
        }
        if (open) {
            sql.append(" and b.qty_sent - coalesce(r.filled, 0) - coalesce(r.rejected, 0) > 0");
        }
        sql.append(" order by b.dispatch_date desc, b.id desc");
        return withReturns(jdbc.query(sql.toString(), p, (rs, i) -> batch(rs)));
    }

    @Transactional(readOnly = true)
    public BatchView batch(long id) {
        List<BatchView> list = withReturns(jdbc.query(BATCHES + "where b.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> batch(rs)));
        if (list.isEmpty()) {
            throw new NotFoundException("Batch", id);
        }
        return list.getFirst();
    }

    /** A dispatch with its batches (FD-0001). */
    @Transactional(readOnly = true)
    public DispatchView dispatch(String dispatchNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("select d.id, d.dispatch_no, d.dispatch_date, d.vehicle_no, d.note, d.created_by, "
                + "f.id as f_id, f.code as f_code, f.name as f_name, f.address, f.contact, f.phone from factory_dispatch d "
                + "join factory f on f.id = d.factory_id where d.dispatch_no = :no", new MapSqlParameterSource("no", dispatchNo));
        if (rows.isEmpty()) {
            throw new NotFoundException("Dispatch", dispatchNo);
        }
        Map<String, Object> d = rows.getFirst();
        long id = ((Number) d.get("id")).longValue();
        List<BatchView> batches = withReturns(jdbc.query(BATCHES + "where b.dispatch_id = :id order by b.id",
                new MapSqlParameterSource("id", id), (rs, i) -> batch(rs)));
        String by = (String) d.get("created_by");
        return new DispatchView(id, dispatchNo, ((java.sql.Date) d.get("dispatch_date")).toLocalDate(),
                new FactoryLink(((Number) d.get("f_id")).longValue(), (String) d.get("f_code"), (String) d.get("f_name")),
                (String) d.get("address"), (String) d.get("contact"), (String) d.get("phone"), (String) d.get("vehicle_no"),
                (String) d.get("note"), userNames(Set.of(by)).get(by), batches.stream().mapToInt(BatchView::qtySent).sum(), batches);
    }

    /** KPIs: empty and at factory per active bottle type (largest first), bottles at and money owed to each factory. */
    @Transactional(readOnly = true)
    public ProductionSummary summary() {
        List<FactoryBottleStock> bottles = jdbc.query("select t.code, t.name, t.litres, "
                        + "coalesce(sum(s.qty) filter (where s.bucket = 'EMPTY'), 0) as empty, "
                        + "coalesce(sum(s.qty) filter (where s.bucket = 'FACTORY'), 0) as factory "
                        + "from bottle_type t left join stock_balance s on s.bottle_type_code = t.code where t.active "
                        + "group by t.code, t.name, t.litres order by t.litres desc",
                (rs, i) -> new FactoryBottleStock(rs.getString("code"), LedgerRules.bottleLabel(rs.getBigDecimal("litres")),
                        rs.getString("name"), rs.getInt("empty"), rs.getInt("factory")));
        Map<Long, Integer> at = atFactoryByFactory();
        Map<Long, BigDecimal> owed = owedByFactory();
        List<FactoryOwed> factories = jdbc.query("select id, code, name, active from factory order by code", (rs, i) -> {
            long id = rs.getLong("id");
            return new FactoryOwed(id, rs.getString("code"), rs.getString("name"), at.getOrDefault(id, 0),
                    owed.getOrDefault(id, Money.ZERO), rs.getBoolean("active"));
        }).stream().filter(f -> f.active() || f.atFactory() > 0 || f.owed().signum() != 0).toList();
        return new ProductionSummary(bottles, factories);
    }

    /** Bottles still at each factory (all bottle types). */
    @Transactional(readOnly = true)
    public Map<Long, Integer> atFactoryByFactory() {
        Map<Long, Integer> out = new HashMap<>();
        jdbc.query("select b.factory_id, sum(b.qty_sent - coalesce(r.back, 0)) as left_qty from factory_batch b "
                + "left join (select batch_id, sum(filled_qty + rejected_qty) as back from factory_return group by batch_id) r "
                + "on r.batch_id = b.id group by b.factory_id", rs -> {
            out.put(rs.getLong("factory_id"), rs.getInt("left_qty"));
        });
        return out;
    }

    /** Filling cost − payments per factory. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> owedByFactory() {
        Map<Long, BigDecimal> out = new HashMap<>();
        jdbc.query("select factory_id, sum(v) as owed from (select b.factory_id, r.cost as v from factory_return r "
                + "join factory_batch b on b.id = r.batch_id union all select factory_id, -amount from factory_payment) x "
                + "group by factory_id", rs -> {
            out.put(rs.getLong("factory_id"), Money.of(rs.getBigDecimal("owed")));
        });
        return out;
    }

    /** Filling cost by factory and month of return, newest month first, with what was paid for that month. */
    @Transactional(readOnly = true)
    public List<FactoryMonth> months() {
        record Key(long factoryId, String period) {
        }
        Map<Key, Object[]> rows = new LinkedHashMap<>();
        Map<Long, String> names = new HashMap<>();
        jdbc.query("select id, name from factory", rs -> {
            names.put(rs.getLong("id"), rs.getString("name"));
        });
        jdbc.query("select b.factory_id, to_char(r.return_date, 'YYYY-MM') as period, sum(r.filled_qty) as filled, "
                + "sum(r.rejected_qty) as rejected, sum(r.cost) as cost from factory_return r join factory_batch b on b.id = r.batch_id "
                + "group by b.factory_id, period", rs -> {
            rows.put(new Key(rs.getLong("factory_id"), rs.getString("period")),
                    new Object[] {rs.getInt("filled"), rs.getInt("rejected"), Money.of(rs.getBigDecimal("cost")), Money.ZERO});
        });
        jdbc.query("select factory_id, period, sum(amount) as paid from factory_payment group by factory_id, period", rs -> {
            Object[] r = rows.computeIfAbsent(new Key(rs.getLong("factory_id"), rs.getString("period")),
                    k -> new Object[] {0, 0, Money.ZERO, Money.ZERO});
            r[3] = Money.of(rs.getBigDecimal("paid"));
        });
        List<FactoryMonth> out = new ArrayList<>();
        rows.forEach((k, r) -> {
            BigDecimal cost = (BigDecimal) r[2];
            BigDecimal paid = (BigDecimal) r[3];
            String status = ProductionRules.paymentStatus(cost, paid);
            out.add(new FactoryMonth(k.factoryId(), names.get(k.factoryId()), k.period(), ProductionRules.periodLabel(k.period()),
                    (int) r[0], (int) r[1], cost, paid, cost.subtract(paid).max(Money.ZERO), status, ProductionRules.paymentStatusLabel(status)));
        });
        out.sort(Comparator.comparing(FactoryMonth::period).reversed().thenComparing(FactoryMonth::factoryName));
        return out;
    }

    /** Factory payments, newest first. */
    @Transactional(readOnly = true)
    public List<FactoryPaymentRow> payments() {
        List<Object[]> raw = jdbc.query("select p.id, p.fp_no, p.pay_date, p.factory_id, f.code, f.name, p.period, p.method, p.reference, "
                        + "p.amount, p.note, p.created_by from factory_payment p join factory f on f.id = p.factory_id "
                        + "order by p.pay_date desc, p.id desc",
                (rs, i) -> new Object[] {rs.getLong("id"), rs.getString("fp_no"), rs.getDate("pay_date").toLocalDate(),
                        new FactoryLink(rs.getLong("factory_id"), rs.getString("code"), rs.getString("name")), rs.getString("period"),
                        PayMethod.valueOf(rs.getString("method")), rs.getString("reference"), rs.getBigDecimal("amount"),
                        rs.getString("note"), rs.getString("created_by")});
        Map<String, String> names = userNames(raw.stream().map(r -> (String) r[9]).toList());
        return raw.stream().map(r -> {
            PayMethod m = (PayMethod) r[5];
            return new FactoryPaymentRow((long) r[0], (String) r[1], (java.time.LocalDate) r[2], (FactoryLink) r[3], (String) r[4],
                    ProductionRules.periodLabel((String) r[4]), m.name(), m.label(), (String) r[6], Money.of((BigDecimal) r[7]),
                    (String) r[8], names.get((String) r[9]));
        }).toList();
    }

    @Transactional(readOnly = true)
    public FactoryPaymentRow payment(long id) {
        return payments().stream().filter(p -> p.id() == id).findFirst().orElseThrow(() -> new NotFoundException("Factory payment", id));
    }

    // ------------------------------------------------------------------ helpers

    private static BatchView batch(ResultSet rs) throws SQLException {
        int sent = rs.getInt("qty_sent");
        int filled = rs.getInt("filled");
        int rejected = rs.getInt("rejected");
        String status = ProductionRules.batchStatus(sent, filled, rejected);
        return new BatchView(rs.getLong("id"), rs.getString("batch_no"), rs.getLong("dispatch_id"), rs.getString("dispatch_no"),
                rs.getDate("dispatch_date").toLocalDate(), new FactoryLink(rs.getLong("factory_id"), rs.getString("f_code"), rs.getString("f_name")),
                rs.getString("bottle_type_code"), LedgerRules.bottleLabel(rs.getBigDecimal("litres")), rs.getString("bt_name"), sent, filled,
                rejected, ProductionRules.stillAtFactory(sent, filled, rejected), Money.of(rs.getBigDecimal("charge_per_bottle")),
                Money.of(rs.getBigDecimal("cost")), status, ProductionRules.batchStatusLabel(status), rs.getString("vehicle_no"),
                rs.getString("note"), List.of());
    }

    /** Adds the returns of the batches (one query). */
    private List<BatchView> withReturns(List<BatchView> batches) {
        if (batches.isEmpty()) {
            return batches;
        }
        Map<Long, List<ReturnView>> byBatch = new HashMap<>();
        List<Object[]> raw = jdbc.query("select id, batch_id, return_no, return_date, filled_qty, rejected_qty, reject_reason, cost, note, "
                        + "created_by from factory_return where batch_id in (:ids) order by return_date, id",
                new MapSqlParameterSource("ids", batches.stream().map(BatchView::id).toList()),
                (rs, i) -> new Object[] {rs.getLong("batch_id"), rs.getLong("id"), rs.getString("return_no"), rs.getDate("return_date").toLocalDate(),
                        rs.getInt("filled_qty"), rs.getInt("rejected_qty"), rs.getString("reject_reason"), rs.getBigDecimal("cost"),
                        rs.getString("note"), rs.getString("created_by")});
        Map<String, String> names = userNames(raw.stream().map(r -> (String) r[9]).toList());
        for (Object[] r : raw) {
            byBatch.computeIfAbsent((Long) r[0], k -> new ArrayList<>()).add(new ReturnView((long) r[1], (String) r[2],
                    (java.time.LocalDate) r[3], (int) r[4], (int) r[5], (String) r[6], Money.of((BigDecimal) r[7]), (String) r[8],
                    names.get((String) r[9])));
        }
        return batches.stream().map(b -> new BatchView(b.id(), b.batchNo(), b.dispatchId(), b.dispatchNo(), b.date(), b.factory(),
                b.bottleTypeCode(), b.bottleLabel(), b.bottleName(), b.qtySent(), b.filled(), b.rejected(), b.atFactory(), b.charge(),
                b.cost(), b.status(), b.statusLabel(), b.vehicleNo(), b.note(), byBatch.getOrDefault(b.id(), List.of()))).toList();
    }

    /** Full names of users; usernames without a user (seed, system) are shown as they are. */
    private Map<String, String> userNames(Collection<String> usernames) {
        Set<String> wanted = new HashSet<>();
        usernames.forEach(u -> {
            if (u != null) {
                wanted.add(u);
            }
        });
        Map<String, String> out = new HashMap<>();
        if (!wanted.isEmpty()) {
            jdbc.query("select username, full_name from app_user where username in (:u)", new MapSqlParameterSource("u", wanted), rs -> {
                out.put(rs.getString("username"), rs.getString("full_name"));
            });
        }
        wanted.forEach(u -> out.putIfAbsent(u, u));
        return out;
    }
}
