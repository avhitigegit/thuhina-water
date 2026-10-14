package lk.thuhina.water.billing.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.billing.dto.BillingDtos.GenerateResult;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceMonthRow;
import lk.thuhina.water.billing.dto.BillingDtos.InvoiceRow;
import lk.thuhina.water.billing.dto.BillingDtos.SkippedCustomer;
import lk.thuhina.water.billing.model.MonthlyInvoice;
import lk.thuhina.water.billing.repository.MonthlyInvoiceRepository;
import lk.thuhina.water.billing.rules.InvoiceRules;
import lk.thuhina.water.billing.rules.InvoiceRules.InvoiceTotals;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Monthly invoices (FR-49, design 5.6): one per Monthly bill customer and month, listing the month's bills (opening balances
 * excluded); opening = balance before the month, this month = Σ (amount − paid), total due = the month's closing balance;
 * invoice date today, due = date + the customer's terms; number {@code INV-YYMM-0001} by the invoice month. Customers are
 * skipped when not on Monthly bill, already invoiced, or with no deliveries and nothing due.
 */
@Service
public class InvoiceService {

    private final MonthlyInvoiceRepository invoices;
    private final BillingQueryService query;
    private final NumberingService numbering;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public InvoiceService(MonthlyInvoiceRepository invoices, BillingQueryService query, NumberingService numbering, AuditService audit,
                          ApplicationEventPublisher events, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.invoices = invoices;
        this.query = query;
        this.numbering = numbering;
        this.audit = audit;
        this.events = events;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public GenerateResult generate(String period) {
        LocalDate today = BusinessDates.today(clock);
        String p = period == null ? null : period.trim();
        String problem = InvoiceRules.periodProblem(p, today);
        if (problem != null) {
            throw new ValidationException("period", problem);
        }
        // One run at a time, so two users pressing Generate never both invoice a customer.
        jdbc.query("select pg_advisory_xact_lock(hashtext('monthly-invoice-run'))", rs -> {
        });
        YearMonth month = YearMonth.parse(p);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        Map<Long, Object[]> customers = new HashMap<>();
        jdbc.query("select id, pay_type, terms_days from customer where pay_type = 'MONTHLY_BILL' "
                        + "or id in (select customer_id from monthly_invoice where period = :p)", new MapSqlParameterSource("p", p),
                rs -> {
                    customers.put(rs.getLong("id"), new Object[] {PayType.valueOf(rs.getString("pay_type")), rs.getInt("terms_days")});
                });

        List<InvoiceRow> created = new ArrayList<>();
        List<SkippedCustomer> skipped = new ArrayList<>();
        for (InvoiceMonthRow r : query.monthRows(p, null)) {
            Object[] c = customers.get(r.customerId());
            String reason = InvoiceRules.skipReason((PayType) c[0], r.invoice() != null, r.deliveries(), r.totalDue());
            if (reason != null) {
                skipped.add(new SkippedCustomer(r.customerId(), r.code(), r.name(), reason));
                continue;
            }
            InvoiceTotals totals = InvoiceTotals.of(r.opening(), r.charges(), r.paidOnBills(), r.payments());
            MonthlyInvoice inv = invoices.saveAndFlush(new MonthlyInvoice(numbering.next(SequenceNames.MONTHLY_INVOICE, from), r.customerId(),
                    p, from, to, today, InvoiceRules.dueDate(today, (int) c[1]), totals));
            jdbc.update("insert into invoice_txn (invoice_id, txn_id) select :inv, id from sales_txn where customer_id = :c "
                    + "and txn_date between :from and :to and kind <> 'OPENING_BALANCE'", new MapSqlParameterSource("inv", inv.getId())
                    .addValue("c", r.customerId()).addValue("from", from).addValue("to", to));
            audit.log(AuditAction.CREATE, "Invoice", inv.getInvoiceNo(), r.name() + " (" + r.code() + ") – " + InvoiceRules.periodLabel(p)
                    + ": " + Money.format(totals.amount()) + " (total due " + Money.format(totals.totalDue()) + ")");
            events.publishEvent(new InvoiceGenerated(inv.getId(), r.customerId(), p));
            created.add(query.invoiceRow(inv.getId()));
        }
        return new GenerateResult(p, InvoiceRules.periodLabel(p), created, skipped);
    }
}
