package lk.thuhina.water.quotations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.customers.CustomerTestSupport;
import lk.thuhina.water.quotations.rules.CustomerQuotationRules;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * M16 Customer quotations – SERVER (S2, S3): a new organisation quoted below the current price, printed, sent, accepted and
 * registered as a customer with the quoted price as its agreed price; an existing customer with Apply prices from a chosen date;
 * not accepted with a reason; Expired; Make a copy; validations; roles. Each test has its own customer type and bottle type
 * (standard water Rs. 300, deposit Rs. 1,000 – CustomerTestSupport).
 */
class CustomerQuotationIntegrationTest extends CustomerTestSupport {

    /** The shared test database: an inactive bottle type no longer adds lines to other tests. */
    @AfterEach
    void deactivateBottle() {
        jdbc.update("update bottle_type set active = false where code = ?", bottle);
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Object> call(MockHttpServletRequestBuilder b, Cookie cookie, Object body, int status) throws Exception {
        b.cookie(cookie);
        if (body != null) {
            b.contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body(body));
        }
        MvcResult r = mvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(status);
        String text = r.getResponse().getContentAsString();
        return text.isEmpty() || text.startsWith("[") || text.startsWith("%PDF") ? Map.of() : read(r);
    }

    private Map<String, Object> call(MockHttpServletRequestBuilder b, Object body, int status) throws Exception {
        return call(b, admin, body, status);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> quotation(Map<String, Object> saved) {
        return (Map<String, Object>) saved.get("quotation");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    private static long id(Map<String, Object> m) {
        return ((Number) m.get("id")).longValue();
    }

    private static BigDecimal money(Object o) {
        return new BigDecimal(o.toString());
    }

    private static Map<String, Object> line(String kind, String bottle, String description, int qty, String unit, Object price) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("bottleTypeCode", bottle);
        m.put("description", description);
        m.put("qty", qty);
        m.put("unit", unit);
        m.put("unitPrice", price);
        return m;
    }

    /** A quotation to a new government office: water below the current price, deposits and a free-text line. */
    private Map<String, Object> newOrg(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        m.put("orgName", "Divisional Secretariat " + System.nanoTime());
        m.put("contact", "Mrs. W.A. Dilrukshi");
        m.put("designation", "Administrative Officer");
        m.put("address", "High Level Road, Maharagama");
        m.put("areaId", areaId);
        m.put("phone", "011 285 0153");
        m.put("email", "ds@example.lk");
        m.put("customerTypeId", typeId);
        m.put("quoteDate", today.toString());
        m.put("validUntil", today.plusDays(30).toString());
        m.put("subject", "Supply of drinking water – monthly");
        m.put("deliveryText", "Every Monday");
        m.put("termsDays", 30);
        m.put("lines", new ArrayList<>(List.of(line("WATER", bottle, "Water – drinking water (refill, delivered)", 40, "per month", 285),
                line("DEPOSIT", bottle, "Bottle deposit (one time)", 12, "bottles", 1000),
                line("OTHER", null, "Installation of dispensers", 1, "job", 0))));
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private String price(long customerId, String date) {
        return "/pricing/water?customerId=" + customerId + "&bottleType=" + bottle + "&date=" + date;
    }

    private String fieldError(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) ((Map<String, Object>) body.get("details")).get("fields");
        return fields.values().iterator().next().toString();
    }

    private List<Map<String, Object>> history(Map<String, Object> view) {
        return list(view.get("history"));
    }

    // ------------------------------------------------------------------ tests

    @Test
    void aNewOrganisationIsQuotedPrintedSentAcceptedAndRegisteredWithTheQuotedPriceAsAgreed() throws Exception {
        // Items: the standard price of the chosen customer type.
        MvcResult items = mvc.perform(get("/customer-quotations/items?customerTypeId=" + typeId).cookie(admin)).andReturn();
        Map<String, Object> water = readList(items).stream().filter(i -> ("WATER|" + bottle).equals(i.get("key"))).findFirst().orElseThrow();
        assertThat(money(water.get("currentPrice"))).isEqualByComparingTo("300");

        Map<String, Object> saved = call(post("/customer-quotations"), newOrg(), 201);
        Map<String, Object> q = quotation(saved);
        long id = id(q);
        String no = (String) q.get("cqNo");
        assertThat(no).matches("QT-\\d{4}-\\d{3,}");
        assertThat(saved.get("message")).isEqualTo(no + " saved as Draft");
        assertThat(money(q.get("total"))).isEqualByComparingTo("23400");
        Map<String, Object> first = list(q.get("lines")).getFirst();
        assertThat(money(first.get("currentPrice"))).isEqualByComparingTo("300");
        assertThat(first.get("belowCurrent")).isEqualTo(true);
        assertThat(q.get("conditions").toString()).startsWith("Prices include delivery");
        assertThat(q.get("canEdit")).isEqualTo(true);
        assertThat(q.get("emailBody").toString()).contains("Please find attached our quotation " + no, "Total: Rs. 23,400.00");

        // Edit while a draft; print with the DRAFT mark.
        q = quotation(call(put("/customer-quotations/" + id), newOrg("orgName", q.get("orgName"), "subject", "Tender ref. DS/2026/7",
                "version", q.get("version")), 200));
        assertThat(history(q)).extracting(h -> h.get("label")).containsExactly("Draft", "Edited");
        MvcResult pdf = mvc.perform(get("/print/customer-quotation/" + id).cookie(admin)).andReturn();
        assertThat(pdf.getResponse().getStatus()).isEqualTo(200);
        String text = new PdfTextExtractor(new PdfReader(pdf.getResponse().getContentAsByteArray())).getTextFromPage(1);
        assertThat(text).contains("QUOTATION", no, "Subject: Tender ref. DS/2026/7", "Mrs. W.A. Dilrukshi, Administrative Officer",
                "Terms and conditions", "1. Prices include delivery", "Customer acceptance");

        // Sent by email: no more edits, only the answer.
        q = quotation(call(post("/customer-quotations/" + id + "/send"), Map.of("via", "EMAIL"), 200));
        assertThat(q.get("shown")).isEqualTo("SENT");
        assertThat(q.get("sentViaText")).isEqualTo("by email");
        Map<String, Object> err = call(put("/customer-quotations/" + id), newOrg("version", q.get("version")), 422);
        assertThat(err.get("message")).isEqualTo(CustomerQuotationRules.MSG_ONLY_DRAFT);
        assertThat(call(post("/customer-quotations/" + id + "/send"), Map.of("via", "POST"), 422).get("message"))
                .isEqualTo("Cannot change Sent to Sent.");

        q = quotation(call(post("/customer-quotations/" + id + "/accept"), Map.of(), 200));
        assertThat(q.get("canRegister")).isEqualTo(true);
        assertThat(list(q.get("agreedPrices"))).singleElement().satisfies(p -> {
            assertThat(p.get("bottleTypeCode")).isEqualTo(bottle);
            assertThat(money(p.get("price"))).isEqualByComparingTo("285");
        });
        assertThat(call(post("/customer-quotations/" + id + "/apply-prices"), Map.of(), 422).get("message"))
                .isEqualTo(CustomerQuotationRules.MSG_LINK_FIRST);

        // Register as customer from the quotation, with the quoted price as the agreed price.
        Map<String, Object> c = customer((String) q.get("orgName"));
        c.put("agreedPrices", Map.of(bottle, 285));
        c.put("agreedReason", "Quotation " + no + " accepted");
        c.put("fromQuotationId", id);
        long customerId = register(c);
        q = call(get("/customer-quotations/" + id), null, 200);
        assertThat(((Number) q.get("customerId")).longValue()).isEqualTo(customerId);
        assertThat(q.get("pricesAppliedOn")).isEqualTo(today.toString());
        assertThat(q.get("canRegister")).isEqualTo(false);
        assertThat(history(q)).extracting(h -> h.get("label"))
                .containsExactly("Draft", "Edited", "Sent", "Accepted", "Customer registered", "Prices applied");
        assertThat(jdbc.queryForObject("select price from customer_price where customer_id = ? and customer_quotation_id = ?",
                BigDecimal.class, customerId, id)).isEqualByComparingTo("285");
        // A quotation is registered once.
        Map<String, Object> again = customer("Again " + System.nanoTime());
        again.put("fromQuotationId", id);
        assertThat(fieldError(call(post("/customers/register"), again, 400))).isEqualTo(CustomerQuotationRules.MSG_NOT_REGISTRABLE);
        // The list: accepted, nothing left to do.
        Map<String, Object> row = list(call(get("/customer-quotations?q=" + no), null, 200).get("items")).getFirst();
        assertThat(row.get("shownLabel")).isEqualTo("Accepted");
        assertThat(row.get("nextStep")).isEqualTo("–");
    }

    @Test
    void applyPricesForAnExistingCustomerChangesThePriceFromTheChosenDateOnly() throws Exception {
        long customerId = register(customer("Vertex " + System.nanoTime()));
        MvcResult items = mvc.perform(get("/customer-quotations/items?customerTypeId=" + typeId + "&customerId=" + customerId).cookie(admin))
                .andReturn();
        assertThat(readList(items).stream().filter(i -> ("WATER|" + bottle).equals(i.get("key"))).findFirst().orElseThrow())
                .satisfies(i -> assertThat(money(i.get("currentPrice"))).isEqualByComparingTo("300"));

        Map<String, Object> body = newOrg("orgName", null, "customerId", customerId);
        body.put("lines", List.of(line("WATER", bottle, "Water", 16, "per month", 270)));
        Map<String, Object> q = quotation(call(post("/customer-quotations"), body, 201));
        long id = id(q);
        assertThat(q.get("orgName")).asString().startsWith("Vertex");
        assertThat(q.get("customerCode")).asString().startsWith("C");
        call(post("/customer-quotations/" + id + "/send"), Map.of("via", "BY_HAND"), 200);
        q = quotation(call(post("/customer-quotations/" + id + "/accept"), Map.of(), 200));
        assertThat(q.get("canApplyPrices")).isEqualTo(true);
        assertThat(list(call(get("/customer-quotations?q=" + q.get("cqNo")), null, 200).get("items")).getFirst().get("nextStep"))
                .isEqualTo("Apply prices to customer");

        String from = today.plusDays(7).toString();
        Map<String, Object> applied = call(post("/customer-quotations/" + id + "/apply-prices"), Map.of("from", from), 200);
        assertThat(applied.get("message")).isEqualTo("Agreed prices updated");
        q = quotation(applied);
        assertThat(q.get("pricesAppliedOn")).isEqualTo(from);
        assertThat(history(q).getLast().get("note").toString()).startsWith("1 price for C").endsWith(BusinessDates.format(today.plusDays(7)));
        // Today still the standard price; from the chosen date the quoted one.
        assertThat(money(read(mvc.perform(get(price(customerId, today.toString()))
                .cookie(admin)).andReturn()).get("price"))).isEqualByComparingTo("300");
        assertThat(money(read(mvc.perform(get(price(customerId, from))
                .cookie(admin)).andReturn()).get("price"))).isEqualByComparingTo("270");
        assertThat(call(post("/customer-quotations/" + id + "/apply-prices"), Map.of(), 422).get("message")).isEqualTo(CustomerQuotationRules.MSG_APPLIED);
    }

    @Test
    void notAcceptedExpiredCopyAndValidations() throws Exception {
        // Not accepted needs a reason; only after sending.
        long a = id(quotation(call(post("/customer-quotations"), newOrg(), 201)));
        assertThat(call(post("/customer-quotations/" + a + "/reject"), Map.of("reason", "Too high"), 422).get("message"))
                .isEqualTo("Cannot change Draft to Not accepted.");
        assertThat(fieldError(call(post("/customer-quotations/" + a + "/send"), Map.of("via", "FAX"), 400))).isEqualTo(CustomerQuotationRules.MSG_VIA);
        call(post("/customer-quotations/" + a + "/send"), Map.of("via", "POST"), 200);
        assertThat(fieldError(call(post("/customer-quotations/" + a + "/reject"), Map.of("reason", " "), 400))).isEqualTo(CustomerQuotationRules.MSG_REASON);
        Map<String, Object> rejected = quotation(call(post("/customer-quotations/" + a + "/reject"),
                Map.of("reason", "Tender given to the lowest bidder"), 200));
        assertThat(rejected.get("shownLabel")).isEqualTo("Not accepted");
        assertThat(rejected.get("rejectReason")).isEqualTo("Tender given to the lowest bidder");

        // Sent and past its date: Expired, still answerable.
        Map<String, Object> old = quotation(call(post("/customer-quotations"), newOrg("quoteDate", today.minusDays(40).toString(),
                "validUntil", today.minusDays(10).toString()), 201));
        long e = id(old);
        call(post("/customer-quotations/" + e + "/send"), Map.of("via", "WHATSAPP", "date", today.minusDays(39).toString()), 200);
        Map<String, Object> list = call(get("/customer-quotations?status=EXPIRED&q=" + old.get("cqNo")), null, 200);
        Map<String, Object> row = list(list.get("items")).getFirst();
        assertThat(row.get("shownLabel")).isEqualTo("Expired");
        assertThat(row.get("nextStep")).isEqualTo("Record the answer or make a new copy");
        assertThat(((Number) ((Map<?, ?>) list.get("kpis")).get("expiredOrRejected")).intValue()).isGreaterThanOrEqualTo(2);
        assertThat(list(call(get("/customer-quotations?status=SENT&q=" + old.get("cqNo")), null, 200).get("items"))).isEmpty();

        // Make a copy: a new draft that remembers where it came from.
        Map<String, Object> copy = quotation(call(post("/customer-quotations"), newOrg("orgName", old.get("orgName"), "copyOf", e), 201));
        assertThat(copy.get("copyOfNo")).isEqualTo(old.get("cqNo"));
        assertThat(history(copy).getFirst().get("note")).isEqualTo("Copy of " + old.get("cqNo"));
        call(post("/customer-quotations/" + e + "/accept"), Map.of(), 200);

        // Validations with the prototype messages.
        assertThat(fieldError(call(post("/customer-quotations"), newOrg("orgName", " "), 400))).isEqualTo(CustomerQuotationRules.MSG_TO);
        assertThat(fieldError(call(post("/customer-quotations"), newOrg("validUntil", today.minusDays(1).toString()), 400)))
                .isEqualTo(CustomerQuotationRules.MSG_VALID);
        assertThat(fieldError(call(post("/customer-quotations"), newOrg("customerTypeId", null), 400))).isEqualTo(CustomerQuotationRules.MSG_TYPE);
        assertThat(fieldError(call(post("/customer-quotations"), newOrg("lines", List.of()), 400))).isEqualTo(CustomerQuotationRules.MSG_NO_LINES);
        assertThat(fieldError(call(post("/customer-quotations"), newOrg("lines", List.of(line("OTHER", null, "Stand", 0, "units", 10))), 400)))
                .isEqualTo(CustomerQuotationRules.MSG_LINE);
        assertThat(fieldError(call(post("/customer-quotations"), newOrg("quoteDate", today.plusDays(1).toString()), 400)))
                .isEqualTo("Date cannot be after today (" + BusinessDates.format(today) + ").");
        assertThat(fieldError(call(post("/customer-quotations"), newOrg("lines", List.of(line("WATER", bottle, "A", 1, "x", 1),
                line("WATER", bottle, "B", 1, "x", 2))), 400))).isEqualTo("Water for one bottle type can be quoted only once.");
    }

    @Test
    void accountantViewsAndPrintsDeliveryStaffNothing() throws Exception {
        long id = id(quotation(call(post("/customer-quotations"), newOrg(), 201)));
        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        call(get("/customer-quotations"), accountant, null, 200);
        call(get("/customer-quotations/" + id), accountant, null, 200);
        call(get("/print/customer-quotation/" + id), accountant, null, 200);
        call(post("/customer-quotations"), accountant, newOrg(), 403);
        call(post("/customer-quotations/" + id + "/send"), accountant, Map.of("via", "EMAIL"), 403);
        call(get("/customer-quotations"), staff, null, 403);
        call(get("/print/customer-quotation/" + id), staff, null, 403);
    }
}
