package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;
import java.util.List;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;
import lk.thuhina.water.sales.rules.SaleInputs.ProductInfo;

/**
 * Other product sale (FR-40) to a registered customer or a walk-in. The price defaults to the selling price (a lower
 * price is allowed and shown as a discount warning); product stock goes down (BR-12); the cost is kept for profit.
 * A walk-in must pay in full.
 */
public final class ProductSaleRules {

    /** A product on the bill; {@code unitPrice} null = the selling price. */
    public record Item(ProductInfo product, int qty, BigDecimal unitPrice) {
    }

    private ProductSaleRules() {
    }

    /** {@code customer} = null for a walk-in. */
    public static SaleCalc calculate(CustomerView customer, String walkInName, List<Item> items, BigDecimal paid) {
        SaleCalc.Builder b = new SaleCalc.Builder();
        if (customer == null && Texts.blankToNull(walkInName) == null) {
            b.problem(Problem.field("customerId", "Select a customer or enter a walk-in name."));
        }
        for (Item i : items) {
            if (i.product() == null || i.qty() <= 0) {
                continue;
            }
            ProductInfo p = i.product();
            BigDecimal price = i.unitPrice() == null ? p.sellingPrice() : i.unitPrice();
            if (price.signum() < 0) {
                b.problem(Problem.field("items", "Price cannot be negative."));
                continue;
            }
            if (!p.active()) {
                b.problem(Problem.field("items", p.name() + " is no longer sold."));
            }
            if (price.compareTo(p.sellingPrice()) < 0) {
                b.warnings.add(p.name() + ": discount " + Money.format(p.sellingPrice().subtract(price)) + " per item (selling price "
                        + Money.format(p.sellingPrice()) + ").");
            }
            if (i.qty() > p.stock()) {
                b.warnings.add("Only " + p.stock() + " " + p.name() + " in stock.");
            }
            SaleLine l = SaleLine.product(p.code(), p.name()).qty(i.qty()).unitPrice(price, false).unitCost(p.costPrice());
            l.lineAmount(Money.times(price, i.qty()));
            b.product = b.product.add(l.getLineAmount());
            b.productCost = b.productCost.add(Money.times(p.costPrice(), i.qty()));
            b.lines.add(l);
            b.effects.add(Effect.product(p.code(), -i.qty()));
        }
        if (b.lines.isEmpty()) {
            b.problem(Problem.field("items", "Add at least one product."));
        }
        if (customer == null && Money.of(paid).compareTo(Money.of(b.product)) < 0) {
            b.problem(Problem.field("paid", "Walk-in sales must be paid in full."));
        }
        return b.build(customer);
    }
}
