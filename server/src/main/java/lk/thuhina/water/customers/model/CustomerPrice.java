package lk.thuhina.water.customers.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/**
 * An agreed water price for one customer and bottle type (FR-05, BR-08) from a date, optionally until a date. It wins
 * over the standard price for that customer. Rows are never deleted: a newer agreement closes this one the day before
 * it starts, or cancels it when it had not started yet.
 */
@Entity
@Table(name = "customer_price")
public class CustomerPrice extends BaseEntity {

    /** As of a day: in force, scheduled (starts later), ended, replaced (cut short by a newer one), cancelled. */
    public enum State { IN_FORCE, SCHEDULED, ENDED, REPLACED, CANCELLED }

    @Column(name = "cp_no", nullable = false, unique = true, length = 10)
    private String cpNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "bottle_type_code", nullable = false, length = 10)
    private String bottleTypeCode;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "standard_at_set", precision = 12, scale = 2)
    private BigDecimal standardAtSet;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(nullable = false, length = 250)
    private String reason;

    @Column(name = "customer_quotation_id")
    private Long customerQuotationId;

    @Column(nullable = false)
    private boolean cancelled;

    @Column(name = "cancel_reason", length = 250)
    private String cancelReason;

    protected CustomerPrice() {
    }

    public CustomerPrice(String cpNo, Long customerId, String bottleTypeCode, BigDecimal price, BigDecimal standardAtSet,
                         LocalDate validFrom, LocalDate validUntil, String reason, Long customerQuotationId) {
        this.cpNo = cpNo;
        this.customerId = customerId;
        this.bottleTypeCode = bottleTypeCode;
        this.price = price;
        this.standardAtSet = standardAtSet;
        this.validFrom = validFrom;
        this.validUntil = validUntil;
        this.reason = reason;
        this.customerQuotationId = customerQuotationId;
    }

    /** True when this row applies on {@code date}. */
    public boolean covers(LocalDate date) {
        return !cancelled && !validFrom.isAfter(date) && (validUntil == null || !validUntil.isBefore(date));
    }

    public void closeOn(LocalDate lastDay) {
        this.validUntil = lastDay;
    }

    public void cancel(String why) {
        this.cancelled = true;
        this.cancelReason = why;
    }

    public String getCpNo() {
        return cpNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getStandardAtSet() {
        return standardAtSet;
    }

    public LocalDate getValidFrom() {
        return validFrom;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public String getReason() {
        return reason;
    }

    public Long getCustomerQuotationId() {
        return customerQuotationId;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
