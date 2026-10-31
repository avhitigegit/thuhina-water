package lk.thuhina.water.sales.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;
import lk.thuhina.water.common.Money;

/**
 * A sales transaction = one bill (design 4.3 {@code sales_txn}): kind, date, customer or walk-in, amount (split into
 * water, deposits and products) and the money paid on the bill. Never changed after saving – a mistake is corrected by
 * a REVERSAL bill (BR-14); only the link to that reversal is set later.
 */
@Entity
@Table(name = "sales_txn")
public class SalesTxn extends BaseEntity {

    @Column(name = "bill_no", nullable = false, unique = true, length = 20, updatable = false)
    private String billNo;

    @Column(name = "paper_bill_no", length = 30, updatable = false)
    private String paperBillNo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private SalesKind kind;

    @Column(name = "txn_date", nullable = false, updatable = false)
    private LocalDate txnDate;

    @Column(name = "customer_id", updatable = false)
    private Long customerId;

    @Column(name = "walk_in_name", length = 120, updatable = false)
    private String walkInName;

    @Column(nullable = false, updatable = false)
    private BigDecimal amount;

    @Column(name = "paid_on_bill", nullable = false, updatable = false)
    private BigDecimal paidOnBill;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_method", length = 15, updatable = false)
    private PayMethod payMethod;

    @Column(name = "pay_reference", length = 100, updatable = false)
    private String payReference;

    @Column(name = "water_amount", nullable = false, updatable = false)
    private BigDecimal waterAmount = Money.ZERO;

    @Column(name = "deposit_amount", nullable = false, updatable = false)
    private BigDecimal depositAmount = Money.ZERO;

    @Column(name = "product_amount", nullable = false, updatable = false)
    private BigDecimal productAmount = Money.ZERO;

    @Column(name = "product_cost", nullable = false, updatable = false)
    private BigDecimal productCost = Money.ZERO;

    @Column(length = 500, updatable = false)
    private String note;

    @Column(name = "phone_confirmed", nullable = false, updatable = false)
    private boolean phoneConfirmed;

    @Enumerated(EnumType.STRING)
    @Column(name = "damage_mode", length = 10, updatable = false)
    private DamageMode damageMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "settle_method", length = 10, updatable = false)
    private SettleMethod settleMethod;

    @Column(name = "credit_override_by", length = 50, updatable = false)
    private String creditOverrideBy;

    @Column(name = "reversal_of_id", updatable = false)
    private Long reversalOfId;

    @Column(name = "reversed_by_id")
    private Long reversedById;

    protected SalesTxn() {
    }

    public SalesTxn(String billNo, SalesKind kind, LocalDate txnDate, Long customerId, String walkInName) {
        this.billNo = billNo;
        this.kind = kind;
        this.txnDate = txnDate;
        this.customerId = customerId;
        this.walkInName = walkInName;
    }

    public SalesTxn amounts(BigDecimal water, BigDecimal deposit, BigDecimal product, BigDecimal productCost, BigDecimal paid,
                            PayMethod payMethod) {
        this.waterAmount = Money.of(water);
        this.depositAmount = Money.of(deposit);
        this.productAmount = Money.of(product);
        this.productCost = Money.of(productCost);
        this.amount = Money.of(waterAmount.add(depositAmount).add(productAmount));
        this.paidOnBill = Money.of(paid);
        this.payMethod = payMethod;
        return this;
    }

    /** Cheque number or bank transfer reference of the money paid on the bill. */
    public SalesTxn payReference(String reference) {
        this.payReference = reference;
        return this;
    }

    /** Opening balance migrated from the old ledger: an amount without water / deposit / product split. */
    public SalesTxn openingAmount(BigDecimal amount) {
        this.amount = Money.of(amount);
        this.paidOnBill = Money.ZERO;
        return this;
    }

    public SalesTxn details(String paperBillNo, String note, boolean phoneConfirmed, DamageMode damageMode, SettleMethod settleMethod,
                            String creditOverrideBy) {
        this.paperBillNo = paperBillNo;
        this.note = note;
        this.phoneConfirmed = phoneConfirmed;
        this.damageMode = damageMode;
        this.settleMethod = settleMethod;
        this.creditOverrideBy = creditOverrideBy;
        return this;
    }

    /** A reversal bill: every amount negated, linked to the original. */
    public static SalesTxn reversalOf(SalesTxn original, String billNo, LocalDate date, String note) {
        SalesTxn r = new SalesTxn(billNo, SalesKind.REVERSAL, date, original.customerId, original.walkInName);
        r.waterAmount = original.waterAmount.negate();
        r.depositAmount = original.depositAmount.negate();
        r.productAmount = original.productAmount.negate();
        r.productCost = original.productCost.negate();
        r.amount = original.amount.negate();
        r.paidOnBill = original.paidOnBill.negate();
        r.payMethod = original.payMethod;
        r.payReference = original.payReference;
        r.note = note;
        r.reversalOfId = original.getId();
        return r;
    }

    public void markReversedBy(long reversalId) {
        this.reversedById = reversalId;
    }

    /** What the bill adds to the account balance (positive = the customer owes more). */
    public BigDecimal balanceChange() {
        return Money.of(amount.subtract(paidOnBill));
    }

    public String getBillNo() {
        return billNo;
    }

    public String getPaperBillNo() {
        return paperBillNo;
    }

    public SalesKind getKind() {
        return kind;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getWalkInName() {
        return walkInName;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPaidOnBill() {
        return paidOnBill;
    }

    public String getPayReference() {
        return payReference;
    }

    public PayMethod getPayMethod() {
        return payMethod;
    }

    public BigDecimal getWaterAmount() {
        return waterAmount;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public BigDecimal getProductAmount() {
        return productAmount;
    }

    public BigDecimal getProductCost() {
        return productCost;
    }

    public String getNote() {
        return note;
    }

    public boolean isPhoneConfirmed() {
        return phoneConfirmed;
    }

    public DamageMode getDamageMode() {
        return damageMode;
    }

    public SettleMethod getSettleMethod() {
        return settleMethod;
    }

    public String getCreditOverrideBy() {
        return creditOverrideBy;
    }

    public Long getReversalOfId() {
        return reversalOfId;
    }

    public Long getReversedById() {
        return reversedById;
    }
}
