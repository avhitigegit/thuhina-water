package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;

import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.BrandInfo;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;
import lk.thuhina.water.sales.rules.SaleInputs.Price;

/**
 * First purchase of a bottle type (FR-32, FR-33, BR-01, BR-02): water for every bottle plus a deposit for each bottle
 * not covered by an accepted old bottle handed in. Amount = Q × price + (Q − O) × deposit; FILLED −Q, CUSTOMERS +Q,
 * EMPTY +O (old bottles join the pool as empties); held +Q.
 */
public final class FirstPurchaseRules {

    private FirstPurchaseRules() {
    }

    /** {@code c.id()} may be null while previewing a registration (the customer does not exist yet). */
    public static SaleCalc calculate(CustomerView c, BottleInfo bottle, int qty, int oldBottles, BrandInfo brand, Price price,
                                     BigDecimal deposit) {
        SaleCalc.Builder b = new SaleCalc.Builder();
        if (c.bottles(bottle.code()).held() > 0) {
            b.problem(Problem.rule(c.name() + " already holds " + bottle.label() + " bottles. Use Extra order for additional bottles."));
        }
        if (qty < 1) {
            b.problem(Problem.field("qty", "Enter the number of bottles."));
        }
        if (oldBottles < 0) {
            b.problem(Problem.field("oldBottles", "Quantities cannot be negative."));
        } else if (oldBottles > Math.max(qty, 0)) {
            b.problem(Problem.field("oldBottles", "Old bottles handed in cannot be more than the bottles given."));
        }
        OldBottles.check(b, bottle, oldBottles, brand);
        if (price == null || deposit == null) {
            b.problem(Problem.rule("Price or deposit not set for this bottle type."));
        }
        int q = Math.max(qty, 0);
        int old = Math.min(Math.max(oldBottles, 0), q);
        SaleLine l = SaleLine.bottle(bottle.code(), bottle.label()).filled(q).newBottles(q)
                .depositUnit(deposit).depositCount(q - old).oldBottles(old, brand == null ? null : brand.id());
        if (price != null) {
            l.unitPrice(price.price(), price.agreed());
        }
        b.water = l.waterAmount(q);
        b.deposit = l.depositAmount();
        l.lineAmount(b.water.add(b.deposit));
        b.lines.add(l);
        b.effects.add(Effect.stock(bottle.code(), Bucket.FILLED, -q));
        b.effects.add(Effect.stock(bottle.code(), Bucket.CUSTOMERS, q));
        b.effects.add(Effect.stock(bottle.code(), Bucket.EMPTY, old));
        if (c.id() != null) {
            b.effects.add(Effect.customer(c.id(), bottle.code(), CustomerField.HELD, q));
        }
        return b.build(c);
    }
}
