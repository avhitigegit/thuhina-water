package lk.thuhina.water.sales.rules;

import java.math.BigDecimal;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.customers.model.PayType;

/**
 * Credit limit (BR-09, design 5.6): a Credit or Monthly bill customer with a limit may not go over it with a bill
 * unless the Admin approves. Cash customers and a zero limit are not checked.
 */
public final class CreditCheck {

    public static final String CREDIT_LIMIT = "CREDIT_LIMIT";

    /** Balance before and after the bill, the limit, and whether the bill takes the balance over the limit. */
    public record Result(BigDecimal balance, BigDecimal after, BigDecimal limit, boolean exceeds) {
    }

    private CreditCheck() {
    }

    public static Result check(PayType payType, BigDecimal limit, BigDecimal balance, BigDecimal amount, BigDecimal paid) {
        BigDecimal after = Money.of(Money.of(balance).add(Money.of(amount)).subtract(Money.of(paid)));
        boolean exceeds = payType != PayType.CASH && Money.isPositive(limit) && after.compareTo(Money.of(limit)) > 0
                && Money.of(amount).subtract(Money.of(paid)).signum() > 0;
        return new Result(Money.of(balance), after, Money.of(limit), exceeds);
    }

    /** "Ceylon Hotel would exceed the credit limit (Rs. 12,000.00). Balance after this bill: Rs. 13,840.00. …" */
    public static String message(String name, Result r) {
        return name + " would exceed the credit limit (" + Money.format(r.limit()) + "). Balance after this bill: "
                + Money.format(r.after()) + ". Admin approval is required.";
    }
}
