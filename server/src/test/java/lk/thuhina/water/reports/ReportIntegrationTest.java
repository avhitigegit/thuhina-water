package lk.thuhina.water.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.sales.SalesTestSupport;
import lk.thuhina.water.security.Role;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M12 Dashboard & reports – SERVER (S1 – S3, S5): a known scenario on today's date for the test's own customer type and bottle
 * type (first purchase, an exchange and its reversal, a customer on an agreed price) checked in the dashboard (today's sales
 * and money in, as a change), the Sales report (by customer type, agreed prices), Profit, Stock at a date, Outstanding vs the
 * account summary, the exports (PDF text, Excel cells) and the role rules. The demo-data generator's checks.
 */
class ReportIntegrationTest extends SalesTestSupport {

    @BeforeEach
    void setUp() throws Exception {
        setUpSales();
    }

    /** The test database is shared: an inactive bottle type no longer adds lines to other tests' bills and lists. */
    @AfterEach
    void deactivateBottle() {
        jdbc.update("update bottle_type set active = false where code = ?", bottle);
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> getJson(String url, Cookie cookie) throws Exception {
        MvcResult r = mvc.perform(get(url).cookie(cookie)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    private int status(String url, Cookie cookie) throws Exception {
        return mvc.perform(get(url).cookie(cookie)).andReturn().getResponse().getStatus();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    @SuppressWarnings("unchecked")
    private static List<List<String>> rows(Map<String, Object> table) {
        return (List<List<String>>) table.get("rows");
    }

    private static Map<String, Object> table(Map<String, Object> report, String title) {
        return list(report.get("tables")).stream().filter(t -> t.get("title").toString().startsWith(title)).findFirst().orElseThrow();
    }

    private static String kpi(Map<String, Object> view, String key, String field) {
        return list(view.get("kpis")).stream().filter(k -> key.equals(k.get(field))).findFirst().orElseThrow().get("value").toString();
    }

    private static BigDecimal rupees(String shown) {
        return new BigDecimal(shown.replace("Rs. ", "").replace(",", ""));
    }

    private Map<String, Object> bill(long customerId, int filled, int empties) {
        return map("customerId", customerId, "date", today.toString(), "paperBillNo", "R" + System.nanoTime() % 10_000_000_000L,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", filled, "empties", empties)), "paid", 0);
    }

    private String range() {
        return "?from=" + today + "&to=" + today;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void dashboardAndSalesProfitAndStockReportsAgreeWithAKnownScenario() throws Exception {
        Map<String, Object> before = getJson("/dashboard", admin);
        String typeName = jdbc.queryForObject("select name from customer_type where id = ?", String.class, typeId);

        // A: first purchase of 2 (water 700 + deposits 2,000, paid 2,700), an exchange of 2 (700) and its reversal.
        long a = customerHolding("Report A", 2);
        Map<String, Object> exchange = sale("/sales/bills", bill(a, 2, 2));
        sale("/sales/txns/" + txnId(exchange) + "/reverse", map("reason", "Entered twice"));
        // B: first purchase of 2, then an agreed price of 300 (standard 350) and an exchange of 2 (600, unpaid).
        long b = customerHolding("Report B", 2);
        mvc.perform(json(post("/customers/" + b + "/prices"), map("bottleTypeCode", bottle, "price", 300, "from", today.toString(),
                "reason", "Volume price"))).andReturn();
        sale("/sales/bills", bill(b, 2, 2));

        // Dashboard: today's sales and money in went up by exactly this scenario.
        Map<String, Object> after = getJson("/dashboard", admin);
        assertThat(rupees(kpi(after, "sales", "key")).subtract(rupees(kpi(before, "sales", "key"))))
                .isEqualByComparingTo("6000"); // 2,700 + 700 − 700 + 2,700 + 600
        assertThat(rupees(kpi(after, "collected", "key")).subtract(rupees(kpi(before, "collected", "key"))))
                .isEqualByComparingTo("5400");
        assertThat(list(after.get("kpis"))).extracting(k -> k.get("key"))
                .containsExactly("sales", "collected", "filled", "factory", "deliveries", "toCollect", "owed", "outstanding");
        assertThat((List<?>) after.get("sales14")).hasSize(14);

        // Sales: by customer type (reversal counts against the exchange, not as a bill); agreed price below standard.
        Map<String, Object> sales = getJson("/reports/sales" + range(), admin);
        assertThat(rows(table(sales, "By customer type"))).contains(List.of(typeName, "4", "2000.00", "4000.00", "0.00", "6000.00"));
        assertThat(rows(table(sales, "Customers on agreed prices"))).anySatisfy(r -> {
            assertThat(r.getFirst()).startsWith("Report B");
            assertThat(r.subList(2, 6)).containsExactly("2", "600.00", "700.00", "-100.00");
        });
        assertThat(rows(table(sales, "By sale type")).stream().map(List::getFirst)).contains("First purchase", "Exchange");
        // By bottle type (B-07): the test bottle type's bills, water and deposits (the reversal counts against them).
        String salesBottleName = jdbc.queryForObject("select name from bottle_type where code = ?", String.class, bottle);
        assertThat(rows(table(sales, "By bottle type"))).anySatisfy(r -> {
            assertThat(r.subList(0, 2)).containsExactly(salesBottleName, "4");
            assertThat(r.subList(3, 6)).containsExactly("2000.00", "4000.00", "6000.00");
        });

        // Profit: the test bottle type's revenue = its water and deposits.
        Map<String, Object> profit = getJson("/reports/profit" + range(), admin);
        String bottleName = jdbc.queryForObject("select name from bottle_type where code = ?", String.class, bottle);
        assertThat(rows(table(profit, "By bottle type"))).anySatisfy(r -> assertThat(r.subList(0, 2)).containsExactly(bottleName, "6000.00"));
        // Running expenses = the Expenses report total for the same months.
        Map<String, Object> exp = getJson("/reports/expenses" + range(), admin);
        assertThat(kpi(profit, "Running expenses", "label")).isEqualTo(kpi(exp, "Total expenses", "label"));

        // Stock at the end date: today = now; yesterday = before today's movements (the bottle type is new today).
        Map<String, Object> stockNow = getJson("/reports/stock" + range(), admin);
        Map<String, Object> stockYesterday = getJson("/reports/stock?from=" + today.minusDays(1) + "&to=" + today.minusDays(1), admin);
        List<String> now = rows(table(stockNow, "Bottle stock")).stream().filter(r -> r.getFirst().equals(bottleName)).findFirst().orElseThrow();
        assertThat(now.get(2)).isEqualTo(String.valueOf(stock("FILLED")));
        assertThat(now.get(4)).isEqualTo("4"); // with customers: A 2 + B 2
        assertThat(rows(table(stockYesterday, "Bottle stock"))).anySatisfy(r -> {
            assertThat(r.getFirst()).isEqualTo(bottleName);
            assertThat(r.subList(1, 7)).containsOnly("0");
        });

        // Outstanding at today = the account summary (money owed; advances in their own column).
        Map<String, Object> out = getJson("/reports/outstanding" + range(), admin);
        Map<String, Object> summary = getJson("/accounts/summary", admin);
        List<?> footer = (List<?>) table(out, "By payment type").get("footer");
        assertThat(new BigDecimal(footer.get(5).toString()))
                .isEqualByComparingTo(new BigDecimal(summary.get("totalOutstanding").toString()));
        // B-08: every row adds up – 0–30 + 31–60 + 61–90 + 90+ = Owed (an advance does not reduce it).
        for (List<String> r : rows(table(out, "By customer"))) {
            BigDecimal ages = new BigDecimal(r.get(2)).add(new BigDecimal(r.get(3))).add(new BigDecimal(r.get(4))).add(new BigDecimal(r.get(5)));
            assertThat(ages).as(r.getFirst()).isEqualByComparingTo(new BigDecimal(r.get(6)));
            assertThat(new BigDecimal(r.get(7))).as(r.getFirst()).isNotNegative();
        }

        // Exports carry the same data.
        MvcResult pdf = mvc.perform(get("/reports/sales/export" + range() + "&format=pdf").cookie(admin)).andReturn();
        assertThat(pdf.getResponse().getContentType()).isEqualTo(MediaType.APPLICATION_PDF_VALUE);
        String text = new PdfTextExtractor(new PdfReader(pdf.getResponse().getContentAsByteArray())).getTextFromPage(1);
        assertThat(text).contains("SALES REPORT", BusinessDates.format(today), "By customer type", typeName);
        MvcResult xlsx = mvc.perform(get("/reports/sales/export" + range() + "&format=xlsx").cookie(admin)).andReturn();
        assertThat(xlsx.getResponse().getHeader("Content-Disposition")).contains("attachment", "Sales-report-");
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx.getResponse().getContentAsByteArray()))) {
            assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Summary");
            Sheet byType = wb.getSheet("By customer type");
            Row found = null;
            for (Row r : byType) {
                if (r.getCell(0) != null && typeName.equals(r.getCell(0).toString())) {
                    found = r;
                }
            }
            assertThat(found).isNotNull();
            assertThat(found.getCell(5).getNumericCellValue()).isEqualTo(6000.0);
            assertThat(found.getCell(5).getCellStyle().getDataFormatString()).isEqualTo("#,##0.00");
        }
    }

    @Test
    void everyReportRunsAndExportsAndTheRangeIsChecked() throws Exception {
        List<Map<String, Object>> reports = list(json.readValue(mvc.perform(get("/reports").cookie(admin)).andReturn().getResponse()
                .getContentAsString(), List.class));
        assertThat(reports).extracting(r -> r.get("key")).containsExactly("sales", "purchasing", "production", "stock", "movement", "damage",
                "outstanding", "profit", "expenses", "delivery");
        String r = "?from=" + today.minusDays(20) + "&to=" + today;
        for (Map<String, Object> rep : reports) {
            Map<String, Object> v = getJson("/reports/" + rep.get("key") + r, admin);
            assertThat(v.get("name")).isEqualTo(rep.get("name"));
            assertThat(status("/reports/" + rep.get("key") + "/export" + r + "&format=pdf", admin)).as(rep.get("key") + " pdf").isEqualTo(200);
            assertThat(status("/reports/" + rep.get("key") + "/export" + r + "&format=xlsx", admin)).as(rep.get("key") + " xlsx").isEqualTo(200);
        }
        Map<String, Object> def = getJson("/reports/sales", admin);
        assertThat(def.get("from")).isEqualTo(today.minusMonths(1).withDayOfMonth(1).toString());
        assertThat(def.get("to")).isEqualTo(today.toString());
        assertThat(mvc.perform(get("/reports/sales?from=" + today + "&to=" + today.minusDays(1)).cookie(admin)).andReturn().getResponse()
                .getContentAsString()).contains("The From date must be on or before the To date.");
        assertThat(mvc.perform(get("/reports/sales?from=" + today.minusYears(2) + "&to=" + today).cookie(admin)).andReturn().getResponse()
                .getContentAsString()).contains("Choose a range of at most 366 days.");
        assertThat(status("/reports/unknown", admin)).isEqualTo(404);
        assertThat(status("/reports/sales/export?format=doc", admin)).isEqualTo(400);
    }

    @Test
    void accountantSeesFiveReportsAndTheDashboardWithoutLinksToPagesSheCannotOpen() throws Exception {
        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        List<Map<String, Object>> reports = list(json.readValue(mvc.perform(get("/reports").cookie(accountant)).andReturn().getResponse()
                .getContentAsString(), List.class));
        assertThat(reports).extracting(r -> r.get("key")).containsExactly("sales", "purchasing", "outstanding", "profit", "expenses");
        assertThat(status("/reports/profit", accountant)).isEqualTo(200);
        assertThat(status("/reports/production", accountant)).isEqualTo(403);
        assertThat(status("/reports/delivery/export?format=pdf", accountant)).isEqualTo(403);

        Map<String, Object> d = getJson("/dashboard", accountant);
        Map<String, Object> sales = list(d.get("kpis")).stream().filter(k -> "sales".equals(k.get("key"))).findFirst().orElseThrow();
        Map<String, Object> outstanding = list(d.get("kpis")).stream().filter(k -> "outstanding".equals(k.get("key"))).findFirst().orElseThrow();
        assertThat(sales.get("href")).isNull(); // Sales & Bills is not on the Accountant's menu
        assertThat(outstanding.get("href")).isEqualTo("/billing");
        assertThat(d.get("deliveriesHref")).isNull();

        assertThat(status("/dashboard", staff)).isEqualTo(403);
        assertThat(status("/reports", staff)).isEqualTo(403);
        assertThat(status("/reports/sales", staff)).isEqualTo(403);
    }

    @Test
    void demoDataGeneratorChecksTheRangeAndIsAdminOnly() throws Exception {
        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        Map<String, Object> st = getJson("/admin/demo-data", admin);
        assertThat(st.get("suggestedTo")).isEqualTo(today.minusDays(1).toString());
        assertThat(status("/admin/demo-data", accountant)).isEqualTo(403);
        String msg = mvc.perform(json(post("/admin/demo-data"), map("from", today.toString(), "to", today.toString()))).andReturn().getResponse()
                .getContentAsString();
        assertThat(msg).contains("Demo data is made only for days before today.");
        msg = mvc.perform(json(post("/admin/demo-data"), map("from", today.minusDays(3).toString(), "to", today.minusDays(5).toString())))
                .andReturn().getResponse().getContentAsString();
        assertThat(msg).contains("The From date must be on or before the To date.");
        msg = mvc.perform(json(post("/admin/demo-data"), map("from", today.minusDays(300).toString(), "to", today.minusDays(1).toString())))
                .andReturn().getResponse().getContentAsString();
        assertThat(msg).contains("Choose at most 120 days.");
    }
}
