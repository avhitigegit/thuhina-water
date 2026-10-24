package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.sales.model.SettleMethod;
import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;

/**
 * Settle bottles owed (FR-38, BR-07). RETURN: the bottles come back as empties (owed −Q, EMPTY +Q, CUSTOMERS −Q).
 * DEPOSIT: the customer keeps them and pays the deposit (owed −Q, held +Q, charge Q × deposit).
 */
public final class OwedSettlementRules {

    private OwedSettlementRules() {
    }

    public static SaleCalc calculate(CustomerView c, BottleInfo bottle, int qty, SettleMethod method, BigDecimal deposit) {
        SaleCalc.Builder b = new SaleCalc.Builder();
        int owed = c.bottles(bottle.code()).owed();
        if (qty < 1) {
            b.problem(Problem.field("qty", "Enter the number of bottles."));
        } else if (qty > owed) {
            b.problem(Problem.field("qty", c.name() + " owes only " + owed + " " + bottle.label() + " bottles."));
        }
        int q = Math.max(qty, 0);
        String code = bottle.code();
        SaleLine l = SaleLine.bottle(code, bottle.label()).qty(q).owedCleared(q);
        b.effects.add(Effect.customer(c.id(), code, CustomerField.OWED, -q));
        if (method == SettleMethod.DEPOSIT) {
            if (deposit == null) {
                b.problem(Problem.rule("Price or deposit not set for this bottle type."));
            }
            l.depositUnit(deposit).depositCount(q);
            b.deposit = l.depositAmount();
            l.lineAmount(b.deposit);
            b.effects.add(Effect.customer(c.id(), code, CustomerField.HELD, q));
        } else {
            l.empties(q).lineAmount(Money.ZERO);
            b.effects.add(Effect.stock(code, Bucket.EMPTY, q));
            b.effects.add(Effect.stock(code, Bucket.CUSTOMERS, -q));
        }
        b.lines.add(l);
        return b.build(c);
    }
}
