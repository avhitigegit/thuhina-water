package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.customers.model.PayType;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.sales.model.DamageMode;
import lk.thuhina.water.sales.model.SettleMethod;
import lk.thuhina.water.sales.rules.Bottles;
import lk.thuhina.water.sales.rules.CreditCheck;
import lk.thuhina.water.sales.rules.CustomerDamageRules;
import lk.thuhina.water.sales.rules.ExchangeRules;
import lk.thuhina.water.sales.rules.ExtraOrderRules;
import lk.thuhina.water.sales.rules.FirstPurchaseRules;
import lk.thuhina.water.sales.rules.OwedSettlementRules;
import lk.thuhina.water.sales.rules.Problem;
import lk.thuhina.water.sales.rules.ProductSaleRules;
import lk.thuhina.water.sales.rules.SaleCalc;
import lk.thuhina.water.sales.rules.SaleInputs.BottleInfo;
import lk.thuhina.water.sales.rules.SaleInputs.BrandInfo;
import lk.thuhina.water.sales.rules.SaleInputs.CustomerView;
import lk.thuhina.water.sales.rules.SaleInputs.Price;
import lk.thuhina.water.sales.rules.SaleInputs.ProductInfo;
import org.junit.jupiter.api.Test;

/** The sales rule classes (M06 S3) with the prototype's messages – no database. */
class SalesRulesTest {

    private static final BottleInfo B20 = new BottleInfo("B20", "20L", "20L Bottle");
    private static final BottleInfo B10 = new BottleInfo("B10", "10L", "10L Bottle");
    private static final Price P350 = new Price(new BigDecimal("350"), false);
    private static final Price P270 = new Price(new BigDecimal("270"), true);
    private static final BigDecimal DEP = new BigDecimal("1000");
    private static final BrandInfo AMERICAN = new BrandInfo(1, "American Water", "B20", "20L Bottle", true);

    private static CustomerView customer(Bottles b20) {
        return new CustomerView(7L, "Dilshan Fernando", "Household", Map.of("B20", b20));
    }

    private static List<String> messages(SaleCalc c) {
        return c.problems().stream().map(Problem::message).toList();
    }

    private static int delta(SaleCalc c, Bucket bucket) {
        return c.effects().stream().filter(e -> e.target() == Effect.Target.STOCK && e.bucket() == bucket).mapToInt(Effect::delta).sum();
    }

    private static int delta(SaleCalc c, CustomerField field) {
        return c.effects().stream().filter(e -> e.target() == Effect.Target.CUSTOMER_BOTTLE && e.customerField() == field)
                .mapToInt(Effect::delta).sum();
    }

    // ------------------------------------------------------------------ exchange / left at door / collect

    @Test
    void exchangeWithFewerEmptiesAddsOwedAndNeedsANote() {
        SaleCalc c = ExchangeRules.calculate(customer(new Bottles(3, 0, 0)), false,
                List.of(new ExchangeRules.Line(B20, 3, 2, P270)), null, false);
        assertThat(messages(c)).containsExactly("Fewer empties than filled bottles: 1 bottle(s) will be recorded as owed. "
                + "Add a note explaining the shortage.");
        assertThat(c.problems().getFirst().code()).isEqualTo(Problem.NOTE_REQUIRED);
        assertThat(c.amount()).isEqualByComparingTo("810");
        assertThat(c.lines().getFirst().isAgreedPrice()).isTrue();
        assertThat(delta(c, Bucket.FILLED)).isEqualTo(-3);
        assertThat(delta(c, Bucket.EMPTY)).isEqualTo(2);
        assertThat(delta(c, Bucket.CUSTOMERS)).isEqualTo(1);
        assertThat(delta(c, CustomerField.OWED)).isEqualTo(1);
        assertThat(c.after().get("B20")).isEqualTo(new Bottles(3, 0, 1));
        // With a note it can be saved.
        assertThat(ExchangeRules.calculate(customer(new Bottles(3, 0, 0)), false,
                List.of(new ExchangeRules.Line(B20, 3, 2, P270)), "One bottle at the neighbour", false).ok()).isTrue();
    }

