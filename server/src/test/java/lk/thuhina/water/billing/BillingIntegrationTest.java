package lk.thuhina.water.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.servlet.http.Cookie;
import lk.thuhina.water.billing.rules.AgingRules.Aging;
import lk.thuhina.water.billing.service.AgingService;
import lk.thuhina.water.billing.service.InvoiceGenerated;
import lk.thuhina.water.sales.SalesTestSupport;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M11 Billing & payments – SERVER (S2 – S6, S8): aging of a hand-calculated customer (FIFO, a 90+ amount, a reversal credit,
 * an advance) in the list (SQL) and for one customer (rules), part payment by cheque and the reference rules, receipts,
 * customer history, recent money, statements, monthly invoices (one per customer and month, skips, lines, PDF = statement
 * closing) and the roles. Bills of the test bottle type cost Rs. 700 for 2 filled (water Rs. 350).
 */
@RecordApplicationEvents
class BillingIntegrationTest extends SalesTestSupport {

    @Autowired
    private AgingService aging;
    @Autowired
    private ApplicationEvents events;

    /** A Monthly bill customer (limit Rs. 100,000, 30 days) registered 200 days ago holding 4 bottles (fully paid). */
    private long monthly(String name) throws Exception {
        Map<String, Object> req = customer(name + " " + System.nanoTime());
        req.put("payType", "MONTHLY_BILL");
        req.put("creditLimit", 100000);
        req.put("termsDays", 30);
        req.put("registeredOn", today.minusDays(200).toString());
        req.put("bottlesGiven", Map.of(bottle, 4));
        req.put("paid", 4 * 1350);
        return register(req);
    }

