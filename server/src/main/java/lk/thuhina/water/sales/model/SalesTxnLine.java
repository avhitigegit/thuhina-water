package lk.thuhina.water.sales.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lk.thuhina.water.sales.rules.SaleLine;

/** A stored bill line (design 4.3 {@code sales_txn_line}), insert-only; see {@link SaleLine} for the meaning of each field. */
@Entity
@Table(name = "sales_txn_line")
public class SalesTxnLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_id", nullable = false)
    private Long txnId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "bottle_type_code", length = 10)
    private String bottleTypeCode;

    @Column(name = "product_code", length = 10)
    private String productCode;

    private int filled;
    private int empties;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @Column(name = "agreed_price")
    private boolean agreedPrice;

    @Column(name = "deposit_unit")
    private BigDecimal depositUnit;

    @Column(name = "deposit_count")
    private int depositCount;

    @Column(name = "old_bottles")
    private int oldBottles;

    @Column(name = "old_brand_id")
    private Long oldBrandId;

    @Column(name = "new_bottles")
    private int newBottles;

    @Column(name = "owed_added")
    private int owedAdded;

    @Column(name = "owed_cleared")
    private int owedCleared;

    @Column(name = "to_collect_added")
    private int toCollectAdded;

    @Column(name = "to_collect_cleared")
    private int toCollectCleared;

    private int damaged;
    private int qty;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    @Column(name = "line_amount", nullable = false)
    private BigDecimal lineAmount;

    protected SalesTxnLine() {
    }

    /** {@code sign} −1 stores a reversal's copy with a negative amount. */
    public static SalesTxnLine of(long txnId, int lineNo, SaleLine l, int sign) {
        SalesTxnLine s = new SalesTxnLine();
        s.txnId = txnId;
        s.lineNo = lineNo;
        s.bottleTypeCode = l.getBottleTypeCode();
        s.productCode = l.getProductCode();
        s.filled = l.getFilled();
        s.empties = l.getEmpties();
        s.unitPrice = l.getUnitPrice();
        s.agreedPrice = l.isAgreedPrice();
        s.depositUnit = l.getDepositUnit();
        s.depositCount = l.getDepositCount();
        s.oldBottles = l.getOldBottles();
        s.oldBrandId = l.getOldBrandId();
        s.newBottles = l.getNewBottles();
        s.owedAdded = l.getOwedAdded();
        s.owedCleared = l.getOwedCleared();
        s.toCollectAdded = l.getToCollectAdded();
        s.toCollectCleared = l.getToCollectCleared();
        s.damaged = l.getDamaged();
        s.qty = l.getQty();
        s.unitCost = l.getUnitCost();
        s.lineAmount = sign < 0 ? l.getLineAmount().negate() : l.getLineAmount();
        return s;
    }

    /** Back to a {@link SaleLine} for details, prints and reversals; {@code label} = "20L" or the product name. */
    public SaleLine toSaleLine(String label) {
        SaleLine l = bottleTypeCode != null ? SaleLine.bottle(bottleTypeCode, label) : SaleLine.product(productCode, label);
        return l.filled(filled).empties(empties).unitPrice(unitPrice, agreedPrice).depositUnit(depositUnit).depositCount(depositCount)
                .oldBottles(oldBottles, oldBrandId).newBottles(newBottles).owedAdded(owedAdded).owedCleared(owedCleared)
                .toCollectAdded(toCollectAdded).toCollectCleared(toCollectCleared).damaged(damaged).qty(qty).unitCost(unitCost)
                .lineAmount(lineAmount);
    }

    public Long getTxnId() {
        return txnId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public String getProductCode() {
        return productCode;
    }
}
