package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M07 Enter Bills – SERVER (S1 – S4): the due list (design 5.4), the suggested next paper bill number, the future-date
 * rule and a day of six grid rows posted one by one, with the invariants checked after each test (SalesTestSupport).
 */
class EnterBillsIntegrationTest extends SalesTestSupport {

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> due(LocalDate date) throws Exception {
        MvcResult r = mvc.perform(get("/delivery/due?area=" + areaId + (date == null ? "" : "&date=" + date)).cookie(admin)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return read(r);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> list) {
        return (List<Map<String, Object>>) list.get("items");
    }

    private static List<String> names(Map<String, Object> list) {
        return items(list).stream().map(x -> (String) x.get("name")).toList();
    }

    private static Map<String, Object> item(Map<String, Object> list, long customerId) {
        return items(list).stream().filter(x -> ((Number) x.get("id")).longValue() == customerId).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> bottleOf(Map<String, Object> item) {
        return ((List<Map<String, Object>>) item.get("bottles")).stream().filter(b -> bottle.equals(b.get("bottleTypeCode")))
                .findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> counts(Map<String, Object> list) {
        return (Map<String, Object>) list.get("counts");
    }

    /** A customer registered today (no bottles) with the given day, cycle and next delivery date. */
    private long scheduled(String name, DayOfWeek day, int cycle, LocalDate next) throws Exception {
        Map<String, Object> req = customer(name);
        req.put("deliveryDay", day.getValue());
        req.put("cycleWeeks", cycle);
        req.put("nextDeliveryDate", next.toString());
        return register(req);
    }

    /**
     * A cash customer holding {@code held} bottles, registered (with its first purchase) four weeks ago – so today has no
     * delivery bill yet. A first purchase counts as "entered" on its date (design 5.4).
     */
    private long holding(String name, int held) throws Exception {
        LocalDate since = today.minusWeeks(4);
        Map<String, Object> req = customer(name + " " + System.nanoTime());
        req.put("registeredOn", since.toString());
        req.put("nextDeliveryDate", ScheduleRules.firstDelivery(since, DayOfWeek.MONDAY).toString());
        req.put("bottlesGiven", Map.of(bottle, held));
        req.put("paid", held * 1350);
        return register(req);
    }

    /** Makes the customer due today (delivery day = today's weekday, next delivery = today). */
    private void dueToday(long customerId) {
        jdbc.update("update customer set delivery_day = ?, next_delivery_date = ?, registered_on = ? where id = ?",
                today.getDayOfWeek().getValue(), today, today.minusWeeks(4), customerId);
    }

    private String nextPaper() throws Exception {
        Object next = read(mvc.perform(get("/sales/paper-bill/next").cookie(admin)).andReturn()).get("next");
        return next == null ? "1" : (String) next;
    }

    private Map<String, Object> row(long customerId, String paper, String kind, int filled, int empties, Object paid, String note,
                                    Boolean phone) {
        Map<String, Object> m = map("customerId", customerId, "date", today.toString(), "paperBillNo", paper, "kind", kind,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", filled, "empties", empties)), "paid", paid, "note", note);
        m.put("phoneConfirmed", phone);
        return m;
    }

    // ------------------------------------------------------------------ S1 – due list

    @Test
    void dueListFollowsWeeklyTwoWeeklyAndFourWeeklyCycles() throws Exception {
        LocalDate base = ScheduleRules.firstDelivery(today, DayOfWeek.MONDAY);
        String tag = " " + System.nanoTime();
        scheduled("A weekly" + tag, DayOfWeek.MONDAY, 1, base);
        scheduled("B two-weekly" + tag, DayOfWeek.MONDAY, 2, base);
        scheduled("C four-weekly" + tag, DayOfWeek.MONDAY, 4, base.plusWeeks(1));
        scheduled("D tuesday" + tag, DayOfWeek.TUESDAY, 1, ScheduleRules.firstDelivery(today, DayOfWeek.TUESDAY));

        assertThat(names(due(base))).containsExactly("A weekly" + tag, "B two-weekly" + tag);
        assertThat(names(due(base.plusWeeks(1)))).containsExactly("A weekly" + tag, "C four-weekly" + tag);
        assertThat(names(due(base.plusWeeks(2)))).containsExactly("A weekly" + tag, "B two-weekly" + tag);
        assertThat(names(due(base.plusWeeks(3)))).containsExactly("A weekly" + tag);
        assertThat(names(due(base.plusWeeks(4)))).containsExactly("A weekly" + tag, "B two-weekly" + tag);
        assertThat(names(due(base.plusWeeks(5)))).containsExactly("A weekly" + tag, "C four-weekly" + tag);
        // Before the registration date nobody is due, even on the right weekday and cycle.
        assertThat(names(due(base.minusWeeks(4)))).isEmpty();

        Map<String, Object> list = due(base);
        assertThat(list.get("dayName")).isEqualTo("Monday");
        assertThat(list.get("today")).isEqualTo(false);
        Map<String, Object> a = items(list).getFirst();
        assertThat(a.get("due")).isEqualTo(true);
        assertThat(a.get("overdue")).isEqualTo(false);
        // New customers without bottles are counted apart and not listed in the grid.
        assertThat(a.get("noBottles")).isEqualTo(true);
        assertThat(a.get("listed")).isEqualTo(false);
        assertThat(counts(list)).containsEntry("due", 2).containsEntry("listed", 0).containsEntry("noBottles", 2);
        assertThat(a.get("nextAfterBill")).isEqualTo(base.plusWeeks(1).toString());
    }

    @Test
    void dueRowsHaveUsualQuantitiesBottlesPricesAndBalance() throws Exception {
        long c = holding("Prefilled", 3);
        dueToday(c);
        // Left at door last trip: 2 empties still to collect, so the grid asks for usual 2 + 2 empties.
        sale("/sales/bills", row(c, "P" + System.nanoTime(), "LEFT_AT_DOOR", 2, 0, 0, "Left with the guard", true));
        dueToday(c);
        mvc.perform(json(post("/customers/" + c + "/prices"), map("bottleTypeCode", bottle, "price", 320, "from", today.minusDays(1).toString(),
                "reason", "Volume price"))).andExpect(status().isCreated());

        Map<String, Object> x = item(due(today), c);
        int[] b = bottles(c);
        Map<String, Object> line = bottleOf(x);
        assertThat(line).containsEntry("usual", 2).containsEntry("held", b[0]).containsEntry("toCollect", 2)
                .containsEntry("filled", 2).containsEntry("empties", 4).containsEntry("agreedPrice", true);
        assertThat(((Number) line.get("price")).doubleValue()).isEqualTo(320.0);
        assertThat(((Number) line.get("standardPrice")).doubleValue()).isEqualTo(350.0);
        assertThat(((Number) x.get("balance")).doubleValue()).isEqualTo(balance(c).doubleValue());
        assertThat(x.get("payTypeLabel")).isEqualTo("Cash");
        assertThat(x.get("code").toString()).matches("C\\d{4,}");
    }

    @Test
    void missedCustomerAppearsTodayButNotOnOtherDays() throws Exception {
        long c = holding("Missed", 2);
        LocalDate lastWeek = today.minusDays(3);
        // Delivery day three days ago, never delivered: overdue today only.
        jdbc.update("update customer set delivery_day = ?, next_delivery_date = ?, registered_on = ? where id = ?",
                lastWeek.getDayOfWeek().getValue(), lastWeek, today.minusWeeks(4), c);

        Map<String, Object> x = item(due(null), c);
        assertThat(x).containsEntry("due", false).containsEntry("overdue", true).containsEntry("daysLate", 3).containsEntry("listed", true);
        assertThat(counts(due(today))).containsEntry("overdue", 1).containsEntry("listed", 1);
        assertThat(items(due(today.plusDays(1)))).isEmpty();
        // A bill today moves the next delivery to the first delivery day after today, so it is no longer missed.
        sale("/sales/bills", row(c, "P" + System.nanoTime(), "EXCHANGE", 2, 2, 700, null, null));
        assertThat(items(due(today))).isEmpty();
    }

    @Test
    void enteredCustomersAreListedApartWithTheirBills() throws Exception {
        long c = holding("Entered", 2);
        long other = holding("Waiting", 2);
        dueToday(c);
        dueToday(other);
        Map<String, Object> txn = sale("/sales/bills", row(c, "P" + System.nanoTime(), "EXCHANGE", 2, 2, 700, null, null));

        Map<String, Object> list = due(today);
        assertThat(counts(list)).containsEntry("due", 2).containsEntry("listed", 1).containsEntry("entered", 1);
        Map<String, Object> x = item(list, c);
        assertThat(x).containsEntry("entered", true).containsEntry("listed", false);
        assertThat(x.get("enteredBills").toString()).contains((String) txn.get("billNo"));
        assertThat(item(list, other)).containsEntry("entered", false).containsEntry("listed", true);

        // A reversed bill does not count as entered: the customer is listed again.
        mvc.perform(json(post("/sales/txns/" + txnId(txn) + "/reverse"), map("reason", "Wrong customer"))).andExpect(status().isCreated());
        assertThat(item(due(today), c)).containsEntry("entered", false).containsEntry("listed", true);
    }

    @Test
    void dueListIsForAdminAndDeliveryRolesOnly() throws Exception {
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        Cookie acc = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/delivery/due").cookie(staff)).andExpect(status().isOk());
        mvc.perform(get("/delivery/due").cookie(acc)).andExpect(status().isForbidden());
        mvc.perform(get("/sales/paper-bill/next").cookie(staff)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ S2 – grid support

    @Test
    void futureDateIsRejectedAndThePlanCanLookAhead() throws Exception {
        long c = holding("Future", 2);
        send("/sales/bills", map("customerId", c, "date", today.plusDays(1).toString(), "paperBillNo", "P" + System.nanoTime(),
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.date").value("Date cannot be after today (" + BusinessDates.format(today) + ")."));
        // The due list itself may look ahead (delivery planning).
        assertThat(due(today.plusWeeks(1)).get("date")).isEqualTo(today.plusWeeks(1).toString());
    }

    // ------------------------------------------------------------------ S4 – a day of six grid rows

    @Test
    @SuppressWarnings("unchecked")
    void sixGridRowsPostedOneByOne() throws Exception {
        List<Long> cs = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            long c = holding("Row " + i, 2);
            dueToday(c);
            cs.add(c);
        }
        assertThat(counts(due(today))).containsEntry("listed", 6);
        long first = Long.parseLong(nextPaper());
        int filledBefore = stock("FILLED");

        // Row 1, 2: exchanges.
        Map<String, Object> r1 = read(send("/sales/bills", row(cs.get(0), String.valueOf(first), "EXCHANGE", 2, 2, 700, null, null))
                .andExpect(status().isCreated()).andReturn());
        Map<String, Object> t1 = (Map<String, Object>) r1.get("txn");
        assertThat(((Number) t1.get("amount")).doubleValue()).isEqualTo(700.0);
        assertThat(((Number) t1.get("paid")).doubleValue()).isEqualTo(700.0);
        assertThat((Map<String, Object>) r1.get("schedule")).containsEntry("before", today.toString())
                .containsEntry("after", today.plusWeeks(1).toString());
        assertThat(nextPaper()).isEqualTo(String.valueOf(first + 1));
        Map<String, Object> t2 = sale("/sales/bills", row(cs.get(1), String.valueOf(first + 1), "EXCHANGE", 2, 2, 700, null, null));

        // Row 3: shortage – rejected without a note, saved with one; one bottle owed.
        send("/sales/bills", row(cs.get(2), String.valueOf(first + 2), "EXCHANGE", 2, 1, 700, null, null))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));
        Map<String, Object> t3 = sale("/sales/bills", row(cs.get(2), String.valueOf(first + 2), "EXCHANGE", 2, 1, 700, "1 bottle at the neighbour",
                null));
        assertThat(bottles(cs.get(2))).containsExactly(2, 0, 1);

        // Row 4: left at door – needs phone ✓ and a note; empties to collect.
        send("/sales/bills", row(cs.get(3), String.valueOf(first + 3), "LEFT_AT_DOOR", 2, 0, 0, null, null))
                .andExpect(status().isBadRequest());
        Map<String, Object> t4 = sale("/sales/bills", row(cs.get(3), String.valueOf(first + 3), "LEFT_AT_DOOR", 2, 0, 0, "Wife agreed, at the gate",
                true));
        assertThat(bottles(cs.get(3))).containsExactly(2, 2, 0);

        // Row 5: the paper bill number of row 1 again – rejected; then its own number.
        send("/sales/bills", row(cs.get(4), String.valueOf(first), "EXCHANGE", 2, 2, 700, null, null))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("DUPLICATE_PAPER_BILL"))
                .andExpect(jsonPath("$.details.field").value("paperBillNo"));
        Map<String, Object> t5 = sale("/sales/bills", row(cs.get(4), String.valueOf(first + 4), "EXCHANGE", 2, 2, 700, null, null));
        Map<String, Object> t6 = sale("/sales/bills", row(cs.get(5), String.valueOf(first + 5), "EXCHANGE", 2, 2, 0, null, null));

        List<Object> billNos = List.of(t1.get("billNo"), t2.get("billNo"), t3.get("billNo"), t4.get("billNo"), t5.get("billNo"),
                t6.get("billNo"));
        assertThat(billNos).doesNotHaveDuplicates().allSatisfy(b -> assertThat(b.toString()).matches("B\\d{6}"));
        assertThat(stock("FILLED")).isEqualTo(filledBefore - 12);
        assertThat(nextPaper()).isEqualTo(String.valueOf(first + 6));

        // All six are now entered for today, and their next delivery is a week on.
        Map<String, Object> list = due(today);
        assertThat(counts(list)).containsEntry("entered", 6).containsEntry("listed", 0);
        for (long c : cs) {
            assertThat(item(list, c).get("nextDeliveryDate")).isEqualTo(today.plusWeeks(1).toString());
        }
        // Row 6 paid nothing: the cash customer owes Rs. 700.
        assertThat(balance(cs.get(5)).doubleValue()).isEqualTo(700.0);
        // Next trip for row 4: the 2 extra empties clear its empties to collect.
        sale("/sales/bills", row(cs.get(3), String.valueOf(first + 6), "EXCHANGE", 2, 4, 700, null, null));
        assertThat(bottles(cs.get(3))).containsExactly(2, 0, 0);
    }

    // ------------------------------------------------------------------ S3 – performance

    @Test
    void dueListOf500CustomersAndPostingABillAreFast() throws Exception {
        String suffix = String.valueOf(System.nanoTime() % 1000);
        jdbc.update("insert into customer (code, name, address, area_id, phone, customer_type_id, delivery_day, cycle_weeks, "
                + "next_delivery_date, pay_type, registered_on, created_by) "
                + "select 'E' || lpad(g::text, 6, '0') || lpad(?, 3, '0'), 'Due customer ' || g, g || ', Long Road', ?, "
                + "'077 ' || lpad(g::text, 7, '0'), ?, ?, 1 + g % 4, ?, 'CASH', ?, 'test' from generate_series(1, 500) g",
                suffix, areaId, typeId, today.getDayOfWeek().getValue(), today, today.minusWeeks(8));
        jdbc.update("insert into customer_usual_qty (customer_id, bottle_type_code, qty) select id, ?, 2 from customer "
                + "where area_id = ? and name like 'Due customer %'", bottle, areaId);
        jdbc.update("insert into customer_account (customer_id) select id from customer where area_id = ? and name like 'Due customer %' "
                + "on conflict do nothing", areaId);
        jdbc.update("insert into customer_bottle_balance (customer_id, bottle_type_code) select c.id, b.code from customer c "
                + "cross join bottle_type b where c.area_id = ? and c.name like 'Due customer %' on conflict do nothing", areaId);
        // Bottles with the customers: kept in step with the store's "with customers" count (invariant).
        jdbc.update("update customer_bottle_balance set held = 2 where bottle_type_code = ? and customer_id in "
                + "(select id from customer where area_id = ? and name like 'Due customer %')", bottle, areaId);
        jdbc.update("update stock_balance set qty = qty + 1000 where bottle_type_code = ? and bucket = 'CUSTOMERS'", bottle);
        jdbc.update("analyze customer");

        due(today);
        long start = System.nanoTime();
        Map<String, Object> list = due(today);
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(ms).as("due list of 500 took " + ms + " ms").isLessThan(500);
        assertThat(counts(list)).containsEntry("due", 500).containsEntry("listed", 500);
        assertThat(bottleOf(items(list).getFirst())).containsEntry("filled", 2).containsEntry("empties", 2);

        long c = ((Number) items(list).get(0).get("id")).longValue();
        long c2 = ((Number) items(list).get(1).get("id")).longValue();
        sale("/sales/bills", row(c, "P" + System.nanoTime(), "EXCHANGE", 2, 2, 700, null, null));
        start = System.nanoTime();
        sale("/sales/bills", row(c2, "P" + System.nanoTime(), "EXCHANGE", 2, 2, 700, null, null));
        ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(ms).as("posting a bill took " + ms + " ms").isLessThan(300);
    }
}