    /** An exchange of 2 filled for 2 empties (Rs. 700) on a date, with cash paid on the bill. */
    private Map<String, Object> bill(long customerId, LocalDate date, int paid) throws Exception {
        return sale("/sales/bills", map("customerId", customerId, "date", date.toString(), "paperBillNo", "P" + System.nanoTime() % 10_000_000_000L,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 2)), "paid", paid));
    }

    private Map<String, Object> pay(Cookie who, long customerId, LocalDate date, int amount, String method, String reference) throws Exception {
        MvcResult r = mvc.perform(json(post("/payments"), who, map("customerId", customerId, "date", date.toString(), "amount", amount,
                "method", method, "reference", reference))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return read(r);
    }

    private Map<String, Object> getJson(String url) throws Exception {
        MvcResult r = mvc.perform(get(url).cookie(admin)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return read(r);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listJson(String url) throws Exception {
        MvcResult r = mvc.perform(get(url).cookie(admin)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return json.readValue(r.getResponse().getContentAsString(), List.class);
    }

    private String pdfText(String url) throws Exception {
        MvcResult r = mvc.perform(get(url).cookie(admin)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(r.getResponse().getContentType()).isEqualTo("application/pdf");
        PdfReader reader = new PdfReader(r.getResponse().getContentAsByteArray());
        PdfTextExtractor text = new PdfTextExtractor(reader);
        StringBuilder all = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            all.append(text.getTextFromPage(i)).append('\n');
        }
        return all.toString();
    }

    private String bottleName() {
        return jdbc.queryForObject("select name from bottle_type where code = ?", String.class, bottle);
    }

    private static BigDecimal money(Object v) {
        return new BigDecimal(String.valueOf(v));
    }

    private static void assertBuckets(Map<String, Object> row, String b0, String b30, String b60, String b90, String balance) {
        assertThat(money(row.get("days0to30"))).as("0–30").isEqualByComparingTo(b0);
        assertThat(money(row.get("days31to60"))).as("31–60").isEqualByComparingTo(b30);
        assertThat(money(row.get("days61to90"))).as("61–90").isEqualByComparingTo(b60);
        assertThat(money(row.get("over90"))).as("90+").isEqualByComparingTo(b90);
        assertThat(money(row.get("balance"))).as("balance").isEqualByComparingTo(balance);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> listed(String name) throws Exception {
        List<Map<String, Object>> items = (List<Map<String, Object>>) getJson("/accounts?q=" + name).get("items");
        assertThat(items).hasSize(1);
        return items.getFirst();
    }

    private String name(long customerId) throws Exception {
        return (String) getJson("/customers/" + customerId).get("name");
    }

    // ------------------------------------------------------------------ aging (S3)

    @Test
    @SuppressWarnings("unchecked")
    void agingMatchesTheHandCalculatedReferenceCustomer() throws Exception {
        long c = monthly("Aging Ref");
        String key = name(c).substring(name(c).lastIndexOf(' ') + 1); // the unique number in the name
        bill(c, today.minusDays(120), 0);
        bill(c, today.minusDays(75), 0);
        bill(c, today.minusDays(45), 0);
        Map<String, Object> last = bill(c, today.minusDays(10), 0);
        pay(admin, c, today.minusDays(5), 500, "CASH", null);

        // Rs. 500 settles the oldest bill first: Rs. 200 of it is still over 90 days.
        assertBuckets(listed(key), "700", "700", "700", "200", "2300");
        Map<String, Object> one = (Map<String, Object>) getJson("/accounts/" + c).get("account");
        assertBuckets(one, "700", "700", "700", "200", "2300");
        assertThat(one.get("oldest")).isEqualTo(today.minusDays(120).toString());
        assertThat(balance(c)).isEqualByComparingTo("2300");

        // Filters and totals: older than 30 days; the totals row covers the matching rows only.
        Map<String, Object> old = getJson("/accounts?show=old&q=" + key);
        assertThat((List<?>) old.get("items")).hasSize(1);
        assertThat(money(((Map<String, Object>) old.get("totals")).get("over90"))).isEqualByComparingTo("200");
        assertThat((List<?>) getJson("/accounts?show=over&q=" + key).get("items")).isEmpty();
        assertThat((List<?>) getJson("/accounts?pay=CASH&q=" + key).get("items")).isEmpty();
        Map<String, Object> kpi = getJson("/accounts/summary");
        assertThat(money(kpi.get("over90"))).isGreaterThanOrEqualTo(new BigDecimal("200"));

        // Reversing the newest bill is a credit: it settles the oldest amounts, not the reversed bill.
        sale("/sales/txns/" + txnId(last) + "/reverse", map("reason", "Entered twice"));
        assertBuckets(listed(key), "700", "700", "200", "0", "1600");
        Aging rules = aging.aging(c, today);
        assertThat(rules.total()).isEqualByComparingTo("1600");
        assertThat(rules.days61to90()).isEqualByComparingTo("200");

        // Paying more than owed: an advance – negative balance, every bucket empty.
        Map<String, Object> saved = pay(admin, c, today, 2000, "BANK_TRANSFER", "TRF-1");
        assertThat(saved.get("advance")).isEqualTo(true);
        assertThat((String) saved.get("message")).endsWith("Balance now Rs. 400.00 in advance (credit balance).");
        assertBuckets(listed(key), "0", "0", "0", "0", "-400");
    }

    // ------------------------------------------------------------------ payments (S2, S4, S6)

    @Test
    @SuppressWarnings("unchecked")
    void partPaymentByChequeNeedsTheChequeNumberAndPrintsAReceipt() throws Exception {
        long c = monthly("Cheque Payer");
        bill(c, today.minusDays(3), 0);
        bill(c, today.minusDays(2), 0);

        Map<String, Object> base = map("customerId", c, "date", today.toString(), "amount", 1000, "method", "CHEQUE");
        send("/payments", base).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.reference").value("Enter the cheque number."));
        base.put("method", "BANK_TRANSFER");
        send("/payments", base).andExpect(jsonPath("$.details.fields.reference").value("Enter the bank reference."));
        base.put("method", null);
        send("/payments", base).andExpect(jsonPath("$.details.fields.method").value("Select how the money was received."));
        base.put("method", "CASH");
        base.put("amount", 0);
        send("/payments", base).andExpect(jsonPath("$.details.fields.amount").value("Enter the amount received."));
        base.put("amount", 1000);
        base.put("date", today.plusDays(1).toString());
        send("/payments", base).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fields.date").exists());
        send("/payments", map("date", today.toString(), "amount", 10, "method", "CASH"))
                .andExpect(jsonPath("$.details.fields.customerId").value("Select a customer."));
        assertThat(balance(c)).isEqualByComparingTo("1400");

        Map<String, Object> body = map("customerId", c, "date", today.toString(), "amount", 1000, "method", "CHEQUE",
                "reference", "Cheque 330981", "bank", "HNB", "chequeDate", today.plusDays(7).toString(), "note", "Part payment – July");
        Map<String, Object> saved = read(send("/payments", body).andExpect(status().isCreated()).andReturn());
        Map<String, Object> p = (Map<String, Object>) saved.get("payment");
        assertThat((String) p.get("receiptNo")).matches("RC-\\d{5}");
        assertThat(money(p.get("balanceBefore"))).isEqualByComparingTo("1400");
        assertThat(money(p.get("balanceAfter"))).isEqualByComparingTo("400");
        assertThat(saved.get("partPayment")).isEqualTo(true);
        assertThat(saved.get("message")).isEqualTo("Payment saved – Rs. 1,000.00 received from " + getJson("/customers/" + c).get("name")
                + ". Balance now Rs. 400.00.");
        assertThat(balance(c)).isEqualByComparingTo("400");
        String audit = jdbc.queryForObject("select details from audit_log where entity = 'Payment' and ref = ?", String.class, p.get("receiptNo"));
        assertThat(audit).contains("Rs. 1,000.00 by Cheque (Cheque 330981) – part payment");

        // Customer history: the payment with the running balance after it; payments list for the panel.
        List<Map<String, Object>> history = listJson("/customers/" + c + "/history");
        Map<String, Object> h = history.stream().filter(x -> "PAYMENT".equals(x.get("kind"))).findFirst().orElseThrow();
        assertThat(h.get("billNo")).isEqualTo(p.get("receiptNo"));
        assertThat(money(h.get("balance"))).isEqualByComparingTo("400");
        Map<String, Object> mine = getJson("/payments?customer=" + c);
        assertThat(((Number) mine.get("total")).intValue()).isEqualTo(1);

        // Receipt PDF.
        String receipt = pdfText("/print/receipt/" + p.get("id"));
        assertThat(receipt).contains("RECEIPT", (String) p.get("receiptNo"), "Rs. 1,000.00", "Cheque – Cheque 330981", "Balance before",
                "Rs. 1,400.00", "Balance now", "Rs. 400.00", "Part payment.");

        // Recent money: the payment and cash paid on a bill.
        Map<String, Object> cashBill = bill(c, today, 700);
        List<Map<String, Object>> recent = listJson("/payments/recent");
        assertThat(recent).hasSizeLessThanOrEqualTo(15);
        assertThat(recent).anySatisfy(r -> assertThat(r.get("how")).isEqualTo("Cash on delivery – bill " + cashBill.get("billNo")));
        assertThat(recent).anySatisfy(r -> assertThat(r.get("how")).isEqualTo("Cheque – Cheque 330981"));
    }

    // ------------------------------------------------------------------ invoices and statements (S5, S6)

    @Test
    @SuppressWarnings("unchecked")
    void monthlyInvoicesOnePerCustomerTotalsEqualTheStatementAndRerunSkips() throws Exception {
        YearMonth month = YearMonth.from(today).minusMonths(1);
        String period = month.toString();
        long c = monthly("Invoice Co");
        long idle = monthly("Idle Co");
        bill(c, month.minusMonths(1).atDay(10), 0);           // previous balance Rs. 700
        bill(c, month.atDay(5), 0);
        bill(c, month.atDay(20), 300);                        // Rs. 400 this month after Rs. 300 paid on delivery
        pay(admin, c, month.atDay(25), 500, "CASH", null);    // payment in the month
        bill(c, today, 0);                                    // after the month: not on the invoice

        Map<String, Object> preview = getJson("/invoices/preview?period=" + period);
        assertThat((List<?>) preview.get("periods")).hasSize(4);
        Map<String, Object> row = ((List<Map<String, Object>>) preview.get("rows")).stream()
                .filter(r -> ((Number) r.get("customerId")).longValue() == c).findFirst().orElseThrow();
        assertThat(row.get("deliveries")).isEqualTo(2);
        assertThat(money(row.get("thisMonth"))).isEqualByComparingTo("1100");
        assertThat(money(row.get("opening"))).isEqualByComparingTo("700");
        assertThat(money(row.get("payments"))).isEqualByComparingTo("500");
        assertThat(money(row.get("totalDue"))).isEqualByComparingTo("1300");
        assertThat(row.get("invoice")).isNull();

        mvc.perform(json(post("/invoices/generate"), map("period", YearMonth.from(today).plusMonths(1).toString())))
                .andExpect(status().isBadRequest());
        Map<String, Object> result = read(mvc.perform(json(post("/invoices/generate"), map("period", period))).andExpect(status().isOk()).andReturn());
        Map<String, Object> inv = ((List<Map<String, Object>>) result.get("created")).stream()
                .filter(r -> ((Number) ((Map<String, Object>) r.get("customer")).get("id")).longValue() == c).findFirst().orElseThrow();
        assertThat((String) inv.get("invoiceNo")).matches("INV-" + period.substring(2, 4) + period.substring(5) + "-\\d{4}");
        assertThat(inv.get("invoiceDate")).isEqualTo(today.toString());
        assertThat(inv.get("dueDate")).isEqualTo(today.plusDays(30).toString());
        assertThat(money(inv.get("totalDue"))).isEqualByComparingTo("1300");
        assertThat((List<Map<String, Object>>) result.get("skipped")).anySatisfy(s -> {
            assertThat(((Number) s.get("customerId")).longValue()).isEqualTo(idle);
            assertThat(s.get("reason")).isEqualTo("No deliveries and nothing due");
        });
        long invoiceId = ((Number) inv.get("id")).longValue();
        assertThat(events.stream(InvoiceGenerated.class).filter(e -> e.invoiceId() == invoiceId)).hasSize(1);

        // Running again: already invoiced customers are skipped; still one invoice per customer and month.
        Map<String, Object> again = read(mvc.perform(json(post("/invoices/generate"), map("period", period))).andReturn());
        assertThat((List<Map<String, Object>>) again.get("created")).noneSatisfy(r ->
                assertThat(((Number) ((Map<String, Object>) r.get("customer")).get("id")).longValue()).isEqualTo(c));
        assertThat((List<Map<String, Object>>) again.get("skipped")).anySatisfy(s -> {
            assertThat(((Number) s.get("customerId")).longValue()).isEqualTo(c);
            assertThat(s.get("reason")).isEqualTo("Invoice already exists");
        });
        assertThat(jdbc.queryForObject("select count(*) from monthly_invoice where customer_id = ? and period = ?", Integer.class, c, period))
                .isEqualTo(1);

        // The invoice lists every delivery of the month; total due = the statement's closing balance for the month.
        Map<String, Object> view = getJson("/invoices/" + invoiceId);
        List<Map<String, Object>> lines = (List<Map<String, Object>>) view.get("lines");
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).get("description")).isEqualTo("Water " + bottleName());
        assertThat(lines.get(2).get("description")).isEqualTo("Paid on delivery");
        assertThat(money(lines.get(2).get("amount"))).isEqualByComparingTo("-300");
        BigDecimal sum = lines.stream().map(l -> money(l.get("amount"))).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sum).isEqualByComparingTo(money(view.get("amount")));
        Map<String, Object> st = getJson("/statements/" + c + "?from=" + month.atDay(1) + "&to=" + month.atEndOfMonth());
        assertThat(money(st.get("broughtForward"))).isEqualByComparingTo("700");
        assertThat(money(st.get("closing"))).isEqualByComparingTo(money(view.get("totalDue")));
        assertThat(listJson("/invoices?customer=" + c)).hasSize(1);

        // Statement to today closes on the account balance.
        Map<String, Object> now = getJson("/statements/" + c + "?from=" + month.minusMonths(1).atDay(1));
        assertThat(money(now.get("closing"))).isEqualByComparingTo(balance(c));

        // Paying against the invoice; another customer's invoice is refused.
        Map<String, Object> linked = map("customerId", c, "date", today.toString(), "amount", 1300, "method", "CASH", "invoiceId", invoiceId);
        Map<String, Object> paid = (Map<String, Object>) read(send("/payments", linked).andExpect(status().isCreated()).andReturn()).get("payment");
        assertThat(paid.get("invoiceNo")).isEqualTo(inv.get("invoiceNo"));
        linked.put("customerId", idle);
        send("/payments", linked).andExpect(status().isBadRequest()).andExpect(jsonPath("$.details.fields.invoiceId").exists());

        // PDFs.
        String pdf = pdfText("/print/invoice/" + invoiceId);
        assertThat(pdf).contains("INVOICE", (String) inv.get("invoiceNo"), "Water " + bottleName(), "Paid on delivery", "Previous balance",
                "Rs. 700.00", "Total due by", "Rs. 1,300.00", "VAT not applied (to be confirmed)");
        String statement = pdfText("/print/statement/" + c + "?from=" + month.atDay(1) + "&to=" + month.atEndOfMonth());
        assertThat(statement).contains("STATEMENT", "Brought forward", "Balance due", "Rs. 1,300.00", "Age of balance",
                "Bottle deposits are not refundable.");
    }

    // ------------------------------------------------------------------ roles

    @Test
    void accountantCanDoEverythingAndDeliveryStaffCannotOpenIt() throws Exception {
        long c = monthly("Roles Co");
        bill(c, today, 0);
        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));

        Map<String, Object> saved = pay(accountant, c, today, 300, "CASH", null);
        @SuppressWarnings("unchecked")
        Object id = ((Map<String, Object>) saved.get("payment")).get("id");
        mvc.perform(get("/accounts").cookie(accountant)).andExpect(status().isOk());
        mvc.perform(get("/invoices/preview").cookie(accountant)).andExpect(status().isOk());
        mvc.perform(get("/print/receipt/" + id).cookie(accountant)).andExpect(status().isOk());

        for (String url : List.of("/accounts", "/accounts/summary", "/accounts/" + c, "/payments", "/payments/recent", "/invoices/preview",
                "/invoices", "/statements/" + c, "/print/receipt/" + id, "/print/statement/" + c)) {
            mvc.perform(get(url).cookie(staff)).andExpect(status().isForbidden());
        }
        mvc.perform(json(post("/payments"), staff, map("customerId", c, "date", today.toString(), "amount", 10, "method", "CASH")))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/invoices/generate"), staff, map("period", YearMonth.from(today).minusMonths(1).toString())))
                .andExpect(status().isForbidden());
    }
}
