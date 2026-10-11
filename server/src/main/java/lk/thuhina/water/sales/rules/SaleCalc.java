package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;

/**
 * Result of a rule class: the bill lines, the amount split into water / deposits / products (and product cost),
 * the ledger effects, the customer's bottles after the bill, and the problems that stop saving (empty = can be saved)
 * plus warnings that do not (e.g. a discount).
 */
public record SaleCalc(List<SaleLine> lines, BigDecimal water, BigDecimal deposit, BigDecimal product, BigDecimal productCost,
                       BigDecimal amount, List<Effect> effects, Map<String, Bottles> after, List<Problem> problems,
                       List<String> warnings) {

    public boolean ok() {
        return problems.isEmpty();
    }

    /** Collects lines, amounts, effects and problems while a rule class works. */
    static final class Builder {

        final List<SaleLine> lines = new ArrayList<>();
        final List<Effect> effects = new ArrayList<>();
        final List<Problem> problems = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        BigDecimal water = Money.ZERO;
        BigDecimal deposit = Money.ZERO;
        BigDecimal product = Money.ZERO;
        BigDecimal productCost = Money.ZERO;

        Builder problem(Problem p) {
            problems.add(p);
            return this;
        }

        boolean hasProblems() {
            return !problems.isEmpty();
        }

        /** Bottles after the bill = the customer's bottles + the customer effects. */
        SaleCalc build(CustomerView customer) {
            Map<String, Bottles> after = new LinkedHashMap<>();
            if (customer != null && customer.id() != null) {
                after.putAll(customer.bottles());
                for (Effect e : effects) {
                    if (e.target() != Effect.Target.CUSTOMER_BOTTLE) {
                        continue;
                    }
                    Bottles b = after.getOrDefault(e.bottleTypeCode(), Bottles.NONE);
                    after.put(e.bottleTypeCode(), switch (e.customerField()) {
                        case HELD -> b.plus(e.delta(), 0, 0);
                        case TO_COLLECT -> b.plus(0, e.delta(), 0);
                        case OWED -> b.plus(0, 0, e.delta());
                    });
                }
            }
            BigDecimal amount = Money.of(water.add(deposit).add(product));
            return new SaleCalc(List.copyOf(lines), Money.of(water), Money.of(deposit), Money.of(product), Money.of(productCost),
                    amount, List.copyOf(effects), after, List.copyOf(problems), List.copyOf(warnings));
        }
    }
}
