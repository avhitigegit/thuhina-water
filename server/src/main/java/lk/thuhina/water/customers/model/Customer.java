package lk.thuhina.water.customers.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;

/**
 * A customer (FR-08 – FR-13): contact details, area and type, delivery day and cycle with the next delivery date,
 * usual quantity per bottle type, payment type with credit limit and terms, status. Bottle balances and the account
 * balance are kept in their own tables (changed by the Ledger Engine and by sales / payments).
 */
@Entity
@Table(name = "customer")
public class Customer extends BaseEntity {

    public enum Status { ACTIVE, INACTIVE }

    @Column(nullable = false, unique = true, length = 10)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 250)
    private String address;

    @Column(name = "area_id", nullable = false)
    private Long areaId;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "customer_type_id", nullable = false)
    private Long customerTypeId;

    /** 1 = Monday … 7 = Sunday (ISO). */
    @Column(name = "delivery_day", nullable = false)
    private short deliveryDay;

    @Column(name = "cycle_weeks", nullable = false)
    private short cycleWeeks;

    @Column(name = "next_delivery_date", nullable = false)
    private LocalDate nextDeliveryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_type", nullable = false, length = 15)
    private PayType payType;

    @Column(name = "credit_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "terms_days", nullable = false)
    private int termsDays;

    @Column(name = "registered_on", nullable = false)
    private LocalDate registeredOn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.ACTIVE;

    @Column(name = "status_date")
    private LocalDate statusDate;

    @Column(name = "status_reason", length = 250)
    private String statusReason;

    @Column(length = 500)
    private String notes;

    @Column(name = "whatsapp_opt_in", nullable = false)
    private boolean whatsappOptIn;

    @Column(name = "whatsapp_number", length = 20)
    private String whatsappNumber;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "customer_usual_qty", joinColumns = @JoinColumn(name = "customer_id"))
    @MapKeyColumn(name = "bottle_type_code")
    @Column(name = "qty")
    private Map<String, Integer> usualQty = new HashMap<>();

    protected Customer() {
    }

    public Customer(String code, LocalDate registeredOn) {
        this.code = code;
        this.registeredOn = registeredOn;
    }

    /** Details, delivery and payment (create and edit). */
    public void update(String name, String address, Long areaId, String phone, Long customerTypeId, short deliveryDay,
                       short cycleWeeks, LocalDate nextDeliveryDate, PayType payType, BigDecimal creditLimit, int termsDays,
                       String notes, boolean whatsappOptIn, String whatsappNumber, Map<String, Integer> usualQty) {
        this.name = name;
        this.address = address;
        this.areaId = areaId;
        this.phone = phone;
        this.customerTypeId = customerTypeId;
        this.deliveryDay = deliveryDay;
        this.cycleWeeks = cycleWeeks;
        this.nextDeliveryDate = nextDeliveryDate;
        this.payType = payType;
        this.creditLimit = creditLimit;
        this.termsDays = termsDays;
        this.notes = notes;
        this.whatsappOptIn = whatsappOptIn;
        this.whatsappNumber = whatsappNumber;
        this.usualQty.clear();
        this.usualQty.putAll(usualQty);
    }

    public void deactivate(LocalDate from, String reason) {
        this.status = Status.INACTIVE;
        this.statusDate = from;
        this.statusReason = reason;
    }

    /** Reactivate; a past next delivery date is moved to {@code next} (the next delivery day from today). */
    public void reactivate(LocalDate on, LocalDate next) {
        this.status = Status.ACTIVE;
        this.statusDate = on;
        this.statusReason = null;
        this.nextDeliveryDate = next;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

    public Long getAreaId() {
        return areaId;
    }

    public String getPhone() {
        return phone;
    }

    public Long getCustomerTypeId() {
        return customerTypeId;
    }

    public short getDeliveryDay() {
        return deliveryDay;
    }

    public short getCycleWeeks() {
        return cycleWeeks;
    }

    public LocalDate getNextDeliveryDate() {
        return nextDeliveryDate;
    }

    public PayType getPayType() {
        return payType;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public int getTermsDays() {
        return termsDays;
    }

    public LocalDate getRegisteredOn() {
        return registeredOn;
    }

    public Status getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public LocalDate getStatusDate() {
        return statusDate;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public String getNotes() {
        return notes;
    }

    public boolean isWhatsappOptIn() {
        return whatsappOptIn;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public Map<String, Integer> getUsualQty() {
        return usualQty;
    }
}
