package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;

/** M06 sales through the API: every sale type, its stock / bottle / money effects and messages. */
class SalesIntegrationTest extends SalesTestSupport {

    private Map<String, Object> bill(long customerId, String paper, int filled, int empties, String note) {
        return map("customerId", customerId, "date", today.toString(), "paperBillNo", paper,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", filled, "empties", empties)), "note", note);
    }

    private static String paper() {
        return "P" + System.nanoTime() % 10_000_000_000L;
    }

    // ------------------------------------------------------------------ exchange / left at door / collect

    @Test
    void exchangePostsStockBottlesMoneyAndMovesTheNextDelivery() throws Exception {
        long c = customerHolding("Dilshan", 3);
        int filled = stock("FILLED");
        int empty = stock("EMPTY");

        // 3 filled, 2 empties: 1 owed – a note is required (NOTE_REQUIRED).
        String no = paper();
        send("/sales/bills", bill(c, no, 3, 2, null)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NOTE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Fewer empties than filled bottles: 1 bottle(s) will be recorded as owed. "
                        + "Add a note explaining the shortage."));
        Map<String, Object> txn = sale("/sales/bills", bill(c, no, 3, 2, "One bottle at the neighbour"));
        assertThat(txn.get("kind")).isEqualTo("EXCHANGE");
        assertThat(txn.get("billNo").toString()).matches("B\\d{6}");
        assertThat(((Number) txn.get("amount")).doubleValue()).isEqualTo(1050.0);
        assertThat(stock("FILLED")).isEqualTo(filled - 3);
        assertThat(stock("EMPTY")).isEqualTo(empty + 2);
        assertThat(bottles(c)).containsExactly(3, 0, 1);
        assertThat(balance(c)).isEqualByComparingTo("1050");
    }

    @SuppressWarnings("unchecked")
    @Test
    void scheduleAdvancesPastTheBillDateButNeverBack() throws Exception {
        long c = customerHolding("Schedule", 2);
        LocalDate next = jdbc.queryForObject("select next_delivery_date from customer where id = ?", LocalDate.class, c);
        // A bill dated before the next delivery does not move it.
        Map<String, Object> r = read(send("/sales/bills", bill(c, paper(), 2, 2, null)).andReturn());
        assertThat((Map<String, Object>) r.get("schedule")).containsEntry("before", next.toString()).containsEntry("after", next.toString());
        // A missed delivery two weeks ago: the bill today moves it to the first delivery day after today.
        LocalDate missed = next.minusWeeks(2);
        jdbc.update("update customer set next_delivery_date = ? where id = ?", missed, c);
        r = read(send("/sales/bills", bill(c, paper(), 2, 2, null)).andReturn());
        assertThat((Map<String, Object>) r.get("schedule")).containsEntry("before", missed.toString())
                .containsEntry("after", ScheduleRules.advance(missed, 1, today).toString());
        assertThat(jdbc.queryForObject("select next_delivery_date from customer where id = ?", LocalDate.class, c)).isAfter(today);
    }

