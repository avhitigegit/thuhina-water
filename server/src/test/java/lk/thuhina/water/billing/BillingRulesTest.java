package lk.thuhina.water.billing;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import lk.thuhina.water.billing.rules.AgingRules;
import lk.thuhina.water.billing.rules.AgingRules.Aging;
import lk.thuhina.water.billing.rules.AgingRules.Bill;
import lk.thuhina.water.billing.rules.InvoiceRules;
import lk.thuhina.water.billing.rules.InvoiceRules.InvoiceTotals;
import lk.thuhina.water.billing.rules.PaymentRules;
import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.sales.model.PayMethod;
import org.junit.jupiter.api.Test;

/** M11 rules: aging FIFO (design 5.6, FR-51), payment references (FR-50), invoice months, totals and skips (FR-49). */
class BillingRulesTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 14);

    private static Bill bill(int daysAgo, String net) {
        return new Bill(AS_OF.minusDays(daysAgo), new BigDecimal(net));
    }

    private static void assertBuckets(Aging a, String b0, String b30, String b60, String b90, String total) {
        assertThat(a.days0to30()).isEqualByComparingTo(b0);
        assertThat(a.days31to60()).isEqualByComparingTo(b30);
        assertThat(a.days61to90()).isEqualByComparingTo(b60);
        assertThat(a.over90()).isEqualByComparingTo(b90);
        assertThat(a.total()).isEqualByComparingTo(total);
    }

    @Test
    void bucketsByDaysSinceTheBillDate() {
        assertThat(AgingRules.bucket(0)).isZero();
        assertThat(AgingRules.bucket(30)).isZero();
        assertThat(AgingRules.bucket(31)).isEqualTo(1);
        assertThat(AgingRules.bucket(60)).isEqualTo(1);
        assertThat(AgingRules.bucket(61)).isEqualTo(2);
        assertThat(AgingRules.bucket(90)).isEqualTo(2);
        assertThat(AgingRules.bucket(91)).isEqualTo(3);
    }

    @Test
    void paymentsSettleTheOldestBillsFirst() {
        List<Bill> bills = List.of(bill(10, "700"), bill(120, "700"), bill(45, "700"), bill(75, "700"));
        // Nothing paid: one bill in every bucket.
        assertBuckets(AgingRules.age(bills, BigDecimal.ZERO, AS_OF), "700", "700", "700", "700", "2800");
        // Rs. 500 paid: part of the 120-day bill is settled, Rs. 200 of it is still over 90 days.
        Aging a = AgingRules.age(bills, new BigDecimal("500"), AS_OF);
        assertBuckets(a, "700", "700", "700", "200", "2300");
        assertThat(a.oldest()).isEqualTo(AS_OF.minusDays(120));
        // Rs. 1,000 paid: the 120-day bill is gone, Rs. 400 left of the 75-day bill.
        a = AgingRules.age(bills, new BigDecimal("1000"), AS_OF);
        assertBuckets(a, "700", "700", "400", "0", "1800");
        assertThat(a.oldest()).isEqualTo(AS_OF.minusDays(75));
        assertThat(a.olderThan30()).isEqualByComparingTo("1100");
    }

    @Test
    void reversalsAndSettlementsAreCreditsLikePayments() {
        // A reversal (net −700) settles the oldest bill, not the bill it reverses.
        List<Bill> bills = List.of(bill(120, "700"), bill(75, "700"), bill(45, "700"), bill(10, "700"), bill(0, "-700"));
        assertBuckets(AgingRules.age(bills, new BigDecimal("500"), AS_OF), "700", "700", "200", "0", "1600");
        // Paid in full on the bill (net 0) adds nothing.
        assertBuckets(AgingRules.age(List.of(bill(5, "0")), BigDecimal.ZERO, AS_OF), "0", "0", "0", "0", "0");
    }

    @Test
    void overPaymentIsAnAdvanceAndLaterBillsAreIgnored() {
        Aging a = AgingRules.age(List.of(bill(40, "700"), bill(-3, "700")), new BigDecimal("1000"), AS_OF);
        assertBuckets(a, "0", "0", "0", "0", "-300");
        assertThat(a.oldest()).isNull();
        assertThat(AgingRules.age(List.of(), BigDecimal.ZERO, AS_OF)).isEqualTo(Aging.NONE);
    }

    @Test
    void bankTransferAndChequeNeedAReference() {
        assertThat(PaymentRules.referenceProblem(PayMethod.CASH, null)).isNull();
        assertThat(PaymentRules.referenceProblem(PayMethod.BANK_TRANSFER, " ")).isEqualTo("Enter the bank reference.");
        assertThat(PaymentRules.referenceProblem(PayMethod.CHEQUE, null)).isEqualTo("Enter the cheque number.");
        assertThat(PaymentRules.referenceProblem(PayMethod.CHEQUE, "330981")).isNull();
        assertThat(PaymentRules.isPartPayment(new BigDecimal("2000"), new BigDecimal("6760"))).isTrue();
        assertThat(PaymentRules.isPartPayment(new BigDecimal("6760"), new BigDecimal("6760"))).isFalse();
        assertThat(PaymentRules.isAdvance(new BigDecimal("7000"), new BigDecimal("6760"))).isTrue();
        assertThat(PaymentRules.isAdvance(new BigDecimal("100"), new BigDecimal("-50"))).isTrue();
        assertThat(PaymentRules.how(PayMethod.CHEQUE, "Cheque 330981")).isEqualTo("Cheque (Cheque 330981)");
    }

    @Test
    void invoiceMonthsTotalsSkipsAndDueDate() {
        assertThat(InvoiceRules.periods(AS_OF)).containsExactly("2026-10", "2026-09", "2026-08", "2026-07");
        assertThat(InvoiceRules.defaultPeriod(AS_OF)).isEqualTo("2026-09");
        assertThat(InvoiceRules.periodProblem("2026-09", AS_OF)).isNull();
        assertThat(InvoiceRules.periodProblem("2026-11", AS_OF)).isEqualTo("Choose this month or one of the 3 months before it.");
        assertThat(InvoiceRules.periodProblem("2026-13", AS_OF)).isEqualTo("Choose the month.");
        assertThat(InvoiceRules.periodLabel("2026-09")).isEqualTo("September 2026");

        InvoiceTotals t = InvoiceTotals.of(new BigDecimal("12760"), new BigDecimal("8400"), new BigDecimal("1400"), new BigDecimal("6000"));
        assertThat(t.amount()).isEqualByComparingTo("7000");
        assertThat(t.totalDue()).isEqualByComparingTo("13760");

        assertThat(InvoiceRules.skipReason(PayType.CREDIT, false, 3, BigDecimal.TEN)).isEqualTo("Not a Monthly bill customer");
        assertThat(InvoiceRules.skipReason(PayType.MONTHLY_BILL, true, 3, BigDecimal.TEN)).isEqualTo("Invoice already exists");
        assertThat(InvoiceRules.skipReason(PayType.MONTHLY_BILL, false, 0, BigDecimal.ZERO)).isEqualTo("No deliveries and nothing due");
        assertThat(InvoiceRules.skipReason(PayType.MONTHLY_BILL, false, 0, new BigDecimal("6760"))).isNull();
        assertThat(InvoiceRules.skipReason(PayType.MONTHLY_BILL, false, 2, new BigDecimal("-100"))).isNull();

        assertThat(InvoiceRules.dueDate(LocalDate.of(2026, 9, 1), 30)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(InvoiceRules.dueDate(LocalDate.of(2026, 9, 1), 0)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(InvoiceRules.dueDate(LocalDate.of(2026, 9, 1), 14)).isEqualTo(LocalDate.of(2026, 9, 15));
    }
}
