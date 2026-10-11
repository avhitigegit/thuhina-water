package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;

import io.swagger.v3.oas.annotations.media.Schema;
import lk.thuhina.water.common.Money;

/**
 * One line of a bill (design 4.3 {@code sales_txn_line}): a bottle type or a product, with the quantities, the prices
 * used on the day (agreed or standard, deposit) and what changed in the customer's bottles. Built by the rule classes,
 * stored with the bill and returned by previews and bill details. Zero / null means "not used by this kind of bill".
 */
public class SaleLine {

    private String bottleTypeCode;
    private String productCode;
    private String label;
    private int filled;
    private int empties;
    private BigDecimal unitPrice;
    private boolean agreedPrice;
    private BigDecimal depositUnit;
    private int depositCount;
    private int oldBottles;
    private Long oldBrandId;
    private int newBottles;
    private int owedAdded;
    private int owedCleared;
    private int toCollectAdded;
    private int toCollectCleared;
    private int damaged;
    private int qty;
    private BigDecimal unitCost;
    private BigDecimal lineAmount = Money.ZERO;

    public static SaleLine bottle(String bottleTypeCode, String label) {
        SaleLine l = new SaleLine();
        l.bottleTypeCode = bottleTypeCode;
        l.label = label;
        return l;
    }

    public static SaleLine product(String productCode, String name) {
        SaleLine l = new SaleLine();
        l.productCode = productCode;
        l.label = name;
        return l;
    }

    /** Water part of the line: filled × unit price (customer damage: replacement bottles × price). */
    public BigDecimal waterAmount(int bottles) {
        return unitPrice == null ? Money.ZERO : Money.times(unitPrice, bottles);
    }

    public BigDecimal depositAmount() {
        return depositUnit == null ? Money.ZERO : Money.times(depositUnit, depositCount);
    }

    @Schema(nullable = true)
    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    @Schema(nullable = true)
    public String getProductCode() {
        return productCode;
    }

    /** "20L" for a bottle type, the product name for a product. */
    public String getLabel() {
        return label;
    }

    public int getFilled() {
        return filled;
    }

    public SaleLine filled(int v) {
        filled = v;
        return this;
    }

    public int getEmpties() {
        return empties;
    }

    public SaleLine empties(int v) {
        empties = v;
        return this;
    }

    @Schema(nullable = true)
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public SaleLine unitPrice(BigDecimal v, boolean agreed) {
        unitPrice = v == null ? null : Money.of(v);
        agreedPrice = agreed;
        return this;
    }

    /** True when the water price is the customer's agreed price (shown as "(agreed price)"). */
    public boolean isAgreedPrice() {
        return agreedPrice;
    }

    @Schema(nullable = true)
    public BigDecimal getDepositUnit() {
        return depositUnit;
    }

    public SaleLine depositUnit(BigDecimal v) {
        depositUnit = v == null ? null : Money.of(v);
        return this;
    }

    public int getDepositCount() {
        return depositCount;
    }

    public SaleLine depositCount(int v) {
        depositCount = v;
        return this;
    }

    public int getOldBottles() {
        return oldBottles;
    }

    @Schema(nullable = true)
    public Long getOldBrandId() {
        return oldBrandId;
    }

    public SaleLine oldBottles(int v, Long brandId) {
        oldBottles = v;
        oldBrandId = v > 0 ? brandId : null;
        return this;
    }

    public int getNewBottles() {
        return newBottles;
    }

    public SaleLine newBottles(int v) {
        newBottles = v;
        return this;
    }

    public int getOwedAdded() {
        return owedAdded;
    }

    public SaleLine owedAdded(int v) {
        owedAdded = v;
        return this;
    }

    public int getOwedCleared() {
        return owedCleared;
    }

    public SaleLine owedCleared(int v) {
        owedCleared = v;
        return this;
    }

    public int getToCollectAdded() {
        return toCollectAdded;
    }

    public SaleLine toCollectAdded(int v) {
        toCollectAdded = v;
        return this;
    }

    public int getToCollectCleared() {
        return toCollectCleared;
    }

    public SaleLine toCollectCleared(int v) {
        toCollectCleared = v;
        return this;
    }

    public int getDamaged() {
        return damaged;
    }

    public SaleLine damaged(int v) {
        damaged = v;
        return this;
    }

    /** Products: quantity sold; settle owed / collect empties: bottles. */
    public int getQty() {
        return qty;
    }

    public SaleLine qty(int v) {
        qty = v;
        return this;
    }

    @Schema(nullable = true)
    public BigDecimal getUnitCost() {
        return unitCost;
    }

    public SaleLine unitCost(BigDecimal v) {
        unitCost = v == null ? null : Money.of(v);
        return this;
    }

    public BigDecimal getLineAmount() {
        return lineAmount;
    }

    public SaleLine lineAmount(BigDecimal v) {
        lineAmount = Money.of(v);
        return this;
    }
}
