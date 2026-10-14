package lk.thuhina.water.quotations.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Status;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Via;

/**
 * A price quotation to an existing customer or a new organisation (FR-64, FR-65). The "To" details are a copy taken when saved
 * (for a customer: name, address and area, phone). Only a draft can change; the steps are recorded in {@code cq_history}.
 */
@Entity
@Table(name = "customer_quotation")
public class CustomerQuotation extends BaseEntity {

    @Column(name = "cq_no", nullable = false, updatable = false, length = 20)
    private String cqNo;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "org_name", nullable = false, length = 150)
    private String orgName;

    @Column(length = 100)
    private String contact;

    @Column(length = 100)
    private String designation;

    @Column(length = 250)
    private String address;

    @Column(name = "area_id")
    private Long areaId;

    @Column(length = 20)
    private String phone;

    @Column(length = 100)
    private String email;

    @Column(name = "customer_type_id", nullable = false)
    private long customerTypeId;

    @Column(name = "quote_date", nullable = false)
    private LocalDate quoteDate;

    @Column(name = "valid_until", nullable = false)
    private LocalDate validUntil;

    @Column(length = 200)
    private String subject;

    @Column(name = "delivery_text", length = 200)
    private String deliveryText;

    @Column(name = "terms_days", nullable = false)
    private int termsDays;

    @Column(columnDefinition = "text")
    private String conditions;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "sent_via", length = 10)
    private Via sentVia;

    @Column(name = "sent_on")
    private LocalDate sentOn;

    @Column(name = "accepted_on")
    private LocalDate acceptedOn;

    @Column(name = "reject_reason", length = 250)
    private String rejectReason;

    @Column(name = "prices_applied_on")
    private LocalDate pricesAppliedOn;

    @Column(name = "copy_of_id", updatable = false)
    private Long copyOfId;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<CqLine> lines = new ArrayList<>();

    protected CustomerQuotation() {
    }

    public CustomerQuotation(String cqNo, Long copyOfId) {
        this.cqNo = cqNo;
        this.copyOfId = copyOfId;
    }

    /** The "To" details and the quotation terms (draft only – checked by the service). */
    public record Header(Long customerId, String orgName, String contact, String designation, String address, Long areaId, String phone,
                         String email, long customerTypeId, LocalDate quoteDate, LocalDate validUntil, String subject, String deliveryText,
                         int termsDays, String conditions) {
    }

    public void set(Header h) {
        this.customerId = h.customerId();
        this.orgName = h.orgName();
        this.contact = h.contact();
        this.designation = h.designation();
        this.address = h.address();
        this.areaId = h.areaId();
        this.phone = h.phone();
        this.email = h.email();
        this.customerTypeId = h.customerTypeId();
        this.quoteDate = h.quoteDate();
        this.validUntil = h.validUntil();
        this.subject = h.subject();
        this.deliveryText = h.deliveryText();
        this.termsDays = h.termsDays();
        this.conditions = h.conditions();
    }

    /** Replaces every line (a draft is saved as a whole). */
    public void replaceLines(List<CqLine> newLines) {
        lines.clear();
        lines.addAll(newLines);
    }

    public void markSent(Via via, LocalDate on) {
        this.status = Status.SENT;
        this.sentVia = via;
        this.sentOn = on;
    }

    public void accept(LocalDate on) {
        this.status = Status.ACCEPTED;
        this.acceptedOn = on;
    }

    public void reject(String reason) {
        this.status = Status.REJECTED;
        this.rejectReason = reason;
    }

    public void linkCustomer(long id) {
        this.customerId = id;
    }

    public void pricesApplied(LocalDate from) {
        this.pricesAppliedOn = from;
    }

    public String getCqNo() {
        return cqNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getOrgName() {
        return orgName;
    }

    public String getSubject() {
        return subject;
    }

    public long getCustomerTypeId() {
        return customerTypeId;
    }

    public LocalDate getQuoteDate() {
        return quoteDate;
    }

    public Status getStatus() {
        return status;
    }

    public LocalDate getPricesAppliedOn() {
        return pricesAppliedOn;
    }

    public List<CqLine> getLines() {
        return lines;
    }
}