    @Test
    void exchangeMessages() throws Exception {
        long holder = customerHolding("Holder", 2);
        long none = register(customer("Nobody " + System.nanoTime()));
        send("/sales/bills", bill(none, paper(), 2, 2, null)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("has no bottles yet. Record a First purchase instead.")));
        send("/sales/bills", bill(holder, paper(), 0, 4, null)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("returned 4 more " + label + " empties than they hold")));
        send("/sales/bills", bill(holder, null, 2, 2, null))
                .andExpect(jsonPath("$.details.fields.paperBillNo").value("Enter the paper bill number."));
        send("/sales/bills", map("customerId", holder, "date", today.toString(), "paperBillNo", paper(), "lines", List.of()))
                .andExpect(jsonPath("$.details.fields.lines").value("Enter the filled bottles given and/or empties collected."));
        send("/sales/bills", map("customerId", holder, "date", today.plusDays(1).toString(), "paperBillNo", paper(),
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 1, "empties", 1))))
                .andExpect(jsonPath("$.details.fields.date").value("Date cannot be after today (" + BusinessDates.format(today) + ")."));
    }

    @Test
    void leftAtDoorThenCollectEmpties() throws Exception {
        long c = customerHolding("Door", 2);
        Map<String, Object> door = bill(c, paper(), 2, 0, null);
        door.put("kind", "LEFT_AT_DOOR");
        send("/sales/bills", door).andExpect(jsonPath("$.details.fields.phoneConfirmed").value("Confirm that the customer agreed by phone."));
        door.put("phoneConfirmed", true);
        send("/sales/bills", door).andExpect(jsonPath("$.details.fields.note").value("Add a note for the \"Left at door\" delivery."));
        door.put("note", "Gate locked – customer phoned");
        Map<String, Object> txn = sale("/sales/bills", door);
        assertThat(txn.get("phoneConfirmed")).isEqualTo(true);
        assertThat(bottles(c)).containsExactly(2, 2, 0);

        send("/sales/collect-empties", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "qty", 3))
                .andExpect(jsonPath("$.details.fields.qty").value("Only 2 " + label + " empties are waiting to be collected from "
                        + txn.get("customerName") + "."));
        int empty = stock("EMPTY");
        Map<String, Object> collected = sale("/sales/collect-empties", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "qty", 2));
        assertThat(((Number) collected.get("amount")).doubleValue()).isZero();
        assertThat(bottles(c)).containsExactly(2, 0, 0);
        assertThat(stock("EMPTY")).isEqualTo(empty + 2);
    }

    @Test
    void previewShowsTheCalculationAndBottlesAfterWithoutSaving() throws Exception {
        long c = customerHolding("Preview", 3);
        long bills = jdbc.queryForObject("select count(*) from sales_txn", Long.class);
        mvc.perform(json(post("/sales/bills/preview"), bill(c, null, 3, 1, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(1050.0))
                .andExpect(jsonPath("$.after[0].owed").value(2))
                .andExpect(jsonPath("$.after[0].owedChange").value(2))
                .andExpect(jsonPath("$.credit.exceeds").value(false))
                .andExpect(jsonPath("$.problems[0].field").value("paperBillNo"))
                .andExpect(jsonPath("$.problems[1].code").value("NOTE_REQUIRED"));
        assertThat(jdbc.queryForObject("select count(*) from sales_txn", Long.class)).isEqualTo(bills);
    }

    // ------------------------------------------------------------------ first purchase / extra order

    @Test
    @SuppressWarnings("unchecked")
    void firstPurchaseAndExtraOrder() throws Exception {
        long c = register(customer("Kasun " + System.nanoTime()));
        send("/sales/first-purchase", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "qty", 3, "oldBottles", 1))
                .andExpect(jsonPath("$.details.fields.oldBrandId").value("Select an accepted old-bottle brand."));
        Map<String, Object> first = sale("/sales/first-purchase", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "qty", 3, "oldBottles", 1, "oldBrandId", brandId, "paid", 1000));
        assertThat(((Number) first.get("amount")).doubleValue()).isEqualTo(3050.0);
        assertThat(bottles(c)).containsExactly(3, 0, 0);
        assertThat(balance(c)).isEqualByComparingTo("2050");
        send("/sales/first-purchase", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "qty", 1))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("already holds " + label
                        + " bottles. Use Extra order for additional bottles.")));

        // Extra order: 4 filled, 2 empties back → 2 new bottles → 2 deposits.
        int filled = stock("FILLED");
        Map<String, Object> extra = sale("/sales/extra-order", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "filled", 4, "empties", 2, "paid", 0));
        assertThat(((Number) extra.get("amount")).doubleValue()).isEqualTo(4 * 350 + 2 * 1000.0);
        assertThat(bottles(c)).containsExactly(5, 0, 0);
        assertThat(stock("FILLED")).isEqualTo(filled - 4);
        send("/sales/extra-order", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "filled", 2, "empties", 3))
                .andExpect(jsonPath("$.details.fields.empties")
                        .value("Empties returned are more than filled bottles. Use Bill entry or Collect empties for returns."));
        // The brand's "taken in so far" count.
        List<Map<String, Object>> brands = json.readValue(mvc.perform(get("/old-bottle-brands").cookie(admin)).andReturn().getResponse()
                .getContentAsString(), List.class);
        assertThat(brands).anySatisfy(b -> assertThat(b).containsEntry("name", brandName).containsEntry("takenIn", 1));
    }

    // ------------------------------------------------------------------ customer damage / settle owed

    @Test
    void customerDamageReplaceAndReduce() throws Exception {
        long c = customerHolding("Damage", 3);
        int filled = stock("FILLED");
        int written = stock("WRITTEN_OFF");
        Map<String, Object> replace = sale("/sales/customer-damage", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "qty", 1, "reason", "Cracked when dropped"));
        assertThat(((Number) replace.get("amount")).doubleValue()).isEqualTo(1350.0);
        assertThat(replace.get("damageMode")).isEqualTo("REPLACE");
        assertThat(bottles(c)).containsExactly(3, 0, 0);
        assertThat(stock("FILLED")).isEqualTo(filled - 1);
        assertThat(stock("WRITTEN_OFF")).isEqualTo(written + 1);
        Map<String, Object> damage = jdbc.queryForMap("select responsibility, location, customer_id, reversed from damage_record "
                + "where source_type = 'SALES_TXN' and source_id = ?", txnId(replace));
        assertThat(damage).containsEntry("responsibility", "CUSTOMER").containsEntry("location", "AT_CUSTOMER")
                .containsEntry("reversed", false);

        Map<String, Object> reduce = sale("/sales/customer-damage", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "qty", 1, "mode", "REDUCE", "reason", "Lost"));
        assertThat(((Number) reduce.get("amount")).doubleValue()).isZero();
        assertThat(bottles(c)).containsExactly(2, 0, 0);
        send("/sales/customer-damage", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "qty", 1))
                .andExpect(jsonPath("$.details.fields.reason").value("Enter how the bottle was damaged."));

        // Inventory "Record damage" with responsibility Customer creates the same kind of bill.
        mvc.perform(json(post("/stock/damage"), map("date", today.toString(), "bottleTypeCode", bottle, "qty", 1, "reason", "Broken handle",
                        "responsibility", "CUSTOMER", "customerId", c, "mode", "REPLACE", "giveFilled", false, "paid", 1000)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.responsibility").value("CUSTOMER"))
                .andExpect(jsonPath("$.where").value("At customer"));
        assertThat(bottles(c)).containsExactly(2, 0, 0);
    }

    @Test
    void settleOwedByReturnAndByDeposit() throws Exception {
        long c = customerHolding("Owes", 3);
        sale("/sales/bills", bill(c, paper(), 3, 0, "All empties missing"));
        assertThat(bottles(c)).containsExactly(3, 0, 3);
        send("/sales/settle-owed", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "qty", 4))
                .andExpect(jsonPath("$.details.fields.qty").value(org.hamcrest.Matchers.endsWith("owes only 3 " + label + " bottles.")));
        int empty = stock("EMPTY");
        sale("/sales/settle-owed", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle, "qty", 2, "method", "RETURN"));
        assertThat(bottles(c)).containsExactly(3, 0, 1);
        assertThat(stock("EMPTY")).isEqualTo(empty + 2);
        Map<String, Object> dep = sale("/sales/settle-owed", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "qty", 1, "method", "DEPOSIT", "paid", 1000));
        assertThat(((Number) dep.get("amount")).doubleValue()).isEqualTo(1000.0);
        assertThat(bottles(c)).containsExactly(4, 0, 0);
    }

    // ------------------------------------------------------------------ product sale

    @Test
    void productSaleWalkInMustPayInFull() throws Exception {
        Map<String, Object> p = read(mvc.perform(json(post("/products"), map("name", "Cup " + System.nanoTime(), "sellingPrice", 150,
                "costPrice", 90, "openingStock", 10))).andReturn());
        String code = (String) p.get("code");
        Map<String, Object> req = map("walkInName", "Walk-in Kamal", "date", today.toString(),
                "items", List.of(map("productCode", code, "qty", 2)), "paid", 100);
        send("/sales/product-sale", req).andExpect(jsonPath("$.details.fields.paid").value("Walk-in sales must be paid in full."));
        req.put("paid", 300);
        req.put("payMethod", "BANK_TRANSFER");
        Map<String, Object> txn = sale("/sales/product-sale", req);
        assertThat(txn.get("customerName")).isEqualTo("Walk-in Kamal");
        assertThat(txn.get("payMethod")).isEqualTo("Bank transfer");
        assertThat(((Number) txn.get("productCost")).doubleValue()).isEqualTo(180.0);
        assertThat(jdbc.queryForObject("select stock_qty from product where code = ?", Integer.class, code)).isEqualTo(8);
        send("/sales/product-sale", map("date", today.toString(), "items", List.of(map("productCode", code, "qty", 1)), "paid", 150))
                .andExpect(jsonPath("$.details.fields.customerId").value("Select a customer or enter a walk-in name."));
        // Not enough stock → BR-12.
        send("/sales/product-sale", map("walkInName", "Big order", "date", today.toString(), "items", List.of(map("productCode", code, "qty", 9)),
                "paid", 1350)).andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"));
    }

    // ------------------------------------------------------------------ common checks

    @Test
    void inactiveCustomerCannotBuy() throws Exception {
        long c = customerHolding("Inactive", 2);
        mvc.perform(json(post("/customers/" + c + "/deactivate"), map("date", today.toString(), "reason", "Moved"))).andExpect(status().isNoContent());
        send("/sales/bills", bill(c, paper(), 2, 2, null)).andExpect(jsonPath("$.code").value("CUSTOMER_INACTIVE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("is inactive. Reactivate the customer before entering sales.")));
    }

    @Test
    void creditLimitNeedsAdminApprovalWhichIsAudited() throws Exception {
        Map<String, Object> req = customer("Ceylon Hotel " + System.nanoTime());
        req.put("payType", "CREDIT");
        req.put("creditLimit", 2000);
        req.put("termsDays", 30);
        req.put("bottlesGiven", Map.of(bottle, 1));
        long c = register(req);
        assertThat(balance(c)).isEqualByComparingTo("1350");

        Map<String, Object> b = bill(c, paper(), 3, 1, "Two extra kept");
        send("/sales/bills", b).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CREDIT_LIMIT"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("would exceed the credit limit (Rs. 2,000.00). "
                        + "Balance after this bill: Rs. 2,400.00. Admin approval is required.")))
                .andExpect(jsonPath("$.details.after").value(2400.0));

        // Other roles cannot approve (they cannot enter sales at all).
        b.put("approveOverLimit", true);
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        mvc.perform(json(post("/sales/bills"), staff, b)).andExpect(status().isForbidden());

        Map<String, Object> txn = sale("/sales/bills", b);
        assertThat(txn.get("creditOverrideBy")).isNotNull();
        String details = jdbc.queryForObject("select details from audit_log where action = 'APPROVE' and entity = 'Credit limit' and ref = ?",
                String.class, txn.get("billNo"));
        assertThat(details).isEqualTo("Over-limit sale approved by Test Admin");
        assertThat(balance(c)).isEqualByComparingTo("2400");
    }

    @Test
    void accountantHasNoAccessToSales() throws Exception {
        Cookie acc = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/sales/txns").cookie(acc)).andExpect(status().isForbidden());
        mvc.perform(json(post("/sales/bills/preview"), acc, map())).andExpect(status().isForbidden());
        mvc.perform(get("/print/bill/1").cookie(acc)).andExpect(status().isForbidden());
    }
}
