package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Reversal (BR-14), paper bill numbers (BR-13), bill list / details and the customer history (M06 S9 – S11). */
class SalesReversalTest extends SalesTestSupport {

    private Map<String, Object> bill(long customerId, String paper, int filled, int empties, String note) {
        return map("customerId", customerId, "date", today.toString(), "paperBillNo", paper,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", filled, "empties", empties)), "note", note);
    }

    private Map<String, Object> reverse(Map<String, Object> txn, String reason) throws Exception {
        return sale("/sales/txns/" + txnId(txn) + "/reverse", map("reason", reason));
    }

    /** Stock buckets + the customer's bottles + balance, to compare before and after a reversal. */
    private List<Object> state(long customerId) {
        return List.of(stock("FILLED"), stock("EMPTY"), stock("CUSTOMERS"), stock("WRITTEN_OFF"), List.of(bottles(customerId)[0],
                bottles(customerId)[1], bottles(customerId)[2]), balance(customerId).stripTrailingZeros());
    }

    @Test
    void reversingEachKindRestoresStockBottlesAndMoney() throws Exception {
        long c = customerHolding("Reverse", 3);
        String today = this.today.toString();
        // 2 bottles owed first, so "settle owed" has something to settle.
        sale("/sales/bills", bill(c, "O" + System.nanoTime(), 3, 1, "Two kept"));
        List<Map<String, Object>> requests = List.of(
                map("url", "/sales/bills", "body", bill(c, "R" + System.nanoTime(), 3, 1, "Two kept")),
                map("url", "/sales/extra-order", "body", map("customerId", c, "date", today, "bottleTypeCode", bottle, "filled", 2, "oldBottles", 1,
                        "oldBrandId", brandId, "paid", 500)),
                map("url", "/sales/settle-owed", "body", map("customerId", c, "date", today, "bottleTypeCode", bottle, "qty", 1,
                        "method", "DEPOSIT")),
                map("url", "/sales/customer-damage", "body", map("customerId", c, "date", today, "bottleTypeCode", bottle, "qty", 1,
                        "reason", "Cracked", "paid", 1350)),
                map("url", "/sales/customer-damage", "body", map("customerId", c, "date", today, "bottleTypeCode", bottle, "qty", 1,
                        "mode", "REDUCE", "reason", "Lost")));
        for (Map<String, Object> r : requests) {
            List<Object> before = state(c);
            @SuppressWarnings("unchecked")
            Map<String, Object> txn = sale((String) r.get("url"), (Map<String, Object>) r.get("body"));
            assertThat(state(c)).as("changed by " + r.get("url")).isNotEqualTo(before);
            Map<String, Object> rev = reverse(txn, "Entered by mistake");
            assertThat(state(c)).as("restored after reversing " + r.get("url")).isEqualTo(before);
            assertThat(rev.get("kind")).isEqualTo("REVERSAL");
            assertThat(((Number) rev.get("amount")).doubleValue()).isEqualTo(-((Number) txn.get("amount")).doubleValue());
            @SuppressWarnings("unchecked")
            Map<String, Object> of = (Map<String, Object>) rev.get("reversalOf");
            assertThat(of.get("billNo")).isEqualTo(txn.get("billNo"));
        }
        // The customer-damage records are marked reversed.
        assertThat(jdbc.queryForObject("select count(*) from damage_record d join sales_txn t on t.id = d.source_id "
                + "where t.customer_id = ? and not d.reversed", Integer.class, c)).isZero();
    }

    @Test
    void reversalOfFirstPurchaseCollectAndLeftAtDoor() throws Exception {
        long c = register(customer("Fresh " + System.nanoTime()));
        Map<String, Object> first = sale("/sales/first-purchase", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "qty", 2));
        Map<String, Object> door = bill(c, "D" + System.nanoTime(), 2, 0, "Left with the guard");
        door.put("kind", "LEFT_AT_DOOR");
        door.put("phoneConfirmed", true);
        sale("/sales/bills", door);
        List<Object> afterDoor = state(c);
        Map<String, Object> collect = sale("/sales/collect-empties", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "qty", 2));
        reverse(collect, "Wrong customer");
        assertThat(state(c)).isEqualTo(afterDoor);
        // Reversing the first purchase takes the 2 bottles held back to store; the 2 empties to collect stay.
        int filled = stock("FILLED");
        reverse(first, "Duplicate entry");
        assertThat(bottles(c)).containsExactly(0, 2, 0);
        assertThat(stock("FILLED")).isEqualTo(filled + 2);
    }

    @Test
    void reversalRules() throws Exception {
        long c = customerHolding("Rules", 2);
        Map<String, Object> txn = sale("/sales/bills", bill(c, "X" + System.nanoTime(), 2, 2, null));
        send("/sales/txns/" + txnId(txn) + "/reverse", map("reason", " "))
                .andExpect(jsonPath("$.details.fields.reason").value("Enter the reason for the reversal."));
        Map<String, Object> rev = reverse(txn, "Wrong date");
        send("/sales/txns/" + txnId(txn) + "/reverse", map("reason", "again"))
                .andExpect(jsonPath("$.code").value("ALREADY_REVERSED"))
                .andExpect(jsonPath("$.message").value("Already reversed by " + rev.get("billNo") + "."));
        send("/sales/txns/" + txnId(rev) + "/reverse", map("reason", "again"))
                .andExpect(jsonPath("$.message").value("This entry cannot be reversed."));
        String audit = jdbc.queryForObject("select details from audit_log where action = 'REVERSE' and ref = ?", String.class, txn.get("billNo"));
        assertThat(audit).isEqualTo("Reversal entry " + rev.get("billNo") + " – Wrong date");
        // The original's details show the link and the reason.
        mvc.perform(get("/sales/txns/" + txnId(txn)).cookie(admin))
                .andExpect(jsonPath("$.reversedBy.billNo").value(rev.get("billNo")))
                .andExpect(jsonPath("$.reversedBy.note").value("Reversal of bill " + txn.get("billNo") + ": Wrong date"));
    }

    @Test
    void reversalFailsWhenStockHasMovedOn() throws Exception {
        long c = customerHolding("Moved", 2);
        // 2 owed bottles returned as empties, then the empties left the store (counted to zero): reversing the return
        // would make Empty in store negative.
        sale("/sales/bills", bill(c, "M" + System.nanoTime(), 2, 0, "Empties not ready"));
        Map<String, Object> settle = sale("/sales/settle-owed", map("customerId", c, "date", today.toString(), "bottleTypeCode", bottle,
                "qty", 2, "method", "RETURN"));
        count("EMPTY", 0);
        send("/sales/txns/" + txnId(settle) + "/reverse", map("reason", "Mistake"))
                .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("Empty in store (" + label + ") cannot go below zero")));
    }

    @Test
    void paperBillNumberOnceOnlyAndFreeAgainAfterReversal() throws Exception {
        long c = customerHolding("Paper", 2);
        long other = customerHolding("Other", 2);
        String no = "41" + System.nanoTime() % 100000000;
        Map<String, Object> txn = sale("/sales/bills", bill(c, no, 2, 2, null));
        String name = (String) txn.get("customerName");
        send("/sales/bills", bill(other, no.toLowerCase(), 2, 2, null))
                .andExpect(jsonPath("$.code").value("DUPLICATE_PAPER_BILL"))
                .andExpect(jsonPath("$.message").value("Paper bill " + no + " was already entered on " + lk.thuhina.water.common.BusinessDates.format(today)
                        + " (Exchange, " + name + "). A bill number can be entered only once."));
        mvc.perform(get("/sales/paper-bill/" + no).cookie(admin))
                .andExpect(jsonPath("$.entered").value(true))
                .andExpect(jsonPath("$.message").value("Bill " + no + " is already entered – Exchange, " + name + ", "
                        + lk.thuhina.water.common.BusinessDates.format(today) + ". Do not enter it again."));
        reverse(txn, "Wrong customer");
        mvc.perform(get("/sales/paper-bill/" + no).cookie(admin))
                .andExpect(jsonPath("$.entered").value(false))
                .andExpect(jsonPath("$.message").value("Bill " + no + " has not been entered yet."));
        sale("/sales/bills", bill(other, no, 2, 2, null));
    }

    @Test
    @SuppressWarnings("unchecked")
    void billListDetailsAndHistoryWithRunningBalance() throws Exception {
        long c = customerHolding("History", 2);
        String no = "H" + System.nanoTime();
        Map<String, Object> txn = sale("/sales/bills", map("customerId", c, "date", today.toString(), "paperBillNo", no,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 2)), "paid", 200));
        mvc.perform(get("/sales/txns").param("q", no.substring(2)).cookie(admin))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].billNo").value(txn.get("billNo")))
                .andExpect(jsonPath("$.items[0].filled").value(2))
                .andExpect(jsonPath("$.items[0].empties").value(2));
        mvc.perform(get("/sales/txns").param("customerId", String.valueOf(c)).param("kind", "FIRST_PURCHASE").cookie(admin))
                .andExpect(jsonPath("$.total").value(1));
        mvc.perform(get("/sales/txns").param("customerId", String.valueOf(c)).param("from", today.plusDays(1).toString()).cookie(admin))
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/sales/txns/" + txnId(txn)).cookie(admin))
                .andExpect(jsonPath("$.lines[0].filled").value(2))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(350.0))
                .andExpect(jsonPath("$.effects[?(@.place == 'Filled in store')].change").value(org.hamcrest.Matchers.contains(-2)))
                .andExpect(jsonPath("$.createdByName").value("Test Admin"));

        List<Map<String, Object>> history = json.readValue(mvc.perform(get("/customers/" + c + "/history").cookie(admin)).andReturn()
                .getResponse().getContentAsString(), List.class);
        List<Map<String, Object>> sales = history.stream().filter(h -> "SALE".equals(h.get("kind"))).toList();
        assertThat(sales).hasSize(2);
        // Newest first: the exchange (balance 700 − 200 = 500 after a fully paid first purchase).
        assertThat(sales.getFirst()).containsEntry("billNo", txn.get("billNo")).containsEntry("balance", 500.0);
        assertThat(sales.get(1)).containsEntry("balance", 0.0);
        assertThat(balance(c)).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(LocalDate.parse((String) sales.getFirst().get("date"))).isEqualTo(today);
        mvc.perform(json(post("/sales/txns/999999999/reverse"), map("reason", "x"))).andExpect(status().isNotFound());
    }
}
