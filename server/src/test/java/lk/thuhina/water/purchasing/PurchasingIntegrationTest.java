package lk.thuhina.water.purchasing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.printing.PurchaseOrderPrintService;
import lk.thuhina.water.purchasing.service.PurchaseOrderService;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * M09 Purchasing – SERVER (S2 – S7, S9): quotation requests with suggested suppliers, quotations compared side by side and
 * chosen with a reason, purchase orders only from the chosen quotation and one step at a time, goods receipts into stock,
 * supplier owed and payments, the PO PDF and the role rules. Each test has its own bottle type, product and suppliers.
 */
class PurchasingIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SIZE = new AtomicInteger();

    @Autowired
    private Clock clock;
    @Autowired
    private PurchaseOrderPrintService prints;
    @Autowired
    private PurchaseOrderService orderService;

    private Cookie admin;
    private Cookie accountant;
    private LocalDate today;
    private String bottle;
    private String product;
    private long supplierA;
    private long supplierB;
    private long supplierC;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(createUser(Role.ADMIN));
        accountant = login(createUser(Role.ACCOUNTANT));
        today = BusinessDates.today(clock);
        BigDecimal litres = new BigDecimal("100").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        bottle = (String) ok(send(post("/bottle-types"), Map.of("name", litres + "L P", "litres", litres)), 201).get("code");
        product = (String) ok(send(post("/products"), Map.of("name", "Pump " + System.nanoTime(), "sellingPrice", 1500, "costPrice", 900,
                "openingStock", 2)), 201).get("code");
        supplierA = supplier("Both", List.of(bottle, product));
        supplierB = supplier("Bottles", List.of(bottle));
        supplierC = supplier("Products", List.of(product));
    }

    /** The ledger and the stock agree for the test items: Σ GRN / other effects = the balances. */
    @AfterEach
    void stockMatchesLedger() {
        Integer empty = jdbc.queryForObject("select qty from stock_balance where bottle_type_code = ? and bucket = 'EMPTY'", Integer.class, bottle);
        Integer effects = jdbc.queryForObject("select coalesce(sum(delta), 0) from ledger_effect where bottle_type_code = ? and target = 'STOCK' "
                + "and field = 'EMPTY'", Integer.class, bottle);
        assertThat(empty).as("Empty in store = Σ ledger effects").isEqualTo(effects);
        Integer stock = jdbc.queryForObject("select stock_qty from product where code = ?", Integer.class, product);
        Integer productEffects = jdbc.queryForObject("select coalesce(sum(delta), 0) from ledger_effect where product_code = ?", Integer.class,
                product);
        assertThat(stock).as("Product stock = Σ ledger effects").isEqualTo(productEffects);
        // The test database is shared: an inactive bottle type no longer adds a column to other tests' lists.
        jdbc.update("update bottle_type set active = false where code = ?", bottle);
    }

    // ------------------------------------------------------------------ helpers

    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder b, Cookie cookie, Object body) {
        return b.cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    private ResultActions send(MockHttpServletRequestBuilder b, Object body) throws Exception {
        return mvc.perform(body(b, admin, body));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> ok(ResultActions r, int status) throws Exception {
        MvcResult res = r.andReturn();
        assertThat(res.getResponse().getStatus()).as(res.getResponse().getContentAsString()).isEqualTo(status);
        return json.readValue(res.getResponse().getContentAsString(), Map.class);
    }

    private Map<String, Object> getJson(String url) throws Exception {
        return ok(mvc.perform(get(url).cookie(admin)), 200);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getList(String url, Cookie cookie) throws Exception {
        MvcResult res = mvc.perform(get(url).cookie(cookie)).andExpect(status().isOk()).andReturn();
        return json.readValue(res.getResponse().getContentAsString(), List.class);
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

    private long supplier(String name, List<String> items) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name + " " + System.nanoTime());
        m.put("phone", "011 222 3333");
        m.put("termsDays", 30);
        m.put("items", items);
        return id(ok(send(post("/suppliers"), m), 201));
    }

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** A date a few days back, but in this year (the request number is yearly). */
    private LocalDate daysAgo(int days) {
        LocalDate d = today.minusDays(days);
        return d.getYear() == today.getYear() ? d : today.withDayOfYear(1);
    }

    /** A request for 300 empty test bottles and 20 test products, asked from A, B and C. */
    private Map<String, Object> request() throws Exception {
        return ok(send(post("/quotation-requests"), map("date", daysAgo(5).toString(), "requiredBy", today.plusDays(20).toString(),
                "note", "Test stock", "items", List.of(map("itemCode", bottle, "qty", 300), map("itemCode", product, "qty", 20)),
                "supplierIds", List.of(supplierA, supplierB, supplierC))), 201);
    }

    private ResultActions quote(long qrId, long supplierId, Object bottlePrice, Object productPrice, LocalDate date, LocalDate validUntil)
            throws Exception {
        return send(post("/quotation-requests/" + qrId + "/quotations"), map("supplierId", supplierId, "supplierRef", "REF-" + supplierId,
                "date", date.toString(), "validUntil", validUntil.toString(), "deliveryDays", 7,
                "prices", List.of(map("itemCode", bottle, "unitPrice", bottlePrice), map("itemCode", product, "unitPrice", productPrice)),
                "notes", "Free delivery"));
    }

    /** Request + quotation from A (bottle 620, product 1,000) chosen; returns A's quotation id. */
    private long chosenQuotation() throws Exception {
        long qr = id(request());
        Map<String, Object> d = ok(quote(qr, supplierA, 620, 1000, daysAgo(2), today.plusDays(30)), 201);
        long q = id(list(d.get("quotations")).getFirst());
        ok(send(post("/quotations/" + q + "/choose"), map("reason", "Best price")), 200);
        return q;
    }

    /** A PO from A's chosen quotation (300 × 620 + 20 × 1,000), approved and sent. */
    private Map<String, Object> sentPo() throws Exception {
        long po = id(ok(send(post("/quotations/" + chosenQuotation() + "/create-po"), null), 201));
        ok(send(post("/purchase-orders/" + po + "/approve"), null), 200);
        return ok(send(post("/purchase-orders/" + po + "/send"), null), 200);
    }

    private long lineId(Map<String, Object> po, String itemCode) {
        return id(list(po.get("lines")).stream().filter(l -> itemCode.equals(l.get("itemCode"))).findFirst().orElseThrow());
    }

    private int empty() {
        return jdbc.queryForObject("select qty from stock_balance where bottle_type_code = ? and bucket = 'EMPTY'", Integer.class, bottle);
    }

    private int productStock() {
        return jdbc.queryForObject("select stock_qty from product where code = ?", Integer.class, product);
    }

    private String pdfText(long poId) throws Exception {
        MvcResult pdf = mvc.perform(get("/print/purchase-order/" + poId).cookie(accountant)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf")).andReturn();
        PdfReader reader = new PdfReader(pdf.getResponse().getContentAsByteArray());
        StringBuilder text = new StringBuilder();
        for (int p = 1; p <= reader.getNumberOfPages(); p++) {
            text.append(new PdfTextExtractor(reader).getTextFromPage(p)).append(' ');
        }
        return text.toString().replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------ S2 – requests

    @Test
    void newRequestTicksTheSuppliersOfTheItems() throws Exception {
        long inactive = supplier("Old bottles", List.of(bottle));
        Map<String, Object> edit = map("name", "Old bottles X", "phone", "011 222 3333", "termsDays", 30, "items", List.of(bottle), "active", false);
        ok(send(put("/suppliers/" + inactive), edit), 200);

        List<Long> forBottle = getList("/quotation-requests/suggested-suppliers?items=" + bottle, admin).stream().map(PurchasingIntegrationTest::id)
                .toList();
        assertThat(forBottle).contains(supplierA, supplierB).doesNotContain(supplierC, inactive);
        List<Long> forBoth = getList("/quotation-requests/suggested-suppliers?items=" + bottle + "," + product, admin).stream()
                .map(PurchasingIntegrationTest::id).toList();
        assertThat(forBoth).contains(supplierA, supplierB, supplierC);

        send(post("/quotation-requests"), map("date", today.toString(), "items", List.of(map("itemCode", bottle, "qty", 0)),
                "supplierIds", List.of(supplierA)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.items").value("Add at least one item with a quantity."));
        send(post("/quotation-requests"), map("date", today.toString(), "items", List.of(map("itemCode", bottle, "qty", 5)),
                "supplierIds", List.of()))
                .andExpect(jsonPath("$.details.fields.supplierIds").value("Select the suppliers asked to quote."));
        send(post("/quotation-requests"), map("date", today.plusDays(1).toString(), "items", List.of(map("itemCode", bottle, "qty", 5)),
                "supplierIds", List.of(supplierA)))
                .andExpect(jsonPath("$.details.fields.date").value("Date cannot be after today (" + BusinessDates.format(today) + ")."));

        Map<String, Object> qr = request();
        assertThat((String) qr.get("qrNo")).matches("QR-" + today.getYear() + "-\\d{3,}");
        assertThat(qr).containsEntry("status", "OPEN").containsEntry("canAddQuotation", true).containsEntry("canChoose", false);
        assertThat(list(qr.get("items"))).extracting(i -> i.get("itemCode")).containsExactly(bottle, product);
        assertThat(list(qr.get("items")).getFirst().get("name")).asString().startsWith("Empty ");
        assertThat(list(qr.get("suppliers"))).hasSize(3).allMatch(x -> Boolean.FALSE.equals(x.get("quoted")));

        Map<String, Object> row = getList("/quotation-requests", admin).stream().filter(r -> id(r) == id(qr)).findFirst().orElseThrow();
        assertThat(row).containsEntry("suppliersAsked", 3).containsEntry("quotations", 0).containsEntry("statusLabel", "Open");
        assertThat(list(row.get("items"))).extracting(i -> i.get("qty")).containsExactly(300, 20);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = 'Quotation request' and ref = ?", Integer.class,
                qr.get("qrNo"))).isEqualTo(1);
    }

    // ------------------------------------------------------------------ S3 – quotations and comparison

    @Test
    void quotationsCompareSideBySideAndTheOwnerChooses() throws Exception {
        assumeTrue(today.getDayOfYear() > 5, "needs dates earlier in this year");
        long qr = id(request());
        ok(quote(qr, supplierA, 620, 1000, daysAgo(3), today.plusDays(30)), 201);
        Map<String, Object> afterB = ok(quote(qr, supplierB, 600, null, daysAgo(3), today.plusDays(10)), 201);
        assertThat(afterB).containsEntry("status", "QUOTES_RECEIVED");

        quote(qr, supplierA, 610, 990, daysAgo(2), today.plusDays(30))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUPLICATE_QUOTATION"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith("already has a quotation recorded for " + afterB.get("qrNo") + ".")));
        long notAsked = supplier("Not asked", List.of(bottle));
        quote(qr, notAsked, 500, null, daysAgo(2), today.plusDays(30))
                .andExpect(jsonPath("$.details.fields.supplierId").value("Select one of the suppliers asked to quote."));
        quote(qr, supplierC, null, null, daysAgo(2), today.plusDays(30))
                .andExpect(jsonPath("$.details.fields.prices").value("Enter at least one unit price."));
        quote(qr, supplierC, null, -5, daysAgo(2), today.plusDays(30)).andExpect(status().isBadRequest());

        // C quoted only the product, cheapest – but the quotation has expired.
        Map<String, Object> d = ok(quote(qr, supplierC, null, 900, daysAgo(4), daysAgo(1)), 201);
        List<Map<String, Object>> cols = list(d.get("quotations"));
        assertThat(cols).hasSize(3);
        Map<String, Object> a = cols.get(0);
        Map<String, Object> b = cols.get(1);
        Map<String, Object> c = cols.get(2);
        assertThat(money(a.get("total"))).isEqualByComparingTo("206000");
        assertThat(a).containsEntry("complete", true).containsEntry("expired", false);
        assertThat(b).containsEntry("complete", false);
        assertThat(c).containsEntry("expired", true);
        // Lowest per item, only when two or more prices are given.
        assertThat(list(a.get("prices"))).extracting(p -> p.get("lowest")).containsExactly(false, false);
        assertThat(list(b.get("prices"))).extracting(p -> p.get("lowest")).containsExactly(true, false);
        assertThat(list(c.get("prices"))).extracting(p -> p.get("lowest")).containsExactly(false, true);
        assertThat(list(b.get("prices")).get(1).get("unitPrice")).isNull();
        assertThat(d).containsEntry("canAddQuotation", false).containsEntry("canChoose", true).containsEntry("canCreatePo", false);

        send(post("/quotations/" + id(c) + "/choose"), map("reason", "Cheapest"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("QUOTATION_EXPIRED"))
                .andExpect(jsonPath("$.message").value("This quotation expired on " + BusinessDates.format(daysAgo(1))
                        + ". Ask the supplier to re-quote."));
        send(post("/quotations/" + id(a) + "/choose"), map("reason", " "))
                .andExpect(jsonPath("$.details.fields.reason").value("Add the reason for choosing this quotation."));
        Map<String, Object> chosen = ok(send(post("/quotations/" + id(a) + "/choose"), map("reason", "Complete and free delivery")), 200);
        assertThat(chosen).containsEntry("status", "SELECTED").containsEntry("canCreatePo", true).containsEntry("canChoose", false);
        assertThat(list(chosen.get("quotations")).getFirst()).containsEntry("selected", true)
                .containsEntry("selectionReason", "Complete and free delivery").containsEntry("selectedByName", "Test Admin");

        send(post("/quotations/" + id(b) + "/choose"), map("reason", "Cheaper bottles"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("A quotation has already been chosen for " + chosen.get("qrNo") + "."));
        Map<String, Object> row = getList("/quotation-requests?status=SELECTED", admin).stream().filter(r -> id(r) == qr).findFirst()
                .orElseThrow();
        assertThat(row).containsEntry("quotations", 3);
        assertThat(((Map<?, ?>) row.get("chosen")).get("id")).isEqualTo((int) supplierA);
    }

    @Test
    void scannedQuotationUploadAndOpen() throws Exception {
        long qr = id(request());
        long q = id(list(ok(quote(qr, supplierB, 600, null, daysAgo(1), today.plusDays(9)), 201).get("quotations")).getFirst());
        byte[] pdf = "%PDF-1.4 test quotation".getBytes(StandardCharsets.US_ASCII);
        mvc.perform(multipart("/quotations/" + q + "/attachment").file(new MockMultipartFile("file", "C:\\scans\\B-Q-77.pdf",
                        "application/pdf", pdf)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attachmentName").value("B-Q-77.pdf"));
        mvc.perform(multipart("/quotations/" + q + "/attachment").file(new MockMultipartFile("file", "notes.txt", "text/plain",
                        "hello".getBytes(StandardCharsets.US_ASCII))).cookie(admin))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.file").value("Attach a PDF, JPG or PNG file."));
        mvc.perform(multipart("/quotations/" + q + "/attachment").file(new MockMultipartFile("file", "x.pdf", "application/pdf", pdf))
                .cookie(accountant)).andExpect(status().isForbidden());

        MvcResult file = mvc.perform(get("/quotations/" + q + "/attachment").cookie(accountant)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf")).andReturn();
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(pdf);
        Map<String, Object> col = list(getJson("/quotation-requests/" + qr).get("quotations")).getFirst();
        assertThat(col).containsEntry("attachmentName", "B-Q-77.pdf").containsEntry("attachmentFile", true);
    }

    // ------------------------------------------------------------------ S4 – purchase orders

    @Test
    void poOnlyFromTheChosenQuotationAndOneStepAtATime() throws Exception {
        long qr = id(request());
        Map<String, Object> d = ok(quote(qr, supplierA, 620, 1000, daysAgo(1), today.plusDays(30)), 201);
        d = ok(quote(qr, supplierB, 600, null, daysAgo(1), today.plusDays(30)), 201);
        long qa = id(list(d.get("quotations")).get(0));
        long qb = id(list(d.get("quotations")).get(1));
        send(post("/quotations/" + qa + "/create-po"), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("A purchase order can be created only from the chosen quotation."));
        ok(send(post("/quotations/" + qa + "/choose"), map("reason", "Complete")), 200);
        send(post("/quotations/" + qb + "/create-po"), null).andExpect(jsonPath("$.code").value("PO_NOT_CHOSEN"));

        Map<String, Object> po = ok(send(post("/quotations/" + qa + "/create-po"), null), 201);
        long poId = id(po);
        assertThat((String) po.get("poNo")).matches("PO-" + today.getYear() + "-\\d{4,}");
        assertThat(po).containsEntry("status", "DRAFT").containsEntry("nextStep", "Approve").containsEntry("editable", true)
                .containsEntry("expectedDate", today.plusDays(7).toString()).containsEntry("canReceive", false);
        assertThat(money(po.get("total"))).isEqualByComparingTo("206000");
        assertThat(list(po.get("lines"))).hasSize(2);
        assertThat(getJson("/quotation-requests/" + qr)).containsEntry("status", "CLOSED").containsEntry("canCreatePo", false);
        send(post("/quotations/" + qa + "/create-po"), null)
                .andExpect(jsonPath("$.code").value("PO_EXISTS"))
                .andExpect(jsonPath("$.message").value("Purchase order " + po.get("poNo") + " already exists for this quotation."));

        // Steps cannot be skipped.
        send(post("/purchase-orders/" + poId + "/send"), null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.message").value("Cannot change Draft to Sent."));
        send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(),
                "lines", List.of(map("poLineId", lineId(po, bottle), "received", 1, "damaged", 0))))
                .andExpect(jsonPath("$.message").value("Goods can be received only against an approved and sent PO. " + po.get("poNo")
                        + " is Draft."));

        // Draft edits: product removed, bottle price changed.
        send(put("/purchase-orders/" + poId), map("expectedDate", today.plusDays(10).toString(), "lines",
                List.of(map("itemCode", bottle, "qty", 0), map("itemCode", product, "qty", 0))))
                .andExpect(jsonPath("$.details.fields.lines").value("At least one line needs a quantity."));
        send(put("/purchase-orders/" + poId), map("expectedDate", today.plusDays(10).toString(), "lines",
                List.of(map("itemCode", bottle, "qty", 300, "unitPrice", 0))))
                .andExpect(jsonPath("$.details.fields.lines").value(org.hamcrest.Matchers.startsWith("Enter the unit price for Empty ")));
        Map<String, Object> edited = ok(send(put("/purchase-orders/" + poId), map("expectedDate", today.plusDays(10).toString(),
                "notes", "Deliver to the back gate", "lines", List.of(map("itemCode", bottle, "qty", 300, "unitPrice", 610),
                        map("itemCode", product, "qty", 0)))), 200);
        assertThat(list(edited.get("lines"))).hasSize(1);
        assertThat(money(edited.get("total"))).isEqualByComparingTo("183000");
        assertThat(edited).containsEntry("expectedDate", today.plusDays(10).toString()).containsEntry("notes", "Deliver to the back gate");

        Map<String, Object> approved = ok(send(post("/purchase-orders/" + poId + "/approve"), null), 200);
        assertThat(approved).containsEntry("status", "APPROVED").containsEntry("nextStep", "Mark as sent").containsEntry("editable", false)
                .containsEntry("approvedByName", "Test Admin").containsEntry("canCancel", true);
        send(put("/purchase-orders/" + poId), map("expectedDate", today.plusDays(10).toString(), "lines", List.of()))
                .andExpect(jsonPath("$.message").value("Only Draft purchase orders can be edited."));
        Map<String, Object> sent = ok(send(post("/purchase-orders/" + poId + "/send"), null), 200);
        assertThat(sent).containsEntry("status", "SENT").containsEntry("nextStep", "Receive goods").containsEntry("canCancel", false)
                .containsEntry("canReceive", true);
        send(post("/purchase-orders/" + poId + "/cancel"), map("reason", "Too late"))
                .andExpect(jsonPath("$.message").value("Cannot change Sent to Cancelled."));
        assertThat(list(sent.get("history"))).extracting(h -> h.get("status")).containsExactly("DRAFT", "APPROVED", "SENT");

        Map<String, Object> row = getList("/purchase-orders?status=SENT", admin).stream().filter(r -> id(r) == poId).findFirst().orElseThrow();
        assertThat(row).containsEntry("nextStep", "Receive goods").containsEntry("ordered", 300).containsEntry("received", 0);
        assertThat(jdbc.queryForObject("select count(*) from audit_log where entity = 'Purchase order' and ref = ?", Integer.class,
                po.get("poNo"))).isEqualTo(4);
    }

    @Test
    void cancelOnlyFromDraftOrApprovedWithAReasonThenOrderAgain() throws Exception {
        long q = chosenQuotation();
        long first = id(ok(send(post("/quotations/" + q + "/create-po"), null), 201));
        ok(send(post("/purchase-orders/" + first + "/approve"), null), 200);
        send(post("/purchase-orders/" + first + "/cancel"), map("reason", ""))
                .andExpect(jsonPath("$.details.fields.reason").value("Enter the reason for cancelling."));
        Map<String, Object> cancelled = ok(send(post("/purchase-orders/" + first + "/cancel"), map("reason", "Supplier cannot deliver")), 200);
        assertThat(cancelled).containsEntry("status", "CANCELLED").containsEntry("cancelReason", "Supplier cannot deliver")
                .containsEntry("nextStep", null).containsEntry("canCancel", false);
        assertThat(list(cancelled.get("history")).getLast()).containsEntry("note", "Supplier cannot deliver");
        send(post("/purchase-orders/" + first + "/approve"), null).andExpect(jsonPath("$.message").value("Cannot change Cancelled to Approved."));

        // The chosen quotation can be ordered again.
        long qr = ((Number) cancelled.get("qrId")).longValue();
        assertThat(getJson("/quotation-requests/" + qr)).containsEntry("canCreatePo", true);
        Map<String, Object> again = ok(send(post("/quotations/" + q + "/create-po"), null), 201);
        assertThat(again.get("poNo")).isNotEqualTo(cancelled.get("poNo"));
        assertThat(((Map<?, ?>) getJson("/quotation-requests/" + qr).get("po")).get("poNo")).isEqualTo(again.get("poNo"));
    }

    // ------------------------------------------------------------------ S5 – goods receipt

    @Test
    void receiving300With3DamagedAdds297ToEmptyInStore() throws Exception {
        Map<String, Object> po = sentPo();
        long poId = id(po);
        int emptyBefore = empty();
        int productBefore = productStock();
        long bottleLine = lineId(po, bottle);
        long productLine = lineId(po, product);

        send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(),
                "lines", List.of(map("poLineId", bottleLine, "received", 301, "damaged", 0))))
                .andExpect(jsonPath("$.details.fields.lines").value(org.hamcrest.Matchers.endsWith("is more than the 300 still due.")));
        send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(),
                "lines", List.of(map("poLineId", productLine, "received", 2, "damaged", 3))))
                .andExpect(jsonPath("$.details.fields.lines").value(org.hamcrest.Matchers.startsWith("Damaged cannot be more than received for ")));
        send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(), "lines", List.of()))
                .andExpect(jsonPath("$.details.fields.lines").value("Enter the quantities received."));

        Map<String, Object> result = ok(send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(),
                "deliveryNote", "DN-1", "note", "3 cracked", "lines", List.of(map("poLineId", bottleLine, "received", 300, "damaged", 3),
                        map("poLineId", productLine, "received", 4, "damaged", 0)))), 201);
        Map<?, ?> grn = (Map<?, ?>) result.get("receipt");
        Map<?, ?> after = (Map<?, ?>) result.get("po");
        assertThat((String) grn.get("grnNo")).matches("GRN-\\d{4,}");
        assertThat(money(grn.get("value"))).isEqualByComparingTo(BigDecimal.valueOf(297 * 620 + 4 * 1000));
        assertThat(after.get("status")).isEqualTo("PARTLY_RECEIVED");
        assertThat(after.get("nextStep")).isEqualTo("Receive the rest");
        assertThat(empty()).isEqualTo(emptyBefore + 297);
        assertThat(productStock()).isEqualTo(productBefore + 4);
        assertThat(jdbc.queryForObject("select description from stock_movement where doc_no = ? and item_code = ?", String.class,
                grn.get("grnNo"), bottle)).isEqualTo("Goods received – " + po.get("poNo"));

        // The rest of the products: Received; nothing more can be received.
        Map<String, Object> rest = ok(send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(),
                "lines", List.of(map("poLineId", productLine, "received", 16, "damaged", 1)))), 201);
        Map<?, ?> done = (Map<?, ?>) rest.get("po");
        assertThat(done.get("status")).isEqualTo("RECEIVED");
        assertThat(done.get("nextStep")).isNull();
        assertThat(money(done.get("receivedValue"))).isEqualByComparingTo(BigDecimal.valueOf(297 * 620 + 4 * 1000 + 15 * 1000));
        assertThat(productStock()).isEqualTo(productBefore + 19);
        assertThat(list(done.get("history"))).extracting(h -> h.get("status"))
                .containsExactly("DRAFT", "APPROVED", "SENT", "PARTLY_RECEIVED", "RECEIVED");
        assertThat((List<?>) done.get("receipts")).hasSize(2);
        send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(),
                "lines", List.of(map("poLineId", productLine, "received", 1, "damaged", 0))))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.endsWith(" is Received.")));
    }

    // ------------------------------------------------------------------ S6 – supplier owed and payments

    @Test
    void supplierOwedIsReceivedValueMinusPayments() throws Exception {
        Map<String, Object> po = sentPo();
        long poId = id(po);
        ok(send(post("/purchase-orders/" + poId + "/receipts"), map("date", today.toString(),
                "lines", List.of(map("poLineId", lineId(po, bottle), "received", 100, "damaged", 0)))), 201);
        BigDecimal value = BigDecimal.valueOf(100 * 620);

        Map<String, Object> bal = getList("/supplier-balances", accountant).stream().filter(b -> ((Number) b.get("supplierId")).longValue() == supplierA)
                .findFirst().orElseThrow();
        assertThat(money(bal.get("received"))).isEqualByComparingTo(value);
        assertThat(money(bal.get("owed"))).isEqualByComparingTo(value);
        assertThat(bal).containsEntry("oldestUnpaidDate", today.toString()).containsEntry("oldestUnpaidDays", 0).containsEntry("overdue", false);

        Map<String, Object> pay = map("supplierId", supplierA, "date", today.toString(), "amount", 0, "method", "BANK_TRANSFER");
        send(post("/supplier-payments"), pay).andExpect(jsonPath("$.details.fields.amount").value("Enter the amount paid."));
        pay.put("amount", 50000);
        send(post("/supplier-payments"), pay)
                .andExpect(jsonPath("$.details.fields.reference").value("Enter the cheque number or bank reference."));
        pay.put("poId", id(sentPoOf(supplierB)));
        send(post("/supplier-payments"), pay).andExpect(status().isBadRequest());
        pay.put("poId", poId);
        pay.put("reference", "BOC-1");
        Map<String, Object> paid = ok(send(post("/supplier-payments"), pay), 201);
        assertThat((String) paid.get("spNo")).matches("SP-\\d{4,}");
        assertThat(paid).containsEntry("poNo", po.get("poNo")).containsEntry("methodLabel", "Bank transfer");
        ok(send(post("/supplier-payments"), map("supplierId", supplierA, "date", today.toString(), "amount", 2000, "method", "CASH")), 201);

        BigDecimal owed = value.subtract(BigDecimal.valueOf(52000));
        bal = getList("/supplier-balances", admin).stream().filter(b -> ((Number) b.get("supplierId")).longValue() == supplierA).findFirst()
                .orElseThrow();
        assertThat(money(bal.get("paid"))).isEqualByComparingTo("52000");
        assertThat(money(bal.get("owed"))).isEqualByComparingTo(owed);
        // The Owed column of the Suppliers tab (M03).
        Map<String, Object> sup = getList("/suppliers", accountant).stream().filter(s -> id(s) == supplierA).findFirst().orElseThrow();
        assertThat(money(sup.get("owed"))).isEqualByComparingTo(owed);
        assertThat(getList("/supplier-payments?supplier=" + supplierA, accountant)).hasSize(2);
    }

    /** A sent PO of another supplier (bottles only). */
    private Map<String, Object> sentPoOf(long supplierId) throws Exception {
        Map<String, Object> qr = ok(send(post("/quotation-requests"), map("date", today.toString(),
                "items", List.of(map("itemCode", bottle, "qty", 10)), "supplierIds", List.of(supplierId))), 201);
        long q = id(list(ok(send(post("/quotation-requests/" + id(qr) + "/quotations"), map("supplierId", supplierId, "date", today.toString(),
                "validUntil", today.plusDays(5).toString(), "deliveryDays", 3, "prices", List.of(map("itemCode", bottle, "unitPrice", 600)))),
                201).get("quotations")).getFirst());
        ok(send(post("/quotations/" + q + "/choose"), map("reason", "Only one")), 200);
        long po = id(ok(send(post("/quotations/" + q + "/create-po"), null), 201));
        ok(send(post("/purchase-orders/" + po + "/approve"), null), 200);
        return ok(send(post("/purchase-orders/" + po + "/send"), null), 200);
    }

    // ------------------------------------------------------------------ S7 – PO PDF

    @Test
    void poPrintsWithTheStatusWatermark() throws Exception {
        long q = chosenQuotation();
        Map<String, Object> po = ok(send(post("/quotations/" + q + "/create-po"), null), 201);
        long poId = id(po);
        assertThat(prints.document(orderService.detail(poId)).watermark()).isEqualTo("DRAFT");
        String draft = pdfText(poId);
        assertThat(draft).contains("PURCHASE ORDER", (String) po.get("poNo"), "DRAFT – not valid for supply", "REF-" + supplierA,
                "Rs. 206,000.00", "Supplier acceptance");

        ok(send(post("/purchase-orders/" + poId + "/approve"), null), 200);
        assertThat(prints.document(orderService.detail(poId)).watermark()).isNull();
        assertThat(pdfText(poId)).doesNotContain("not valid for supply").contains("Approved by Test Admin");

        ok(send(post("/purchase-orders/" + poId + "/cancel"), map("reason", "Changed plan")), 200);
        assertThat(prints.document(orderService.detail(poId)).watermark()).isEqualTo("CANCELLED");
        assertThat(pdfText(poId)).contains("CANCELLED – not valid for supply. Reason: Changed plan");
    }

    // ------------------------------------------------------------------ roles

    @Test
    void accountantCanViewButNotChangeAnything() throws Exception {
        Map<String, Object> po = sentPo();
        long poId = id(po);
        long qr = ((Number) po.get("qrId")).longValue();
        for (String url : List.of("/quotation-requests", "/quotation-requests/" + qr, "/purchase-orders", "/purchase-orders/" + poId,
                "/supplier-balances", "/supplier-payments", "/quotation-requests/suggested-suppliers?items=" + bottle)) {
            mvc.perform(get(url).cookie(accountant)).andExpect(status().isOk());
        }
        Map<String, Object> newQr = map("date", today.toString(), "items", List.of(map("itemCode", bottle, "qty", 5)),
                "supplierIds", List.of(supplierA));
        mvc.perform(body(post("/quotation-requests"), accountant, newQr)).andExpect(status().isForbidden());
        mvc.perform(body(post("/quotation-requests/" + qr + "/quotations"), accountant, map("supplierId", supplierB, "date", today.toString())))
                .andExpect(status().isForbidden());
        long q = ((Number) po.get("quotationId")).longValue();
        mvc.perform(body(post("/quotations/" + q + "/choose"), accountant, map("reason", "x"))).andExpect(status().isForbidden());
        mvc.perform(body(post("/quotations/" + q + "/create-po"), accountant, map())).andExpect(status().isForbidden());
        mvc.perform(body(put("/purchase-orders/" + poId), accountant, map("expectedDate", today.toString()))).andExpect(status().isForbidden());
        for (String action : List.of("approve", "send", "cancel")) {
            mvc.perform(body(post("/purchase-orders/" + poId + "/" + action), accountant, map("reason", "x"))).andExpect(status().isForbidden());
        }
        mvc.perform(body(post("/purchase-orders/" + poId + "/receipts"), accountant, map("date", today.toString(), "lines", List.of())))
                .andExpect(status().isForbidden());
        mvc.perform(body(post("/supplier-payments"), accountant, map("supplierId", supplierA, "date", today.toString(), "amount", 10,
                "method", "CASH"))).andExpect(status().isForbidden());
        mvc.perform(get("/print/purchase-order/" + poId).cookie(accountant)).andExpect(status().isOk());

        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        mvc.perform(get("/quotation-requests").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/purchase-orders").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/print/purchase-order/" + poId).cookie(staff)).andExpect(status().isForbidden());
    }
}
