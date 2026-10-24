package lk.thuhina.water.sales.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.sales.dto.SalesDtos.BillLink;
import lk.thuhina.water.sales.dto.SalesDtos.EffectView;
import lk.thuhina.water.sales.dto.SalesDtos.NextPaperBill;
import lk.thuhina.water.sales.dto.SalesDtos.PaperBillCheck;
import lk.thuhina.water.sales.dto.SalesDtos.TxnDetail;
import lk.thuhina.water.sales.dto.SalesDtos.TxnListResponse;
import lk.thuhina.water.sales.dto.SalesDtos.TxnRow;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.sales.model.SalesTxn;
import lk.thuhina.water.sales.repository.SalesTxnLineRepository;
import lk.thuhina.water.sales.repository.SalesTxnRepository;
import lk.thuhina.water.sales.rules.SaleLine;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bill search, details and the paper bill check (FR-41). */
@Service
public class SalesQueryService {

    static final String ENTITY = "Bill";

    private static final String ROW_SELECT = "select t.id, t.bill_no, t.paper_bill_no, t.kind, t.txn_date, t.customer_id, c.code, "
            + "coalesce(c.name, t.walk_in_name) as party, t.amount, t.paid_on_bill, t.reversed_by_id, o.bill_no as reversal_of, "
            + "coalesce((select sum(l.filled) from sales_txn_line l where l.txn_id = t.id), 0) as filled, "
            + "coalesce((select sum(l.empties) from sales_txn_line l where l.txn_id = t.id), 0) as empties "
            + "from sales_txn t left join customer c on c.id = t.customer_id left join sales_txn o on o.id = t.reversal_of_id ";

    private final SalesTxnRepository txns;
    private final SalesTxnLineRepository lines;
    private final NamedParameterJdbcTemplate jdbc;

    public SalesQueryService(SalesTxnRepository txns, SalesTxnLineRepository lines, NamedParameterJdbcTemplate jdbc) {
        this.txns = txns;
        this.lines = lines;
        this.jdbc = jdbc;
    }

