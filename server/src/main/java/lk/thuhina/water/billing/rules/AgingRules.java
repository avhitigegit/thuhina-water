package lk.thuhina.water.billing.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import lk.thuhina.water.common.Money;

/**
 * Aging of a customer's balance (design 5.6, FR-51, prototype {@code Q.aging}):
 * <ul>
 *   <li>charges = bills with net (amount − paid on the bill) &gt; 0, by date;</li>
 *   <li>credits = payments + bills with net &lt; 0 (reversals, settlements);</li>
 *   <li>credits settle the <b>oldest</b> charges first; what is left of each charge goes in a bucket by the days since the
 *       bill date: 0–30, 31–60, 61–90, 90+;</li>
 *   <li>credit left over after every charge is settled is an advance: the total is negative and every bucket 0.</li>
 * </ul>
 * The account lists use the same calculation in one SQL query ({@code AgingService}); the tests check both agree.
 */
public final class AgingRules {

    private AgingRules() {
    }

    /** One bill: date and net (amount − paid on the bill). Bills on the same date keep their order. */
    public record Bill(LocalDate date, BigDecimal net) {
    }

    /** Buckets and total (total = Σ buckets − advance = the account balance on {@code asOf}); {@code oldest} unpaid bill date. */
    public record Aging(BigDecimal days0to30, BigDecimal days31to60, BigDecimal days61to90, BigDecimal over90, BigDecimal total,
                        LocalDate oldest) {

        public static final Aging NONE = new Aging(Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO, null);

        /** Amount older than 30 days. */
        public BigDecimal olderThan30() {
            return days31to60.add(days61to90).add(over90);
        }
    }

    /** Bucket of an amount {@code days} old: 0 = 0–30, 1 = 31–60, 2 = 61–90, 3 = 90+. */
    public static int bucket(long days) {
        if (days <= 30) {
            return 0;
        }
        if (days <= 60) {
            return 1;
        }
        return days <= 90 ? 2 : 3;
    }

    /**
     * Ages the bills and payments dated on or before {@code asOf}.
     *
     * @param bills    the customer's bills (any order; sorted by date here, keeping the given order on the same date)
     * @param payments Σ payments up to {@code asOf}
     */
    public static Aging age(List<Bill> bills, BigDecimal payments, LocalDate asOf) {
        BigDecimal credit = Money.of(payments);
        for (Bill b : bills) {
            if (!b.date().isAfter(asOf) && b.net().signum() < 0) {
                credit = credit.add(b.net().negate());
            }
        }
        List<Bill> charges = bills.stream().filter(b -> !b.date().isAfter(asOf) && b.net().signum() > 0)
                .sorted(Comparator.comparing(Bill::date)).toList();
        BigDecimal[] buckets = {Money.ZERO, Money.ZERO, Money.ZERO, Money.ZERO};
        BigDecimal total = Money.ZERO;
        LocalDate oldest = null;
        for (Bill c : charges) {
            BigDecimal use = credit.min(c.net());
            credit = credit.subtract(use);
            BigDecimal left = Money.of(c.net().subtract(use));
            if (left.signum() <= 0) {
                continue;
            }
            int k = bucket(ChronoUnit.DAYS.between(c.date(), asOf));
            buckets[k] = buckets[k].add(left);
            total = total.add(left);
            if (oldest == null) {
                oldest = c.date();
            }
        }
        total = Money.of(total.subtract(credit));
        return new Aging(buckets[0], buckets[1], buckets[2], buckets[3], total, oldest);
    }
}
