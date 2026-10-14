package lk.thuhina.water.production.model;

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
import org.hibernate.annotations.Immutable;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * The empties of one bottle type in a dispatch (FR-23). {@code chargePerBottle} is the factory's charge on the dispatch
 * date, copied here so a later charge change does not change the cost of this batch (BR-11). Returns are separate rows.
 */
@Entity
@Immutable
@Table(name = "factory_batch")
@EntityListeners(AuditingEntityListener.class)
public class FactoryBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 20)
    private String batchNo;

    @Column(name = "dispatch_id", nullable = false)
    private Long dispatchId;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

    @Column(name = "bottle_type_code", nullable = false, length = 10)
    private String bottleTypeCode;

    @Column(name = "qty_sent", nullable = false)
    private int qtySent;

    @Column(name = "charge_per_bottle", nullable = false)
    private BigDecimal chargePerBottle;

    @Column(name = "vehicle_no", length = 20)
    private String vehicleNo;

    @Column(length = 200)
    private String note;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @CreatedBy
    @Column(name = "created_by", nullable = false, updatable = false, length = 50)
    private String createdBy;

    protected FactoryBatch() {
    }

    public FactoryBatch(String batchNo, FactoryDispatch dispatch, String bottleTypeCode, int qtySent, BigDecimal chargePerBottle) {
        this.batchNo = batchNo;
        this.dispatchId = dispatch.getId();
        this.factoryId = dispatch.getFactoryId();
        this.dispatchDate = dispatch.getDispatchDate();
        this.bottleTypeCode = bottleTypeCode;
        this.qtySent = qtySent;
        this.chargePerBottle = chargePerBottle;
        this.vehicleNo = dispatch.getVehicleNo();
        this.note = dispatch.getNote();
    }

    public Long getId() {
        return id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public Long getDispatchId() {
        return dispatchId;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public LocalDate getDispatchDate() {
        return dispatchDate;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public int getQtySent() {
        return qtySent;
    }

    public BigDecimal getChargePerBottle() {
        return chargePerBottle;
    }
}
