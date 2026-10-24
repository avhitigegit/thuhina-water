package lk.thuhina.water.production.model;

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

/** One load of empties to a filling factory (FR-23): every bottle type sent goes in this one entry, as one batch each. */
@Entity
@Immutable
@Table(name = "factory_dispatch")
@EntityListeners(AuditingEntityListener.class)
public class FactoryDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dispatch_no", nullable = false, unique = true, length = 20)
    private String dispatchNo;

    @Column(name = "factory_id", nullable = false)
    private Long factoryId;

    @Column(name = "dispatch_date", nullable = false)
    private LocalDate dispatchDate;

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

    protected FactoryDispatch() {
    }

    public FactoryDispatch(String dispatchNo, long factoryId, LocalDate dispatchDate, String vehicleNo, String note) {
        this.dispatchNo = dispatchNo;
        this.factoryId = factoryId;
        this.dispatchDate = dispatchDate;
        this.vehicleNo = vehicleNo;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public String getDispatchNo() {
        return dispatchNo;
    }

    public Long getFactoryId() {
        return factoryId;
    }

    public LocalDate getDispatchDate() {
        return dispatchDate;
    }

    public String getVehicleNo() {
        return vehicleNo;
    }

    public String getNote() {
        return note;
    }

    public String getCreatedBy() {
        return createdBy;
    }
}
