package lk.thuhina.water.quotations.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules.Kind;

/** A quotation line: water or deposit per bottle type, a product, or free text; {@code currentPrice} = the price when quoted. */
@Entity
@Table(name = "cq_line")
public class CqLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "quotation_id")
    private CustomerQuotation quotation;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_kind", nullable = false, length = 10)
    private Kind kind;

    @Column(name = "bottle_type_code", length = 10)
    private String bottleTypeCode;

    @Column(name = "product_code", length = 10)
    private String productCode;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false)
    private int qty;

    @Column(length = 30)
    private String unit;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "current_price", precision = 12, scale = 2)
    private BigDecimal currentPrice;

    @Column(name = "current_agreed", nullable = false)
    private boolean currentAgreed;

    protected CqLine() {
    }

    public CqLine(CustomerQuotation quotation, int lineNo, Kind kind, String bottleTypeCode, String productCode, String description,
                  int qty, String unit, BigDecimal unitPrice, BigDecimal currentPrice, boolean currentAgreed) {
        this.quotation = quotation;
        this.lineNo = lineNo;
        this.kind = kind;
        this.bottleTypeCode = bottleTypeCode;
        this.productCode = productCode;
        this.description = description;
        this.qty = qty;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.currentPrice = currentPrice;
        this.currentAgreed = currentAgreed;
    }

    public int getLineNo() {
        return lineNo;
    }

    public Kind getKind() {
        return kind;
    }

    public String getBottleTypeCode() {
        return bottleTypeCode;
    }

    public String getProductCode() {
        return productCode;
    }

    public String getDescription() {
        return description;
    }

    public int getQty() {
        return qty;
    }

    public String getUnit() {
        return unit;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public BigDecimal getCurrentPrice() {
        return currentPrice;
    }

    public boolean isCurrentAgreed() {
        return currentAgreed;
    }
}
