package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.printing.DeliveryPrintService;
import lk.thuhina.water.printing.PrintDocument;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M08 Delivery schedule – SERVER (S1 – S5): the Daily Delivery List totals, Delivery Planning (last / next / week after,
 * KPIs, ready check with "needed before"), the upcoming bottle requirement, the delivery and load sheet PDFs and the role
 * rules. Each test has its own area and bottle type (SalesTestSupport), so the bottle counts are the test's own.
 */
class DeliveryScheduleIntegrationTest extends SalesTestSupport {

    @Autowired
    private DeliveryPrintService prints;

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> getJson(String url, Cookie cookie) throws Exception {
        MvcResult r = mvc.perform(get(url).cookie(cookie)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return read(r);
    }

    private Map<String, Object> planning(LocalDate date, String show) throws Exception {
        return getJson("/delivery/planning?area=" + areaId + "&date=" + date + "&show=" + show, admin);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> obj(Object o) {
        return (Map<String, Object>) o;
    }

    private static List<Map<String, Object>> rows(Map<String, Object> plan) {
        return list(plan.get("routes")).stream().flatMap(r -> list(r.get("rows")).stream()).toList();
    }

    private static Map<String, Object> rowOf(Map<String, Object> plan, long customerId) {
        return rows(plan).stream().filter(x -> ((Number) x.get("id")).longValue() == customerId).findFirst().orElseThrow();
    }

    /** The quantity of the test bottle type in a list of {bottleTypeCode, label, qty}. */
    private int qtyOf(Object bottleQtys) {
        return list(bottleQtys).stream().filter(b -> bottle.equals(b.get("bottleTypeCode"))).mapToInt(b -> ((Number) b.get("qty")).intValue())
                .sum();
    }

    private Map<String, Object> readyOf(Map<String, Object> plan) {
        return list(plan.get("ready")).stream().filter(b -> bottle.equals(b.get("bottleTypeCode"))).findFirst().orElse(null);
    }

    /** A customer registered today without bottles, delivered on {@code day} every {@code cycle} weeks from {@code next}. */
    private long scheduled(String name, DayOfWeek day, int cycle, LocalDate next) throws Exception {
        Map<String, Object> req = customer(name);
        req.put("deliveryDay", day.getValue());
        req.put("cycleWeeks", cycle);
        req.put("nextDeliveryDate", next.toString());
        return register(req);
    }

    /** A cash customer holding {@code held} bottles (first purchase four weeks ago), delivered weekly on {@code next}'s weekday. */
    private long holding(String name, int held, LocalDate next) throws Exception {
        LocalDate since = today.minusWeeks(4);
        Map<String, Object> req = customer(name + " " + System.nanoTime());
        req.put("registeredOn", since.toString());
        req.put("nextDeliveryDate", ScheduleRules.firstDelivery(since, DayOfWeek.MONDAY).toString());
        req.put("bottlesGiven", Map.of(bottle, held));
        req.put("paid", held * 1350);
        long c = register(req);
        jdbc.update("update customer set delivery_day = ?, next_delivery_date = ? where id = ?", next.getDayOfWeek().getValue(), next, c);
        return c;
    }

    private void usual(long customerId, int qty) {
        jdbc.update("update customer_usual_qty set qty = ? where customer_id = ? and bottle_type_code = ?", qty, customerId, bottle);
    }

    private Map<String, Object> exchange(long customerId, int filled, int empties) throws Exception {
        return sale("/sales/bills", map("customerId", customerId, "date", today.toString(), "paperBillNo", "D" + System.nanoTime(),
                "kind", "EXCHANGE", "lines", List.of(map("bottleTypeCode", bottle, "filled", filled, "empties", empties)), "paid", 0));
    }

    private String pdfText(String url, Cookie cookie) throws Exception {
        MvcResult pdf = mvc.perform(get(url).cookie(cookie)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf")).andReturn();
        PdfReader reader = new PdfReader(pdf.getResponse().getContentAsByteArray());
        StringBuilder text = new StringBuilder();
        for (int p = 1; p <= reader.getNumberOfPages(); p++) {
            text.append(new PdfTextExtractor(reader).getTextFromPage(p)).append(' ');
        }
        return text.toString().replaceAll("\\s+", " ");
    }

    private String areaName() {
        return jdbc.queryForObject("select name from area where id = ?", String.class, areaId);
    }

    // ------------------------------------------------------------------ S1 – daily list

    @Test
    void dailyListHasAddressNotesAndDayTotals() throws Exception {
        long a = holding("Daily A", 3, today);
        long b = holding("Daily B", 2, today);
        usual(b, 3);
        jdbc.update("update customer set notes = 'Ring twice' where id = ?", a);
        // B: left at door last trip (2 to collect) and Rs. 700 unpaid.
        sale("/sales/bills", map("customerId", b, "date", today.minusDays(1).toString(), "paperBillNo", "D" + System.nanoTime(),
                "kind", "LEFT_AT_DOOR", "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 0)), "paid", 0,
                "note", "Neighbour took it", "phoneConfirmed", true));
        jdbc.update("update customer set next_delivery_date = ? where id = ?", today, b);

        Map<String, Object> due = getJson("/delivery/due?area=" + areaId, admin);
        Map<String, Object> x = list(due.get("items")).stream().filter(i -> ((Number) i.get("id")).longValue() == a).findFirst().orElseThrow();
        assertThat(x).containsEntry("address", "8, Sales Road").containsEntry("notes", "Ring twice")
                .containsEntry("deliveryDay", today.getDayOfWeek().getValue());
        Map<String, Object> totals = obj(due.get("totals"));
        assertThat(qtyOf(totals.get("load"))).isEqualTo(5);
        assertThat(totals).containsEntry("toCollect", 2).containsEntry("owed", 0);
        assertThat(((Number) totals.get("moneyToCollect")).doubleValue()).isEqualTo(700.0);
    }

    // ------------------------------------------------------------------ S2 – planning

    @Test
    void planningShowsLastNextAndWeekAfterForEachCycle() throws Exception {
        LocalDate d = today.plusDays(1);
        DayOfWeek dow = d.getDayOfWeek();
        String tag = " " + System.nanoTime();
        long w = scheduled("A weekly" + tag, dow, 1, d);
        long t = scheduled("B two-weekly" + tag, dow, 2, d);
        long f = scheduled("C four-weekly" + tag, dow, 4, d.plusWeeks(1));
        long h = holding("D holding", 3, d);
        // Left at the door today: the last delivery before tomorrow, with 2 empties to collect.
        sale("/sales/bills", map("customerId", h, "date", today.toString(), "paperBillNo", "D" + System.nanoTime(), "kind", "LEFT_AT_DOOR",
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 0)), "paid", 0, "note", "Left with the guard",
                "phoneConfirmed", true));

        Map<String, Object> all = planning(d, "all");
        assertThat(all).containsEntry("date", d.toString()).containsEntry("show", "all").containsEntry("today", false);
        assertThat(rowOf(all, w)).containsEntry("due", true).containsEntry("status", "FIRST_DELIVERY")
                .containsEntry("next", d.plusWeeks(1).toString()).containsEntry("weekAfter", d.plusWeeks(2).toString());
        assertThat(qtyOf(rowOf(all, w).get("thisDate"))).isEqualTo(2);
        assertThat(rowOf(all, t)).containsEntry("next", d.plusWeeks(2).toString()).containsEntry("weekAfter", d.plusWeeks(4).toString());
        assertThat(rowOf(all, f)).containsEntry("due", false).containsEntry("status", "NOT_DUE").containsEntry("statusLabel", "Not due")
                .containsEntry("next", d.plusWeeks(1).toString()).containsEntry("weekAfter", d.plusWeeks(5).toString());
        assertThat(list(rowOf(all, f).get("thisDate"))).isEmpty();
        Map<String, Object> hr = rowOf(all, h);
        assertThat(hr).containsEntry("status", "DUE").containsEntry("toCollect", 2);
        assertThat(obj(hr.get("last"))).containsEntry("date", today.toString()).containsEntry("leftAtDoor", true);
        assertThat(qtyOf(obj(hr.get("last")).get("filled"))).isEqualTo(2);
        assertThat(rowOf(all, w).get("last")).isNull();

        // "Due" shows only the customers due on the date; the route header counts them and what to load.
        Map<String, Object> due = planning(d, "due");
        assertThat(rows(due)).extracting(r -> ((Number) r.get("id")).longValue()).containsExactlyInAnyOrder(w, t, h);
        Map<String, Object> route = list(due.get("routes")).getFirst();
        assertThat(route).containsEntry("areaName", areaName()).containsEntry("due", 3).containsEntry("toLoad", 3).containsEntry("toCollect", 2);
        assertThat(qtyOf(route.get("load"))).isEqualTo(6);
        Map<String, Object> kpis = obj(due.get("kpis"));
        assertThat(kpis).containsEntry("due", 3).containsEntry("entered", 0).containsEntry("missed", 0).containsEntry("toCollect", 2);
        assertThat(qtyOf(kpis.get("needed"))).isEqualTo(6);

        // A week on: the two-weekly customer is not due; its next is still the week after.
        Map<String, Object> week = planning(d.plusWeeks(1), "all");
        assertThat(rowOf(week, t)).containsEntry("due", false).containsEntry("next", d.plusWeeks(2).toString());
        assertThat(rowOf(week, f)).containsEntry("due", true).containsEntry("next", d.plusWeeks(5).toString());
    }

    @Test
    void readyCheckCountsTheBottlesNeededBeforeTheDate() throws Exception {
        LocalDate d = today.plusDays(2);
        long x = holding("Today", 2, today);           // usual 2, due today
        long y = holding("Tomorrow", 2, today.plusDays(1));
        usual(y, 2);
        long z = holding("Planned", 2, d);
        usual(z, 3);
        count("FILLED", 5);
        count("FACTORY", 2);

        Map<String, Object> ready = readyOf(planning(d, "due"));
        // 3 needed on the date; 5 filled now, but 4 are needed today and tomorrow → short by 2; the factory's 2 would cover it.
        assertThat(ready).containsEntry("needed", 3).containsEntry("neededBefore", 4).containsEntry("filledInStore", 5)
                .containsEntry("atFactory", 2).containsEntry("shortBy", 2).containsEntry("ready", false).containsEntry("factoryCovers", true);

        // Today's delivery is entered: no longer "needed before" (it has left the store).
        exchange(x, 2, 2);
        ready = readyOf(planning(d, "due"));
        assertThat(ready).containsEntry("neededBefore", 2).containsEntry("filledInStore", 3).containsEntry("shortBy", 2);
        // Enough filled bottles in store → ready.
        count("FILLED", 10);
        assertThat(readyOf(planning(d, "due"))).containsEntry("shortBy", 0).containsEntry("ready", true).containsEntry("factoryCovers", false);

        // Entered customers are counted but not loaded.
        Map<String, Object> todayPlan = planning(today, "due");
        assertThat(rowOf(todayPlan, x)).containsEntry("status", "ENTERED").containsEntry("entered", true);
        assertThat(obj(todayPlan.get("kpis"))).containsEntry("due", 1).containsEntry("entered", 1);
        assertThat(qtyOf(obj(todayPlan.get("kpis")).get("needed"))).isZero();
        assertThat(readyOf(todayPlan)).isNull();
    }

    @Test
    void missedCustomersAreOnlyOnTodaysPlan() throws Exception {
        LocalDate missedOn = today.minusDays(3);
        long m = holding("Missed", 2, missedOn);
        jdbc.update("update customer set credit_limit = 500, terms_days = 30, pay_type = 'CREDIT' where id = ?", m);
        jdbc.update("update customer_account set balance = balance + 900 where customer_id = ?", m);

        Map<String, Object> plan = planning(today, "due");
        assertThat(rowOf(plan, m)).containsEntry("status", "MISSED").containsEntry("statusLabel", "Missed " + BusinessDates.format(missedOn))
                .containsEntry("overdue", true).containsEntry("overLimit", true)
                .containsEntry("next", missedOn.plusWeeks(1).toString());
        assertThat(obj(plan.get("kpis"))).containsEntry("missed", 1);
        assertThat(rows(planning(today.plusDays(1), "due"))).isEmpty();
        jdbc.update("update customer_account set balance = balance - 900 where customer_id = ?", m);

        mvc.perform(get("/delivery/planning?show=some").cookie(admin)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.show").value("Show must be 'due' or 'all'."));
    }

    @Test
    void planningDefaultsToTomorrow() throws Exception {
        Map<String, Object> plan = getJson("/delivery/planning?area=" + areaId, admin);
        assertThat(plan).containsEntry("date", today.plusDays(1).toString()).containsEntry("show", "due").containsEntry("customers", 0);
    }

    // ------------------------------------------------------------------ S3 – requirement

    @Test
    void requirementShowsWhenFilledBottlesRunOut() throws Exception {
        long x = holding("Weekly need", 2, today);
        count("FILLED", 3);
        count("FACTORY", 0);

        Map<String, Object> req = getJson("/delivery/requirement?days=14&bottle=" + bottle, admin);
        assertThat(req).containsEntry("bottleTypeCode", bottle).containsEntry("filledInStore", 3).containsEntry("atFactory", 0)
                .containsEntry("days", 14);
        List<Map<String, Object>> days = list(req.get("rows"));
        assertThat(days).hasSize(14);
        assertThat(days.getFirst()).containsEntry("date", today.toString()).containsEntry("customers", 1).containsEntry("needed", 2)
                .containsEntry("runningTotal", 2).containsEntry("filledLeft", 1);
        assertThat(days.get(7)).containsEntry("needed", 2).containsEntry("runningTotal", 4).containsEntry("filledLeft", -1)
                .containsEntry("withFactory", -1);
        assertThat(req.get("runsOutOn")).isEqualTo(today.plusDays(7).toString());

        // Entered today → today needs nothing more.
        exchange(x, 2, 2);
        req = getJson("/delivery/requirement?days=14&bottle=" + bottle, admin);
        assertThat(list(req.get("rows")).getFirst()).containsEntry("needed", 0).containsEntry("customers", 0);

        mvc.perform(get("/delivery/requirement?days=0").cookie(admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/delivery/requirement?bottle=NOPE").cookie(admin)).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ S4 – PDFs

    @Test
    void deliverySheetIsGroupedByAreaWithBlankColumns() throws Exception {
        long a = holding("Sheet A", 3, today);
        long n = scheduled("Sheet New " + System.nanoTime(), today.getDayOfWeek(), 1, today.plusWeeks(1));
        jdbc.update("update customer set next_delivery_date = ?, registered_on = ? where id = ?", today, today.minusDays(1), n);
        jdbc.update("update customer set notes = 'Blue gate' where id = ?", a);
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));

        String text = pdfText("/print/delivery-sheet?area=" + areaId, staff);
        assertThat(text).contains("DELIVERY SHEET", BusinessDates.format(today), "Driver: Test Delivery Staff", "2 customers", areaName(),
                "Sheet A", "Blue gate", "3 × " + label, "NEW", "Bill no.", "Filled", "Empties", "Cash / note", "Not home?", "Checked by");
        // Printed by the Admin: a blank line for the driver's name.
        assertThat(pdfText("/print/delivery-sheet?area=" + areaId + "&date=" + today, admin)).contains("Driver: ________________");
    }

    @Test
    void loadSheetHasRouteTotalsAndOnlyCustomersStillToDeliver() throws Exception {
        holding("Load A", 2, today);
        long b = holding("Load B", 2, today);
        usual(b, 4);
        long done = holding("Load Done", 2, today);
        exchange(done, 2, 2);
        String doneName = jdbc.queryForObject("select name from customer where id = ?", String.class, done);

        Map<String, Object> plan = planning(today, "due");
        Map<String, Object> route = list(plan.get("routes")).getFirst();
        assertThat(route).containsEntry("toLoad", 2);
        assertThat(qtyOf(route.get("load"))).isEqualTo(6);

        String text = pdfText("/print/load-sheet?area=" + areaId + "&date=" + today, admin);
        assertThat(text).contains("LOAD SHEET", "2 customers · load 6 × " + label, "Route (area)", label + " to load",
                "Load A", "Load B", "4 × " + label, "Loaded by", "Driver", "Checked by");
        assertThat(text).doesNotContain(doneName);

        // Route totals: customers, bottles to load per type, empties to collect.
        PrintDocument doc = prints.loadSheetDocument(today, areaId);
        PrintDocument.Table totals = doc.tables().getFirst();
        List<String> row = totals.rows().getFirst();
        assertThat(row.get(0)).isEqualTo(areaName());
        assertThat(row.get(1)).isEqualTo("2");
        assertThat(row.get(totals.headers().indexOf(label + " to load"))).isEqualTo("6");
        assertThat(row.getLast()).isEqualTo("0");
        // Customer list: area heading, then the two customers with blank columns to write in.
        PrintDocument.Table customers = doc.tables().get(1);
        assertThat(customers.rows()).hasSize(3);
        assertThat(customers.rows().getFirst()).containsExactly(areaName());
        assertThat(customers.rows().get(1).subList(8, 12)).containsOnly("");
        assertThat(doc.page()).isEqualTo(PrintDocument.Page.A4_LANDSCAPE);
    }

    // ------------------------------------------------------------------ S5 – roles

    @Test
    void deliveryStaffReadsOnlyTheDailyListAndItsSheet() throws Exception {
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        Cookie acc = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/delivery/due").cookie(staff)).andExpect(status().isOk());
        mvc.perform(get("/print/delivery-sheet").cookie(staff)).andExpect(status().isOk());
        mvc.perform(get("/delivery/planning").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/delivery/requirement").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/print/load-sheet").cookie(staff)).andExpect(status().isForbidden());
        for (String url : List.of("/delivery/due", "/delivery/planning", "/delivery/requirement", "/print/delivery-sheet", "/print/load-sheet")) {
            mvc.perform(get(url).cookie(acc)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/delivery/planning").cookie(admin)).andExpect(status().isOk());
        mvc.perform(get("/print/load-sheet").cookie(admin)).andExpect(status().isOk());
    }
}
