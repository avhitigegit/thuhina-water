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
 * Extra order (FR-39, FR-33): more filled bottles than usual. Bottles beyond the empties returned are new bottles for
 * the customer and need a deposit (or an old bottle). New = max(0, F − E); deposits = new − O;
 * amount = F × price + deposits × deposit; FILLED −F, EMPTY +(E + O), CUSTOMERS +(F − E); held += new.
 */
public final class ExtraOrderRules {

    private ExtraOrderRules() {
    }

    public static SaleCalc calculate(CustomerView c, BottleInfo bottle, int filled, int empties, int oldBottles, BrandInfo brand,
                                     Price price, BigDecimal deposit) {
        SaleCalc.Builder b = new SaleCalc.Builder();
        int held = c.bottles(bottle.code()).held();
        if (filled < 1) {
            b.problem(Problem.field("filled", "Enter the filled bottles ordered."));
        }
        if (empties < 0 || oldBottles < 0) {
            b.problem(Problem.field("empties", "Quantities cannot be negative."));
        }
        int f = Math.max(filled, 0);
        int e = Math.max(empties, 0);
        if (e > f) {
            b.problem(Problem.field("empties", "Empties returned are more than filled bottles. Use Bill entry or Collect empties for returns."));
        } else if (e > held) {
            b.problem(Problem.field("empties", "Empties returned cannot be more than the bottles the customer holds (" + held + ")."));
        }
        int newBottles = Math.max(0, f - e);
        if (oldBottles > newBottles) {
            b.problem(Problem.field("oldBottles", "Old bottles handed in cannot be more than the new bottles (" + newBottles + ")."));
        }
        OldBottles.check(b, bottle, oldBottles, brand);
        int old = Math.min(Math.max(oldBottles, 0), newBottles);
        int deposits = newBottles - old;
        if (price == null || (deposits > 0 && deposit == null)) {
            b.problem(Problem.rule("Price or deposit not set for this bottle type."));
        }
        SaleLine l = SaleLine.bottle(bottle.code(), bottle.label()).filled(f).empties(e).newBottles(newBottles)
                .depositUnit(deposit).depositCount(deposits).oldBottles(old, brand == null ? null : brand.id());
        if (price != null) {
            l.unitPrice(price.price(), price.agreed());
        }
        b.water = l.waterAmount(f);
        b.deposit = l.depositAmount();
        l.lineAmount(b.water.add(b.deposit));
        b.lines.add(l);
        b.effects.add(Effect.stock(bottle.code(), Bucket.FILLED, -f));
        b.effects.add(Effect.stock(bottle.code(), Bucket.EMPTY, e + old));
        b.effects.add(Effect.stock(bottle.code(), Bucket.CUSTOMERS, f - e));
        b.effects.add(Effect.customer(c.id(), bottle.code(), CustomerField.HELD, newBottles));
        return b.build(c);
    }
}
