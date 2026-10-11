package lk.thuhina.water.printing;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.sales.dto.SalesDtos.TxnDetail;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.sales.rules.SaleLine;
import lk.thuhina.water.sales.service.SalesQueryService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bill print (FR-59, design 10 "Bill"): company header, BILL with the bill number, date and paper bill number, the
 * customer, kind and note, the lines (water, deposits "not refundable", new bottle for damage, old bottles "no
 * deposit", empties returned, products), total, paid, account balance now, bottles held, next delivery, signatures.
 */
@Service
public class BillPrintService {

    private static final boolean[] NUMERIC = {false, true, true, true};

    private final SalesQueryService sales;
    private final CompanyHeaderService company;
    private final PdfRenderer renderer;
    private final NamedParameterJdbcTemplate jdbc;

    public BillPrintService(SalesQueryService sales, CompanyHeaderService company, PdfRenderer renderer, NamedParameterJdbcTemplate jdbc) {
        this.sales = sales;
        this.company = company;
        this.renderer = renderer;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public byte[] bill(long txnId) {
        return renderer.render(company.current(), document(sales.detail(txnId)));
    }

    @Transactional(readOnly = true)
    public String billNo(long txnId) {
        return sales.row(txnId).billNo();
    }

    /** The bill as plain parts (also used by tests to check the wording). */
    @Transactional(readOnly = true)
    public PrintDocument document(TxnDetail t) {
        List<String> meta = new ArrayList<>(List.of(t.billNo(), BusinessDates.format(t.date())));
        if (t.paperBillNo() != null) {
            meta.add("Paper bill " + t.paperBillNo());
        }
        List<String> party = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        List<PrintDocument.SummaryRow> summary = new ArrayList<>(List.of(
                new PrintDocument.SummaryRow("Total", Money.format(t.amount()), true),
                new PrintDocument.SummaryRow("Paid", Money.format(t.paid()), false)));
        if (t.customerId() == null) {
            party.add(t.customerName());
        } else {
            Map<String, Object> c = jdbc.queryForMap("select c.next_delivery_date, a.name as area, coalesce(acc.balance, 0) as balance "
                            + "from customer c join area a on a.id = c.area_id left join customer_account acc on acc.customer_id = c.id "
                            + "where c.id = :id", new MapSqlParameterSource("id", t.customerId()));
            party.add(t.customerName() + " (" + t.customerCode() + ")");
            party.add(t.customerAddress() + ", " + c.get("area"));
            party.add(t.customerPhone() + " · " + PayType.valueOf(t.payType()).label());
            summary.add(new PrintDocument.SummaryRow("Account balance now", Money.format((BigDecimal) c.get("balance")), false));
            LocalDate next = ((java.sql.Date) c.get("next_delivery_date")).toLocalDate();
            footer.add("Bottles held: " + held(t.customerId()) + " · next delivery "
                    + next.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + BusinessDates.format(next));
        }
        String subtitle = t.kindLabel() + (t.note() == null ? "" : " – " + t.note());
        if (t.creditOverrideByName() != null) {
            subtitle += " · Over the credit limit – approved by " + t.creditOverrideByName();
        }
        PrintDocument.Table table = new PrintDocument.Table(List.of("Description", "Qty", "Unit price", "Amount (Rs.)"), NUMERIC,
                rows(t));
        String issuedBy = "Issued by\n" + (t.createdByName() == null ? t.createdBy() : t.createdByName());
        return new PrintDocument("BILL", meta, party, subtitle, table, summary, footer, List.of(issuedBy, "Customer signature"));
    }

    private List<List<String>> rows(TxnDetail t) {
        Map<String, String> bottleNames = new HashMap<>();
        jdbc.query("select code, name from bottle_type", rs -> {
            bottleNames.put(rs.getString("code"), rs.getString("name"));
        });
        Map<Long, String> brands = new HashMap<>();
        jdbc.query("select id, name from old_bottle_brand", rs -> {
            brands.put(rs.getLong("id"), rs.getString("name"));
        });
        int sign = t.amount().signum() < 0 ? -1 : 1;
        boolean damage = t.kind().equals(SalesKind.CUSTOMER_DAMAGE.name())
                || (t.reversalOf() != null && t.lines().stream().anyMatch(l -> l.getDamaged() > 0));
        List<List<String>> rows = new ArrayList<>();
        for (SaleLine l : t.lines()) {
            if (l.getProductCode() != null) {
                rows.add(row(l.getLabel(), l.getQty(), l.getUnitPrice(), l.getLineAmount()));
                continue;
            }
            String label = l.getLabel();
            if (l.getFilled() > 0 && l.getUnitPrice() != null) {
                rows.add(row("Water – " + bottleNames.getOrDefault(l.getBottleTypeCode(), label)
                        + (l.isAgreedPrice() ? " (agreed price)" : ""), l.getFilled(), l.getUnitPrice(), signed(l.waterAmount(l.getFilled()), sign)));
            }
            if (l.getDepositCount() > 0 && l.getDepositUnit() != null) {
                String what = damage ? "New bottle – damaged bottle replaced" : "Bottle deposit – " + label + " (not refundable)";
                rows.add(row(what, l.getDepositCount(), l.getDepositUnit(), signed(l.depositAmount(), sign)));
            }
            if (damage && l.getDepositCount() == 0 && l.getDamaged() > 0) {
                rows.add(row("Damaged " + label + " bottles – holding reduced", l.getDamaged(), null, null));
            }
            if (l.getOldBottles() > 0) {
                rows.add(row("Old " + brands.getOrDefault(l.getOldBrandId(), "") + " bottles handed in – no deposit", l.getOldBottles(), null, null));
            }
            if (l.getEmpties() > 0) {
                rows.add(row("Empty " + label + " bottles returned", l.getEmpties(), null, null));
            }
        }
        return rows;
    }

    private String held(long customerId) {
        List<String> parts = jdbc.query("select b.held, t.litres from customer_bottle_balance b join bottle_type t on t.code = b.bottle_type_code "
                        + "where b.customer_id = :c and (t.active or b.held > 0) order by t.litres desc",
                new MapSqlParameterSource("c", customerId),
                (rs, i) -> rs.getInt("held") + " × " + LedgerRules.bottleLabel(rs.getBigDecimal("litres")));
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private static BigDecimal signed(BigDecimal v, int sign) {
        return sign < 0 ? v.negate() : v;
    }

    private static List<String> row(String what, int qty, BigDecimal unit, BigDecimal amount) {
        return List.of(what, String.valueOf(qty), unit == null ? "" : number(unit), amount == null ? "" : number(amount));
    }

    /** 1,350.00 */
    static String number(BigDecimal v) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ENGLISH));
        return f.format(Money.of(v));
    }
}
