package lk.thuhina.water.inventory.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.PageResponse;
import lk.thuhina.water.inventory.dto.StockDtos.BottleStock;
import lk.thuhina.water.inventory.dto.StockDtos.DamageListRow;
import lk.thuhina.water.inventory.dto.StockDtos.LowStockAlert;
import lk.thuhina.water.inventory.dto.StockDtos.MovementRow;
import lk.thuhina.water.inventory.dto.StockDtos.ProductStock;
import lk.thuhina.water.inventory.dto.StockDtos.StockOverview;
import lk.thuhina.water.inventory.model.DamageRecord;
import lk.thuhina.water.inventory.model.MinStockLevel;
import lk.thuhina.water.inventory.model.StockAdjustment;
import lk.thuhina.water.inventory.repository.DamageRecordRepository;
import lk.thuhina.water.inventory.repository.MinStockLevelRepository;
import lk.thuhina.water.inventory.repository.StockAdjustmentRepository;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.Product;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.ProductRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stock page reads (M04 S3): stock by status, low-stock alerts, movements, damage &amp; adjustments list. */
@Service
public class StockQueryService {

    public static final int MAX_PAGE_SIZE = 200;

    private final NamedParameterJdbcTemplate jdbc;
    private final BottleTypeRepository bottleTypes;
    private final ProductRepository products;
    private final MinStockLevelRepository minLevels;
    private final DamageRecordRepository damages;
    private final StockAdjustmentRepository adjustments;
    private final AppUserRepository users;

    public StockQueryService(NamedParameterJdbcTemplate jdbc, BottleTypeRepository bottleTypes, ProductRepository products,
                             MinStockLevelRepository minLevels, DamageRecordRepository damages,
                             StockAdjustmentRepository adjustments, AppUserRepository users) {
        this.jdbc = jdbc;
        this.bottleTypes = bottleTypes;
        this.products = products;
        this.minLevels = minLevels;
        this.damages = damages;
        this.adjustments = adjustments;
        this.users = users;
    }

    /** Active bottle types with their five buckets, products that are active or still in stock, and alerts. */
    @Transactional(readOnly = true)
    public StockOverview overview() {
        List<BottleStock> bottles = bottleRows();
        List<ProductStock> productRows = products.findAllByOrderByCodeAsc().stream()
                .filter(p -> p.isActive() || p.getStockQty() > 0)
                .map(StockQueryService::productRow)
                .toList();
        return new StockOverview(bottles, productRows, alerts(bottles));
    }

    /** FR-31: active bottle types whose filled stock is below their minimum level. */
    @Transactional(readOnly = true)
    public List<LowStockAlert> alerts() {
        return alerts(bottleRows());
    }

