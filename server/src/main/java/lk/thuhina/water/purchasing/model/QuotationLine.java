package lk.thuhina.water.purchasing.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A quoted unit price for one requested item; {@code unitPrice} null = the supplier did not quote it. */
@Entity
@Table(name = "quotation_line")
public class QuotationLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @Column(name = "item_code", nullable = false, length = 10)
    private String itemCode;

    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    protected QuotationLine() {
    }

    QuotationLine(Quotation quotation, String itemCode, BigDecimal unitPrice) {
        this.quotation = quotation;
        this.itemCode = itemCode;
        this.unitPrice = unitPrice;
    }

    public String getItemCode() {
        return itemCode;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }
}
