package lk.thuhina.water.billing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.thuhina.water.billing.rules.InvoiceRules.InvoiceTotals;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A Monthly bill customer's invoice for one month (FR-49, {@code INV-YYMM-0001}): the totals are kept as they were on the
 * invoice date; the bills it lists are in {@code invoice_txn}. One per customer and month. Insert-only.
 */
@Entity
@Immutable
@Table(name = "monthly_invoice")
@EntityListeners(AuditingEntityListener.class)
public class MonthlyInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no", nullable = false, unique = true, length = 20)
    private String invoiceNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "opening_balance", nullable = false)
    private BigDecimal openingBalance;

    @Column(nullable = false)
    private BigDecimal charges;

    @Column(name = "paid_on_bills", nullable = false)
    private BigDecimal paidOnBills;

    @Column(nullable = false)
    private BigDecimal payments;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(name = "total_due", nullable = false)
    private BigDecimal totalDue;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected MonthlyInvoice() {
    }

    public MonthlyInvoice(String invoiceNo, long customerId, String period, LocalDate periodFrom, LocalDate periodTo, LocalDate invoiceDate,
                          LocalDate dueDate, InvoiceTotals t) {
        this.invoiceNo = invoiceNo;
        this.customerId = customerId;
        this.period = period;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.openingBalance = t.opening();
        this.charges = t.charges();
        this.paidOnBills = t.paidOnBills();
        this.payments = t.payments();
        this.amount = t.amount();
        this.totalDue = t.totalDue();
    }

    public Long getId() {
        return id;
    }

    public String getInvoiceNo() {
        return invoiceNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getTotalDue() {
        return totalDue;
    }
}
