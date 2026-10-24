package lk.thuhina.water.inventory.model;

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
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Damaged bottles (FR-29). Company damage, and customer damage (responsibility CUSTOMER, linked to its bill, M06).
 * The stock change itself is a ledger posting with source DAMAGE.
 */
@Entity
@Table(name = "damage_record")
@EntityListeners(AuditingEntityListener.class)
public class DamageRecord {

    public enum Responsibility { COMPANY, CUSTOMER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "damage_no", nullable = false, unique = true, length = 20)
    private String damageNo;

    @Column(name = "damage_date", nullable = false)
    private LocalDate damageDate;

    @Column(name = "bottle_type_code", nullable = false, length = 10)
    private String bottleTypeCode;

    @Column(nullable = false)
    private int qty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Responsibility responsibility;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DamageLocation location;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(length = 300)
    private String note;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false)
    private boolean reversed;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected DamageRecord() {
    }

    public static DamageRecord company(String damageNo, LocalDate date, String bottleTypeCode, int qty, DamageLocation location,
                                       String reason, String note) {
        DamageRecord d = new DamageRecord();
        d.damageNo = damageNo;
        d.damageDate = date;
        d.bottleTypeCode = bottleTypeCode;
        d.qty = qty;
        d.responsibility = Responsibility.COMPANY;
        d.location = location;
        d.reason = reason;
        d.note = note;
        return d;
    }

    /**
     * Bottles rejected by the filling factory (FR-24, FR-29): company damage "At factory", recorded by the factory return
     * {@code returnId} (source RETURN). The return's own posting writes them off.
     */
    public static DamageRecord factoryReject(String damageNo, LocalDate date, String bottleTypeCode, int qty, String reason,
                                             String note, long returnId) {
        DamageRecord d = company(damageNo, date, bottleTypeCode, qty, DamageLocation.AT_FACTORY, reason, note);
        d.sourceType = "RETURN";
        d.sourceId = returnId;
        return d;
    }

    /** A customer-damaged bottle (BR-06), recorded "At customer" by the bill {@code billId} (source SALES_TXN). */
    public static DamageRecord customer(String damageNo, LocalDate date, String bottleTypeCode, int qty, String reason, String note,
                                        long customerId, long billId) {
        DamageRecord d = new DamageRecord();
        d.damageNo = damageNo;
        d.damageDate = date;
        d.bottleTypeCode = bottleTypeCode;
        d.qty = qty;
        d.responsibility = Responsibility.CUSTOMER;
        d.location = DamageLocation.AT_CUSTOMER;
        d.reason = reason;
        d.note = note;
        d.customerId = customerId;
        d.sourceType = "SALES_TXN";
        d.sourceId = billId;
        return d;
    }

    /** The bill was reversed (BR-14). */
    public void markReversed() {
        reversed = true;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public Long getId() {
        return id;
    }

    public String getDamageNo() {
        return damageNo;
    }

    public LocalDate getDamageDate() {
        return damageDate;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public int getQty() {
        return qty;
    }

    public Responsibility getResponsibility() {
        return responsibility;
    }

    public DamageLocation getLocation() {
        return location;
    }

    public String getReason() {
        return reason;
    }

    public String getNote() {
        return note;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public boolean isReversed() {
        return reversed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
