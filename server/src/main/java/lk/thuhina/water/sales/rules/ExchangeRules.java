package lk.thuhina.water.sales.rules;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;
import lk.thuhina.water.sales.rules.SaleInputs.Price;

/**
 * Delivery bills (design 5.2, BR-03, BR-04, BR-07): an exchange of filled bottles for empties, "Left at door" (the
 * empties are collected later), and collecting those empties.
 */
public final class ExchangeRules {

    /** One bottle type on a delivery bill. */
    public record Line(BottleInfo bottle, int filled, int empties, Price price) {
    }

    private ExchangeRules() {
    }

    /**
     * Per bottle type: {@code diff = F − E}; more filled than empties → owed += diff (a note is needed); more empties →
     * clear empties to collect first, then owed; anything left over is an error. Left at door: empties = 0 and
     * to collect += F. Amount = F × price. Stock: FILLED −F, EMPTY +E, CUSTOMERS +(F − E).
     */
    public static SaleCalc calculate(CustomerView c, boolean leftAtDoor, List<Line> input, String note, boolean phoneConfirmed) {
        SaleCalc.Builder b = new SaleCalc.Builder();
        Set<String> seen = new HashSet<>();
        int owedAdded = 0;
        for (Line in : input) {
            int f = in.filled();
            int e = leftAtDoor ? 0 : in.empties();
            if (f < 0 || e < 0) {
                b.problem(Problem.field("lines", "Quantities cannot be negative."));
                continue;
            }
            if (f == 0 && e == 0) {
                continue;
            }
            String code = in.bottle().code();
            if (!seen.add(code)) {
                b.problem(Problem.field("lines", "Enter each bottle type once."));
                continue;
            }
            Bottles now = c.bottles(code);
            SaleLine l = SaleLine.bottle(code, in.bottle().label()).filled(f).empties(e);
            if (in.price() != null) {
                l.unitPrice(in.price().price(), in.price().agreed());
            }
            if (leftAtDoor) {
                l.toCollectAdded(f);
            } else if (f > e) {
                l.owedAdded(f - e);
            } else if (e > f) {
                int extra = e - f;
                int fromCollect = Math.min(extra, now.toCollect());
                extra -= fromCollect;
                int fromOwed = Math.min(extra, now.owed());
                extra -= fromOwed;
                l.toCollectCleared(fromCollect).owedCleared(fromOwed);
                if (extra > 0) {
                    b.problem(Problem.rule(c.name() + " returned " + extra + " more " + in.bottle().label()
                            + " empties than they hold, owe or have waiting for collection. Check the bill."));
                }
            }
            if (f > 0 && now.held() == 0) {
                b.problem(Problem.rule(c.name() + " holds no " + in.bottle().label()
                        + " bottles. Use Extra order (deposit needed) for new bottles."));
            }
            if (in.price() == null && f > 0) {
                b.problem(Problem.rule("No water price set for " + in.bottle().name() + " / " + c.typeName() + "."));
            }
            l.lineAmount(l.waterAmount(f));
            b.water = b.water.add(l.getLineAmount());
            owedAdded += l.getOwedAdded();
            b.lines.add(l);

            b.effects.add(Effect.stock(code, Bucket.FILLED, -f));
            b.effects.add(Effect.stock(code, Bucket.EMPTY, e));
            b.effects.add(Effect.stock(code, Bucket.CUSTOMERS, f - e));
            b.effects.add(Effect.customer(c.id(), code, CustomerField.TO_COLLECT, l.getToCollectAdded() - l.getToCollectCleared()));
            b.effects.add(Effect.customer(c.id(), code, CustomerField.OWED, l.getOwedAdded() - l.getOwedCleared()));
        }

        if (b.lines.isEmpty() && !b.hasProblems()) {
            b.problem(Problem.field("lines", "Enter the filled bottles given and/or empties collected."));
        }
        if (!leftAtDoor && c.totalHeld() == 0) {
            b.problems.addFirst(Problem.rule(c.name() + " has no bottles yet. Record a First purchase instead."));
        }
        if (leftAtDoor) {
            if (!phoneConfirmed) {
                b.problem(Problem.field("phoneConfirmed", "Confirm that the customer agreed by phone."));
            }
            if (Texts.blankToNull(note) == null) {
                b.problem(Problem.field("note", "Add a note for the \"Left at door\" delivery."));
            }
        } else if (owedAdded > 0 && Texts.blankToNull(note) == null) {
            b.problem(new Problem("note", Problem.NOTE_REQUIRED, "Fewer empties than filled bottles: " + owedAdded
                    + " bottle(s) will be recorded as owed. Add a note explaining the shortage."));
        }
        return b.build(c);
    }

    /** Empties left at the door collected later (FR-36): EMPTY +Q, CUSTOMERS −Q, to collect −Q; no charge. */
    public static SaleCalc collect(CustomerView c, BottleInfo bottle, int qty) {
        SaleCalc.Builder b = new SaleCalc.Builder();
        int waiting = c.bottles(bottle.code()).toCollect();
        if (qty < 1) {
            b.problem(Problem.field("qty", "Enter the number of empties collected."));
        } else if (qty > waiting) {
            b.problem(Problem.field("qty", "Only " + waiting + " " + bottle.label() + " empties are waiting to be collected from "
                    + c.name() + "."));
        }
        int q = Math.max(qty, 0);
        b.lines.add(SaleLine.bottle(bottle.code(), bottle.label()).empties(q).qty(q).toCollectCleared(q).lineAmount(Money.ZERO));
        b.effects.add(Effect.stock(bottle.code(), Bucket.EMPTY, q));
        b.effects.add(Effect.stock(bottle.code(), Bucket.CUSTOMERS, -q));
        b.effects.add(Effect.customer(c.id(), bottle.code(), CustomerField.TO_COLLECT, -q));
        return b.build(c);
    }
}