    /** Filters of the bills list; all optional. {@code q} = part of the system or the paper bill number. */
    public record Filter(String q, Long customerId, SalesKind kind, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public TxnListResponse list(Filter f, int page, int size) {
        int pageSize = Math.clamp(size, 1, 200);
        int pageNo = Math.max(page, 0);
        StringBuilder where = new StringBuilder("where 1 = 1");
        MapSqlParameterSource p = new MapSqlParameterSource();
        String q = Texts.blankToNull(f.q());
        if (q != null) {
            where.append(" and (upper(t.bill_no) like :q or upper(t.paper_bill_no) like :q)");
            p.addValue("q", "%" + q.toUpperCase().replace("%", "") + "%");
        }
        if (f.customerId() != null) {
            where.append(" and t.customer_id = :c");
            p.addValue("c", f.customerId());
        }
        if (f.kind() != null) {
            where.append(" and t.kind = :kind");
            p.addValue("kind", f.kind().name());
        }
        if (f.from() != null) {
            where.append(" and t.txn_date >= :from");
            p.addValue("from", f.from());
        }
        if (f.to() != null) {
            where.append(" and t.txn_date <= :to");
            p.addValue("to", f.to());
        }
        Long total = jdbc.queryForObject("select count(*) from sales_txn t " + where, p, Long.class);
        p.addValue("limit", pageSize).addValue("offset", (long) pageNo * pageSize);
        List<TxnRow> rows = jdbc.query(ROW_SELECT + where + " order by t.txn_date desc, t.id desc limit :limit offset :offset", p,
                (rs, i) -> row(rs));
        return new TxnListResponse(rows, pageNo, pageSize, total == null ? 0 : total);
    }

    /** FR-41 banner: is this paper bill number already entered (on a bill that is not reversed)? */
    @Transactional(readOnly = true)
    public PaperBillCheck paperBill(String paperBillNo) {
        String no = paperBillNo.trim();
        return txns.findActivePaperBill(no).map(t -> {
            TxnRow r = row(t.getId());
            return new PaperBillCheck(no, true, r, "Bill " + no + " is already entered – " + r.kindLabel() + ", " + r.customerName()
                    + ", " + BusinessDates.format(r.date()) + ". Do not enter it again.");
        }).orElseGet(() -> new PaperBillCheck(no, false, null, "Bill " + no + " has not been entered yet."));
    }

    /**
     * Enter Bills: the suggested next paper bill number = the highest numeric paper bill number entered + 1 (reversals
     * excluded), or none when no numeric paper bill exists yet. Uses the partial index {@code ix_sales_txn_paper_num}.
     */
    @Transactional(readOnly = true)
    public NextPaperBill nextPaperBill() {
        Long max = jdbc.queryForObject("select max(paper_bill_no::bigint) from sales_txn where paper_bill_no ~ '^[0-9]{1,18}$' "
                + "and kind <> 'REVERSAL'", Map.of(), Long.class);
        return new NextPaperBill(max == null ? null : String.valueOf(max + 1), max == null ? null : String.valueOf(max));
    }

    @Transactional(readOnly = true)
    public TxnRow row(long id) {
        return jdbc.query(ROW_SELECT + "where t.id = :id", new MapSqlParameterSource("id", id), (rs, i) -> row(rs)).stream()
                .findFirst().orElseThrow(() -> new NotFoundException(ENTITY, id));
    }

    @Transactional(readOnly = true)
    public TxnDetail detail(long id) {
        SalesTxn t = txns.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Map<String, Object> c = t.getCustomerId() == null ? Map.of() : jdbc.queryForMap(
                "select code, name, address, phone, pay_type from customer where id = :id", new MapSqlParameterSource("id", t.getCustomerId()));
        Map<String, String> labels = itemLabels();
        List<SaleLine> saleLines = lines.findByTxnIdOrderByLineNo(id).stream()
                .map(l -> l.toSaleLine(labels.getOrDefault(l.getBottleTypeCode() != null ? l.getBottleTypeCode() : l.getProductCode(),
                        l.getBottleTypeCode() != null ? l.getBottleTypeCode() : l.getProductCode())))
                .toList();
        BillLink reversalOf = t.getReversalOfId() == null ? null : link(t.getReversalOfId());
        BillLink reversedBy = t.getReversedById() == null ? null : link(t.getReversedById());
        Map<String, String> names = userNames(t.getCreatedBy(), t.getCreditOverrideBy());
        return new TxnDetail(t.getId(), t.getBillNo(), t.getPaperBillNo(), t.getKind().name(), t.getKind().label(), t.getTxnDate(),
                t.getCustomerId(), (String) c.get("code"), t.getCustomerId() == null ? t.getWalkInName() : (String) c.get("name"),
                (String) c.get("address"), (String) c.get("phone"), (String) c.get("pay_type"), saleLines,
                t.getWaterAmount(), t.getDepositAmount(), t.getProductAmount(), t.getProductCost(), t.getAmount(), t.getPaidOnBill(),
                t.getPayMethod() == null ? null : t.getPayMethod().label(), t.getNote(), t.isPhoneConfirmed(),
                t.getDamageMode() == null ? null : t.getDamageMode().name(), t.getSettleMethod() == null ? null : t.getSettleMethod().name(),
                t.getCreditOverrideBy(), names.get(t.getCreditOverrideBy()), reversalOf, reversedBy, effects(id, labels),
                t.getCreatedBy(), names.get(t.getCreatedBy()), t.getCreatedAt());
    }

    /** "20L" for bottle types and the product name for products, by code. */
    @Transactional(readOnly = true)
    public Map<String, String> itemLabels() {
        Map<String, String> out = new HashMap<>();
        jdbc.query("select code, litres from bottle_type", rs -> {
            out.put(rs.getString("code"), LedgerRules.bottleLabel(rs.getBigDecimal("litres")));
        });
        jdbc.query("select code, name from product", rs -> {
            out.put(rs.getString("code"), rs.getString("name"));
        });
        return out;
    }

    // ------------------------------------------------------------------ helpers

    private static TxnRow row(java.sql.ResultSet rs) throws java.sql.SQLException {
        SalesKind kind = SalesKind.valueOf(rs.getString("kind"));
        long customerId = rs.getLong("customer_id");
        boolean walkIn = rs.wasNull();
        BigDecimal amount = rs.getBigDecimal("amount");
        return new TxnRow(rs.getLong("id"), rs.getString("bill_no"), rs.getString("paper_bill_no"), kind.name(), kind.label(),
                rs.getObject("txn_date", LocalDate.class), walkIn ? null : customerId, rs.getString("code"), rs.getString("party"), walkIn,
                rs.getInt("filled"), rs.getInt("empties"), amount, rs.getBigDecimal("paid_on_bill"),
                rs.getObject("reversed_by_id") != null, rs.getString("reversal_of"));
    }

    private BillLink link(long id) {
        return jdbc.query("select id, bill_no, txn_date, note from sales_txn where id = :id", new MapSqlParameterSource("id", id),
                (rs, i) -> new BillLink(rs.getLong("id"), rs.getString("bill_no"), rs.getObject("txn_date", LocalDate.class),
                        rs.getString("note"))).stream().findFirst().orElse(null);
    }

    private List<EffectView> effects(long txnId, Map<String, String> labels) {
        List<EffectView> out = new ArrayList<>();
        jdbc.query("select target, bottle_type_code, product_code, field, delta from ledger_effect "
                        + "where source_type = 'SALES_TXN' and source_id = :id order by id",
                new MapSqlParameterSource("id", txnId), rs -> {
                    String target = rs.getString("target");
                    String field = rs.getString("field");
                    String item = "PRODUCT".equals(target) ? rs.getString("product_code") : rs.getString("bottle_type_code");
                    String place = switch (target) {
                        case "STOCK" -> Bucket.valueOf(field).label();
                        case "CUSTOMER_BOTTLE" -> CustomerField.valueOf(field).label();
                        default -> "Stock";
                    };
                    out.add(new EffectView(labels.getOrDefault(item, item), place, rs.getInt("delta")));
                });
        return out;
    }

    private Map<String, String> userNames(String... usernames) {
        Map<String, String> out = new HashMap<>();
        List<String> wanted = new ArrayList<>();
        for (String u : usernames) {
            if (u != null) {
                wanted.add(u);
            }
        }
        if (!wanted.isEmpty()) {
            jdbc.query("select username, full_name from app_user where username in (:u)", new MapSqlParameterSource("u", wanted),
                    rs -> {
                        out.put(rs.getString("username"), rs.getString("full_name"));
                    });
        }
        return out;
    }
}