    @Test
    void extraEmptiesClearToCollectFirstThenOwed() {
        SaleCalc c = ExchangeRules.calculate(customer(new Bottles(3, 1, 2)), false,
                List.of(new ExchangeRules.Line(B20, 2, 5, P350)), null, false);
        assertThat(c.ok()).isTrue();
        assertThat(c.lines().getFirst().getToCollectCleared()).isEqualTo(1);
        assertThat(c.lines().getFirst().getOwedCleared()).isEqualTo(2);
        assertThat(c.after().get("B20")).isEqualTo(new Bottles(3, 0, 0));
        assertThat(delta(c, Bucket.CUSTOMERS)).isEqualTo(-3);

        SaleCalc excess = ExchangeRules.calculate(customer(new Bottles(3, 1, 0)), false,
                List.of(new ExchangeRules.Line(B20, 2, 5, P350)), null, false);
        assertThat(messages(excess)).containsExactly("Dilshan Fernando returned 2 more 20L empties than they hold, owe or have "
                + "waiting for collection. Check the bill.");
    }

    @Test
    void exchangeChecks() {
        assertThat(messages(ExchangeRules.calculate(customer(Bottles.NONE), false, List.of(new ExchangeRules.Line(B20, 2, 2, P350)), null,
                false))).first().isEqualTo("Dilshan Fernando has no bottles yet. Record a First purchase instead.");
        assertThat(messages(ExchangeRules.calculate(customer(new Bottles(2, 0, 0)), false,
                List.of(new ExchangeRules.Line(B10, 1, 0, P350)), "x", false)))
                .contains("Dilshan Fernando holds no 10L bottles. Use Extra order (deposit needed) for new bottles.");
        assertThat(messages(ExchangeRules.calculate(customer(new Bottles(2, 0, 0)), false, List.of(), null, false)))
                .containsExactly("Enter the filled bottles given and/or empties collected.");
        assertThat(messages(ExchangeRules.calculate(customer(new Bottles(2, 0, 0)), false,
                List.of(new ExchangeRules.Line(B20, 2, 2, null)), null, false)))
                .containsExactly("No water price set for 20L Bottle / Household.");
    }

    @Test
    void leftAtDoorAddsToCollectAndNeedsPhoneAndNote() {
        SaleCalc c = ExchangeRules.calculate(customer(new Bottles(2, 0, 0)), true, List.of(new ExchangeRules.Line(B20, 2, 2, P350)), null, false);
        assertThat(messages(c)).containsExactly("Confirm that the customer agreed by phone.", "Add a note for the \"Left at door\" delivery.");
        SaleCalc ok = ExchangeRules.calculate(customer(new Bottles(2, 0, 0)), true, List.of(new ExchangeRules.Line(B20, 2, 2, P350)),
                "Gate locked", true);
        assertThat(ok.ok()).isTrue();
        assertThat(ok.lines().getFirst().getEmpties()).isZero();
        assertThat(ok.after().get("B20")).isEqualTo(new Bottles(2, 2, 0));
        assertThat(delta(ok, Bucket.CUSTOMERS)).isEqualTo(2);
        assertThat(delta(ok, Bucket.EMPTY)).isZero();
        assertThat(ok.amount()).isEqualByComparingTo("700");
    }

    @Test
    void collectEmpties() {
        assertThat(messages(ExchangeRules.collect(customer(new Bottles(2, 2, 0)), B20, 0))).containsExactly("Enter the number of empties collected.");
        assertThat(messages(ExchangeRules.collect(customer(new Bottles(2, 2, 0)), B20, 3)))
                .containsExactly("Only 2 20L empties are waiting to be collected from Dilshan Fernando.");
        SaleCalc c = ExchangeRules.collect(customer(new Bottles(2, 2, 0)), B20, 2);
        assertThat(c.amount()).isEqualByComparingTo("0");
        assertThat(delta(c, Bucket.EMPTY)).isEqualTo(2);
        assertThat(delta(c, Bucket.CUSTOMERS)).isEqualTo(-2);
        assertThat(c.after().get("B20")).isEqualTo(new Bottles(2, 0, 0));
    }

