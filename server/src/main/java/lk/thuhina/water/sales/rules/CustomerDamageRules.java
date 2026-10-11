package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.sales.model.DamageMode;
import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;
import lk.thuhina.water.sales.rules.SaleInputs.Price;

/**
 * Customer-damaged bottle (FR-37, BR-06). The damaged bottles are written off from With customers.
 * <ul>
 *   <li>REPLACE: a new bottle is given and the deposit is charged; given filled (default) it also charges the water and
 *       comes from Filled in store, otherwise from Empty in store. The holding does not change.</li>
 *   <li>REDUCE: no new bottle and no charge; the holding goes down.</li>
 * </ul>
 */
public final class CustomerDamageRules {

    private CustomerDamageRules() {
    }

    public static SaleCalc calculate(CustomerView c, BottleInfo bottle, int qty, DamageMode mode, boolean giveFilled, String reason,
                                     Price price, BigDecimal deposit) {
        SaleCalc.Builder b = new SaleCalc.Builder();
        int held = c.bottles(bottle.code()).held();
        if (qty < 1) {
            b.problem(Problem.field("qty", "Enter the number of damaged bottles."));
        } else if (qty > held) {
            b.problem(Problem.field("qty", c.name() + " holds only " + held + " " + bottle.label() + " bottles."));
        }
        if (Texts.blankToNull(reason) == null) {
            b.problem(Problem.field("reason", "Enter how the bottle was damaged."));
        }
        int q = Math.max(qty, 0);
        String code = bottle.code();
        SaleLine l = SaleLine.bottle(code, bottle.label()).damaged(q);
        b.effects.add(Effect.stock(code, Bucket.CUSTOMERS, -q));
        b.effects.add(Effect.stock(code, Bucket.WRITTEN_OFF, q));
        if (mode == DamageMode.REDUCE) {
            b.effects.add(Effect.customer(c.id(), code, CustomerField.HELD, -q));
            l.lineAmount(Money.ZERO);
        } else {
            if (deposit == null || (giveFilled && price == null)) {
                b.problem(Problem.rule("Price or deposit not set for this bottle type."));
            }
            l.newBottles(q).depositUnit(deposit).depositCount(q);
            if (giveFilled) {
                l.filled(q);
                if (price != null) {
                    l.unitPrice(price.price(), price.agreed());
                }
                b.water = l.waterAmount(q);
            }
            b.deposit = l.depositAmount();
            l.lineAmount(b.water.add(b.deposit));
            b.effects.add(Effect.stock(code, giveFilled ? Bucket.FILLED : Bucket.EMPTY, -q));
            b.effects.add(Effect.stock(code, Bucket.CUSTOMERS, q));
        }
        b.lines.add(l);
        return b.build(c);
    }
}
