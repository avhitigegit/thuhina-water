package lk.thuhina.water.billing.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.thuhina.water.sales.model.PayMethod;
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Money received from a customer (FR-50) with its receipt number ({@code RC-00001}). {@code balanceBefore} is the account
 * balance just before it, so the receipt can always show the balance before and after. Insert-only.
 */
@Entity
@Immutable
@Table(name = "customer_payment")
@EntityListeners(AuditingEntityListener.class)
public class CustomerPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receipt_no", nullable = false, unique = true, length = 20)
    private String receiptNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayMethod method;

    @Column(length = 100)
    private String reference;

    @Column(length = 100)
    private String bank;

    @Column(name = "cheque_date")
    private LocalDate chequeDate;

    @Column(name = "invoice_id")
    private Long invoiceId;

    @Column(length = 200)
    private String note;

    @Column(name = "balance_before", nullable = false)
    private BigDecimal balanceBefore;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected CustomerPayment() {
    }

    public CustomerPayment(String receiptNo, long customerId, LocalDate payDate, BigDecimal amount, PayMethod method, String reference,
                           String bank, LocalDate chequeDate, Long invoiceId, String note, BigDecimal balanceBefore) {
        this.receiptNo = receiptNo;
        this.customerId = customerId;
        this.payDate = payDate;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.bank = bank;
        this.chequeDate = chequeDate;
        this.invoiceId = invoiceId;
        this.note = note;
        this.balanceBefore = balanceBefore;
    }

    public Long getId() {
        return id;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