    // ------------------------------------------------------------------ first purchase / extra order

    @Test
    void firstPurchaseReferenceScenario() {
        // 3 × 20L with 1 American Water bottle, Household Rs. 350, deposit Rs. 1,000 → Rs. 3,050.
        SaleCalc c = FirstPurchaseRules.calculate(customer(Bottles.NONE), B20, 3, 1, AMERICAN, P350, DEP);
        assertThat(c.ok()).isTrue();
        assertThat(c.water()).isEqualByComparingTo("1050");
        assertThat(c.deposit()).isEqualByComparingTo("2000");
        assertThat(c.amount()).isEqualByComparingTo("3050");
        assertThat(delta(c, Bucket.FILLED)).isEqualTo(-3);
        assertThat(delta(c, Bucket.CUSTOMERS)).isEqualTo(3);
        assertThat(delta(c, Bucket.EMPTY)).isEqualTo(1);
        assertThat(delta(c, CustomerField.HELD)).isEqualTo(3);
    }

    @Test
    void firstPurchaseChecks() {
        assertThat(messages(FirstPurchaseRules.calculate(customer(new Bottles(2, 0, 0)), B20, 1, 0, null, P350, DEP)))
                .containsExactly("Dilshan Fernando already holds 20L bottles. Use Extra order for additional bottles.");
        assertThat(messages(FirstPurchaseRules.calculate(customer(Bottles.NONE), B20, 0, 0, null, P350, DEP)))
                .containsExactly("Enter the number of bottles.");
        assertThat(messages(FirstPurchaseRules.calculate(customer(Bottles.NONE), B20, 2, 3, AMERICAN, P350, DEP)))
                .containsExactly("Old bottles handed in cannot be more than the bottles given.");
        assertThat(messages(FirstPurchaseRules.calculate(customer(Bottles.NONE), B20, 2, 1, null, P350, DEP)))
                .containsExactly("Select an accepted old-bottle brand.");
        assertThat(messages(FirstPurchaseRules.calculate(customer(Bottles.NONE), B20, 2, 1,
                new BrandInfo(1, "American Water", "B20", "20L Bottle", false), P350, DEP))).containsExactly("Select an accepted old-bottle brand.");
        assertThat(messages(FirstPurchaseRules.calculate(customer(Bottles.NONE), B10, 2, 1, AMERICAN, P350, DEP)))
                .containsExactly("American Water bottles are accepted only for 20L Bottle.");
        assertThat(messages(FirstPurchaseRules.calculate(customer(Bottles.NONE), B20, 2, 0, null, P350, null)))
                .containsExactly("Price or deposit not set for this bottle type.");
    }

    @Test
    void extraOrderChargesDepositsOnlyBeyondTheEmptiesReturned() {
        // 5 filled, 3 empties back → 2 new bottles, 1 old bottle → 1 deposit.
        SaleCalc c = ExtraOrderRules.calculate(customer(new Bottles(3, 0, 0)), B20, 5, 3, 1, AMERICAN, P350, DEP);
        assertThat(c.ok()).isTrue();
        assertThat(c.lines().getFirst().getNewBottles()).isEqualTo(2);
        assertThat(c.lines().getFirst().getDepositCount()).isEqualTo(1);
        assertThat(c.amount()).isEqualByComparingTo("2750");
        assertThat(delta(c, Bucket.FILLED)).isEqualTo(-5);
        assertThat(delta(c, Bucket.EMPTY)).isEqualTo(4);
        assertThat(delta(c, Bucket.CUSTOMERS)).isEqualTo(2);
        assertThat(delta(c, CustomerField.HELD)).isEqualTo(2);
        // Filled = empties → no deposit at all.
        assertThat(ExtraOrderRules.calculate(customer(new Bottles(3, 0, 0)), B20, 2, 2, 0, null, P350, DEP).deposit()).isEqualByComparingTo("0");
    }

