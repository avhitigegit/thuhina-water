package lk.thuhina.water.expenses.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;
import lk.thuhina.water.sales.model.PayMethod;

/**
 * A running expense (FR-62, BR-19): paid on {@code paidDate}, counted for {@code forMonth} (first day of the month).
 * Any field can be changed; deleting keeps the row with the reason (soft delete) so the audit trail stays complete.
 */
@Entity
@Table(name = "expense")
public class Expense extends BaseEntity {

    @Column(name = "expense_no", nullable = false, updatable = false, length = 20)
    private String expenseNo;

    @Column(name = "paid_date", nullable = false)
    private LocalDate paidDate;

    @Column(name = "for_month", nullable = false)
    private LocalDate forMonth;

    @Column(name = "expense_type_id", nullable = false)
    private long typeId;

    @Column(length = 200)
    private String description;

    @Column(name = "paid_to", length = 100)
    private String paidTo;

    @Column(name = "bill_no", length = 50)
    private String billNo;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayMethod method;

    @Column(length = 100)
    private String reference;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "delete_reason", length = 200)
    private String deleteReason;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 50)
    private String deletedBy;

    protected Expense() {
    }

    public Expense(String expenseNo) {
        this.expenseNo = expenseNo;
    }

    /** All the fields the add / edit pop-up sets. */
    public record Fields(LocalDate paidDate, LocalDate forMonth, long typeId, String description, String paidTo, String billNo,
                         BigDecimal amount, PayMethod method, String reference) {
    }

    public void set(Fields f) {
        this.paidDate = f.paidDate();
        this.forMonth = f.forMonth();
        this.typeId = f.typeId();
        this.description = f.description();
        this.paidTo = f.paidTo();
        this.billNo = f.billNo();
        this.amount = f.amount();
        this.method = f.method();
        this.reference = f.reference();
    }

    public Fields fields() {
        return new Fields(paidDate, forMonth, typeId, description, paidTo, billNo, amount, method, reference);
    }

    public void delete(String reason, Instant at, String by) {
        this.deleted = true;
        this.deleteReason = reason;
        this.deletedAt = at;
        this.deletedBy = by;
    }

    public String getExpenseNo() {
        return expenseNo;
    }

    public LocalDate getPaidDate() {
        return paidDate;
    }

    public LocalDate getForMonth() {
        return forMonth;
    }

    public long getTypeId() {
        return typeId;
    }

    public String getDescription() {
        return description;
    }

    public String getPaidTo() {
        return paidTo;
    }

    public String getBillNo() {
        return billNo;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PayMethod getMethod() {
        return method;
    }

    public String getReference() {
        return reference;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public String getDeleteReason() {
        return deleteReason;
    }
}