    /** Movements tab: postings per item, newest first, filtered by item and text (document number or description). */
    @Transactional(readOnly = true)
    public PageResponse<MovementRow> movements(String item, String q, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_PAGE_SIZE, Math.max(1, size));
        StringBuilder where = new StringBuilder(" where 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        if (item != null && !item.isBlank()) {
            where.append(" and m.item_code = :item");
            p.addValue("item", item.trim());
        }
        if (q != null && !q.isBlank()) {
            where.append(" and lower(coalesce(m.doc_no, '') || ' ' || m.description) like :q escape '\\'");
            p.addValue("q", "%" + q.trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        String group = " group by m.movement_date, m.source_type, m.source_id, m.doc_no, m.description, m.item_code, m.created_by";
        Long total = jdbc.queryForObject("select count(*) from (select 1 from stock_movement m" + where + group + ") x", p, Long.class);

        p.addValue("limit", safeSize).addValue("offset", (long) safePage * safeSize);
        String sql = "select m.movement_date, m.source_type, m.doc_no, m.description, m.item_code, m.created_by, min(m.created_at) as created_at, "
                + sum("EMPTY") + " as empty, " + sum("FACTORY") + " as factory, " + sum("FILLED") + " as filled, "
                + sum("CUSTOMERS") + " as customers, " + sum("WRITTEN_OFF") + " as written_off, " + sum("STOCK") + " as product "
                + "from stock_movement m" + where + group + " order by max(m.id) desc limit :limit offset :offset";
        Map<String, String> itemNames = itemNames();
        List<MovementRow> raw = jdbc.query(sql, p, (rs, i) -> new MovementRow(
                rs.getObject("movement_date", java.time.LocalDate.class), rs.getString("source_type"), rs.getString("doc_no"),
                rs.getString("description"), rs.getString("item_code"),
                itemNames.getOrDefault(rs.getString("item_code"), rs.getString("item_code")),
                rs.getInt("empty"), rs.getInt("factory"), rs.getInt("filled"), rs.getInt("customers"), rs.getInt("written_off"),
                rs.getInt("product"), rs.getString("created_by"), null, rs.getTimestamp("created_at").toInstant()));
        Map<String, String> names = fullNames(raw.stream().map(MovementRow::createdBy).collect(Collectors.toSet()));
        List<MovementRow> items = raw.stream().map(m -> new MovementRow(m.date(), m.sourceType(), m.docNo(), m.description(),
                m.itemCode(), m.itemName(), m.empty(), m.factory(), m.filled(), m.customers(), m.writtenOff(), m.product(),
                m.createdBy(), names.get(m.createdBy()), m.createdAt())).toList();
        return new PageResponse<>(items, safePage, safeSize, total == null ? 0 : total);
    }

    /** Damage &amp; adjustments tab: damaged bottles, lost bottles and count adjustments, newest first. */
    @Transactional(readOnly = true)
    public List<DamageListRow> damagesAndAdjustments() {
        Map<String, String> itemNames = itemNames();
        List<DamageRecord> dmg = damages.findAllByOrderByDamageDateDescIdDesc();
        List<StockAdjustment> adj = adjustments.findAllByOrderByAdjDateDescIdDesc();
        Set<String> who = new HashSet<>();
        dmg.forEach(d -> who.add(d.getCreatedBy()));
        adj.forEach(a -> who.add(a.getCreatedBy()));
        Map<String, String> names = fullNames(who);

        record Sortable(DamageListRow row, java.time.Instant at) {
        }
        List<Sortable> rows = new ArrayList<>();
        for (DamageRecord d : dmg) {
            rows.add(new Sortable(new DamageListRow(d.getDamageNo(), d.getDamageDate(), "DAMAGED", d.getBottleTypeCode(),
                    itemNames.getOrDefault(d.getBottleTypeCode(), d.getBottleTypeCode()), d.getQty(),
                    d.getResponsibility().name(), d.getLocation() == null ? null : d.getLocation().label(), null,
                    d.getReason(), d.getCreatedBy(), names.get(d.getCreatedBy()), d.isReversed()), d.getCreatedAt()));
        }
        for (StockAdjustment a : adj) {
            boolean lost = a.getMode() == StockAdjustment.Mode.LOST;
            String where = "STOCK".equals(a.getBucket()) ? "Product stock" : Bucket.valueOf(a.getBucket()).label();
            rows.add(new Sortable(new DamageListRow(a.getAdjNo(), a.getAdjDate(), lost ? "LOST" : "COUNT", a.getItemCode(),
                    itemNames.getOrDefault(a.getItemCode(), a.getItemCode()), lost ? -a.getDelta() : a.getDelta(),
                    lost ? "COMPANY" : null, where, null, a.getReason(), a.getCreatedBy(), names.get(a.getCreatedBy()), false),
                    a.getCreatedAt()));
        }
        rows.sort((x, y) -> {
            int c = y.row().date().compareTo(x.row().date());
            return c != 0 ? c : y.at().compareTo(x.at());
        });
        return rows.stream().map(Sortable::row).toList();
    }

    // ------------------------------------------------------------------ helpers

    private List<BottleStock> bottleRows() {
        Map<String, Map<Bucket, Integer>> balances = new HashMap<>();
        jdbc.query("select bottle_type_code, bucket, qty from stock_balance", rs -> {
            balances.computeIfAbsent(rs.getString("bottle_type_code"), k -> new EnumMap<>(Bucket.class))
                    .put(Bucket.valueOf(rs.getString("bucket")), rs.getInt("qty"));
        });
        Map<String, Integer> mins = minLevels.findAll().stream()
                .collect(Collectors.toMap(MinStockLevel::getBottleTypeCode, MinStockLevel::getMinFilled));
        List<BottleStock> out = new ArrayList<>();
        for (BottleType b : bottleTypes.findByActiveTrueOrderByLitresDesc()) {
            Map<Bucket, Integer> q = balances.getOrDefault(b.getCode(), Map.of());
            int empty = q.getOrDefault(Bucket.EMPTY, 0);
            int factory = q.getOrDefault(Bucket.FACTORY, 0);
            int filled = q.getOrDefault(Bucket.FILLED, 0);
            int customers = q.getOrDefault(Bucket.CUSTOMERS, 0);
            Integer min = mins.get(b.getCode());
            out.add(new BottleStock(b.getCode(), b.getName(), LedgerRules.bottleLabel(b.getLitres()), empty, factory, filled,
                    customers, q.getOrDefault(Bucket.WRITTEN_OFF, 0), empty + factory + filled + customers, min,
                    min != null && filled < min));
        }
        return out;
    }

    private static List<LowStockAlert> alerts(List<BottleStock> bottles) {
        return bottles.stream().filter(BottleStock::low).map(b -> new LowStockAlert(b.code(), b.name(), b.filled(),
                b.minFilled(), b.factory(), "Low stock: only " + b.filled() + " filled " + b.label() + " bottles in store (minimum "
                + b.minFilled() + "). " + b.factory() + (b.factory() == 1 ? " is" : " are") + " at the factory.")).toList();
    }

    private static ProductStock productRow(Product p) {
        BigDecimal value = p.getCostPrice() == null ? Money.ZERO : Money.times(p.getCostPrice(), p.getStockQty());
        return new ProductStock(p.getId(), p.getCode(), p.getName(), p.getStockQty(), p.getCostPrice(), value, p.isActive());
    }

    private static String sum(String bucket) {
        return "coalesce(sum(case when m.bucket = '" + bucket + "' then m.qty_delta end), 0)";
    }

    /** Item code → name: bottle types ("20L Bottle") and products. */
    private Map<String, String> itemNames() {
        Map<String, String> names = new HashMap<>();
        bottleTypes.findAll().forEach(b -> names.put(b.getCode(), b.getName()));
        products.findAll().forEach(p -> names.put(p.getCode(), p.getName()));
        return names;
    }

    private Map<String, String> fullNames(Set<String> usernames) {
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return users.findByUsernameIn(usernames).stream().collect(Collectors.toMap(AppUser::getUsername, AppUser::getFullName));
    }
}