    @Test
    void extraOrderChecks() {
        CustomerView c = customer(new Bottles(2, 0, 0));
        assertThat(messages(ExtraOrderRules.calculate(c, B20, 0, 0, 0, null, P350, DEP))).containsExactly("Enter the filled bottles ordered.");
        assertThat(messages(ExtraOrderRules.calculate(c, B20, 2, 3, 0, null, P350, DEP)))
                .containsExactly("Empties returned are more than filled bottles. Use Bill entry or Collect empties for returns.");
        assertThat(messages(ExtraOrderRules.calculate(c, B20, 4, 3, 0, null, P350, DEP)))
                .containsExactly("Empties returned cannot be more than the bottles the customer holds (2).");
        assertThat(messages(ExtraOrderRules.calculate(c, B20, 3, 2, 2, AMERICAN, P350, DEP)))
                .containsExactly("Old bottles handed in cannot be more than the new bottles (1).");
    }

    // ------------------------------------------------------------------ damage / settle owed

    @Test
    void customerDamageReplaceChargesDepositAndWater() {
        SaleCalc c = CustomerDamageRules.calculate(customer(new Bottles(3, 0, 0)), B20, 1, DamageMode.REPLACE, true, "Cracked", P350, DEP);
        assertThat(c.ok()).isTrue();
        assertThat(c.amount()).isEqualByComparingTo("1350");
        assertThat(delta(c, Bucket.CUSTOMERS)).isZero();
        assertThat(delta(c, Bucket.FILLED)).isEqualTo(-1);
        assertThat(delta(c, Bucket.WRITTEN_OFF)).isEqualTo(1);
        assertThat(delta(c, CustomerField.HELD)).isZero();
        // Replacement given empty: deposit only, from Empty in store.
        SaleCalc empty = CustomerDamageRules.calculate(customer(new Bottles(3, 0, 0)), B20, 1, DamageMode.REPLACE, false, "Cracked", P350, DEP);
        assertThat(empty.amount()).isEqualByComparingTo("1000");
        assertThat(delta(empty, Bucket.EMPTY)).isEqualTo(-1);
    }

    @Test
    void customerDamageReduceLowersTheHoldingWithoutCharge() {
        SaleCalc c = CustomerDamageRules.calculate(customer(new Bottles(3, 0, 0)), B20, 2, DamageMode.REDUCE, true, "Lost", P350, DEP);
        assertThat(c.amount()).isEqualByComparingTo("0");
        assertThat(delta(c, Bucket.CUSTOMERS)).isEqualTo(-2);
        assertThat(delta(c, Bucket.WRITTEN_OFF)).isEqualTo(2);
        assertThat(c.after().get("B20").held()).isEqualTo(1);
        assertThat(messages(CustomerDamageRules.calculate(customer(new Bottles(3, 0, 0)), B20, 0, DamageMode.REDUCE, true, "x", P350, DEP)))
                .containsExactly("Enter the number of damaged bottles.");
        assertThat(messages(CustomerDamageRules.calculate(customer(new Bottles(3, 0, 0)), B20, 4, DamageMode.REDUCE, true, "x", P350, DEP)))
                .containsExactly("Dilshan Fernando holds only 3 20L bottles.");
        assertThat(messages(CustomerDamageRules.calculate(customer(new Bottles(3, 0, 0)), B20, 1, DamageMode.REDUCE, true, " ", P350, DEP)))
                .containsExactly("Enter how the bottle was damaged.");
    }

