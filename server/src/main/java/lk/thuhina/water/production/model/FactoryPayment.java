package lk.thuhina.water.production.model;

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

/** Money paid to a filling factory for the filling cost of one month ({@code period} = YYYY-MM) (FR-26). Insert-only. */
@Entity
@Immutable
@Table(name = "factory_payment")
@EntityListeners(AuditingEntityListener.class)
public class FactoryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fp_no", nullable = false, unique = true, length = 20)
    private String fpNo;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @Column(nullable = false, length = 7)
    private String period;

    @Column(name = "pay_date", nullable = false)
    private LocalDate payDate;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayMethod method;

    @Column(length = 100)
    private String reference;

    @Column(length = 200)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected FactoryPayment() {
    }

    public FactoryPayment(String fpNo, long factoryId, String period, LocalDate payDate, BigDecimal amount, PayMethod method,
                          String reference, String note) {
        this.fpNo = fpNo;
        this.factoryId = factoryId;
        this.period = period;
        this.payDate = payDate;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getFpNo() {
        return fpNo;
    }
}