    @Test
    void settleOwedByReturnOrDeposit() {
        CustomerView c = customer(new Bottles(3, 0, 2));
        SaleCalc ret = OwedSettlementRules.calculate(c, B20, 2, SettleMethod.RETURN, DEP);
        assertThat(ret.amount()).isEqualByComparingTo("0");
        assertThat(delta(ret, Bucket.EMPTY)).isEqualTo(2);
        assertThat(delta(ret, Bucket.CUSTOMERS)).isEqualTo(-2);
        assertThat(ret.after().get("B20")).isEqualTo(new Bottles(3, 0, 0));
        SaleCalc dep = OwedSettlementRules.calculate(c, B20, 2, SettleMethod.DEPOSIT, DEP);
        assertThat(dep.amount()).isEqualByComparingTo("2000");
        assertThat(delta(dep, Bucket.CUSTOMERS)).isZero();
        assertThat(dep.after().get("B20")).isEqualTo(new Bottles(5, 0, 0));
        assertThat(messages(OwedSettlementRules.calculate(c, B20, 0, SettleMethod.RETURN, DEP))).containsExactly("Enter the number of bottles.");
        assertThat(messages(OwedSettlementRules.calculate(c, B20, 3, SettleMethod.RETURN, DEP)))
                .containsExactly("Dilshan Fernando owes only 2 20L bottles.");
    }

    // ------------------------------------------------------------------ products / credit

    @Test
    void productSale() {
        ProductInfo cup = new ProductInfo("P01", "Water dispenser cup", new BigDecimal("150"), new BigDecimal("90"), true, 10);
        SaleCalc c = ProductSaleRules.calculate(null, "Walk-in Kamal", List.of(new ProductSaleRules.Item(cup, 2, new BigDecimal("140"))),
                new BigDecimal("280"));
        assertThat(c.ok()).isTrue();
        assertThat(c.product()).isEqualByComparingTo("280");
        assertThat(c.productCost()).isEqualByComparingTo("180");
        assertThat(c.warnings()).containsExactly("Water dispenser cup: discount Rs. 10.00 per item (selling price Rs. 150.00).");
        assertThat(messages(ProductSaleRules.calculate(null, "Walk-in", List.of(new ProductSaleRules.Item(cup, 2, null)), new BigDecimal("100"))))
                .containsExactly("Walk-in sales must be paid in full.");
        assertThat(messages(ProductSaleRules.calculate(null, " ", List.of(new ProductSaleRules.Item(cup, 1, null)), new BigDecimal("150"))))
                .containsExactly("Select a customer or enter a walk-in name.");
        assertThat(messages(ProductSaleRules.calculate(customer(Bottles.NONE), null, List.of(), BigDecimal.ZERO)))
                .containsExactly("Add at least one product.");
        assertThat(ProductSaleRules.calculate(customer(Bottles.NONE), null, List.of(new ProductSaleRules.Item(cup, 12, null)), BigDecimal.ZERO)
                .warnings()).containsExactly("Only 10 Water dispenser cup in stock.");
    }

    @Test
    void creditCheck() {
        CreditCheck.Result r = CreditCheck.check(PayType.CREDIT, new BigDecimal("12000"), new BigDecimal("10500"), new BigDecimal("3340"),
                new BigDecimal("0"));
        assertThat(r.exceeds()).isTrue();
        assertThat(CreditCheck.message("Ceylon Hotel", r)).isEqualTo("Ceylon Hotel would exceed the credit limit (Rs. 12,000.00). "
                + "Balance after this bill: Rs. 13,840.00. Admin approval is required.");
        assertThat(CreditCheck.check(PayType.CREDIT, new BigDecimal("12000"), new BigDecimal("10500"), new BigDecimal("3340"),
                new BigDecimal("3000")).exceeds()).isFalse();
        assertThat(CreditCheck.check(PayType.CASH, BigDecimal.ZERO, new BigDecimal("99000"), new BigDecimal("3340"), BigDecimal.ZERO)
                .exceeds()).isFalse();
        // A bill that lowers the balance is never blocked, even when the customer is already over the limit.
        assertThat(CreditCheck.check(PayType.MONTHLY_BILL, new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("700"),
                new BigDecimal("700")).exceeds()).isFalse();
    }
}
