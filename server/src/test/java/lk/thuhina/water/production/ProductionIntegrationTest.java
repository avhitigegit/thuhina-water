package lk.thuhina.water.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * M10 Filling Factory – SERVER (S2 – S4, S6): send empties (one dispatch, one batch per bottle type, all-or-nothing, charge
 * copied at dispatch), receive filled in parts with rejects (written off + damage record), filling cost, factory balances by
 * month and payments, the Factories tab columns, the dispatch note and the role rules. Each test has its own bottle type
 * (sizes 50.xx) and factory, so the counts are the test's own.
 */
class ProductionIntegrationTest extends IntegrationTest {

    private static final AtomicInteger SIZE = new AtomicInteger();

    @Autowired
    private Clock clock;

    private Cookie admin;
    private Cookie accountant;
    private LocalDate today;
    private String bottle;
    private String bottleName;
    private long factoryId;
    private String factoryName;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(createUser(Role.ADMIN));
        accountant = login(createUser(Role.ACCOUNTANT));
        today = BusinessDates.today(clock);
        BigDecimal litres = new BigDecimal("50").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        bottleName = litres + "L F";
        bottle = (String) ok(send(post("/bottle-types"), Map.of("name", bottleName, "litres", litres)), 201).get("code");
        count("EMPTY", 100);
        factoryName = "Filler " + System.nanoTime();
        factoryId = id(ok(send(post("/factories"), factory(factoryName, 60)), 201));
    }

    /** The ledger and the stock agree for the test bottle type in every bucket the factory touches. */
    @AfterEach
    void stockMatchesLedger() {
        for (String bucket : List.of("EMPTY", "FACTORY", "FILLED", "WRITTEN_OFF")) {
            Integer qty = jdbc.queryForObject("select qty from stock_balance where bottle_type_code = ? and bucket = ?", Integer.class, bottle,
                    bucket);
            Integer effects = jdbc.queryForObject("select coalesce(sum(delta), 0) from ledger_effect where bottle_type_code = ? "
                    + "and target = 'STOCK' and field = ?", Integer.class, bottle, bucket);
            assertThat(qty).as(bucket + " = Σ ledger effects").isEqualTo(effects);
        }
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

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** A factory with a charge for every active bottle type (Rs. 50) and {@code ours} for the test bottle type. */
    private Map<String, Object> factory(String name, int ours) throws Exception {
        Map<String, Object> charges = new LinkedHashMap<>();
        for (Map<String, Object> b : getList("/bottle-types?activeOnly=true", admin)) {
            charges.put((String) b.get("code"), b.get("code").equals(bottle) ? ours : 50);
        }
        return map("name", name, "address", "Kandy Road", "contact", "Mr. Filler", "phone", "011 292 7788", "termsDays", 14, "charges", charges);
    }

    private void count(String bucket, int qty) throws Exception {
        MvcResult r = send(post("/stock/adjustments"), map("date", today.toString(), "itemCode", bottle, "mode", "COUNT", "bucket", bucket,
                "counted", qty, "reason", "test stock")).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(201, 422);
    }

    private int stock(String bucket) {
        return jdbc.queryForObject("select qty from stock_balance where bottle_type_code = ? and bucket = ?", Integer.class, bottle, bucket);
    }

    private ResultActions dispatch(LocalDate date, int qty) throws Exception {
        return send(post("/factory-dispatches"), map("factoryId", factoryId, "date", date.toString(), "vehicleNo", "LH-4521", "note", "Weekly",
                "lines", List.of(map("bottleTypeCode", bottle, "qty", qty))));
    }

    private Map<String, Object> batchOf(Map<String, Object> dispatch) {
        return list(dispatch.get("batches")).getFirst();
    }

    private ResultActions receive(long batchId, LocalDate date, int filled, int rejected, String reason) throws Exception {
        return send(post("/factory-batches/" + batchId + "/returns"), map("date", date.toString(), "filled", filled, "rejected", rejected,
                "rejectReason", reason));
    }

    private Map<String, Object> monthRow() throws Exception {
        String period = today.toString().substring(0, 7);
        return getList("/factory-balances", accountant).stream()
                .filter(m -> ((Number) m.get("factoryId")).longValue() == factoryId && period.equals(m.get("period"))).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ S2 – send empties

    @Test
    void sendingEmptiesMovesThemToTheFactoryAndCopiesTheCharge() throws Exception {
        Map<String, Object> d = ok(dispatch(today, 40), 201);
        assertThat((String) d.get("dispatchNo")).matches("FD-\\d{4,}");
        assertThat(d).containsEntry("totalBottles", 40).containsEntry("vehicleNo", "LH-4521");
        Map<String, Object> b = batchOf(d);
        assertThat((String) b.get("batchNo")).matches("FB-\\d{4,}");
        assertThat(b).containsEntry("qtySent", 40).containsEntry("atFactory", 40).containsEntry("status", "AT_FACTORY")
                .containsEntry("statusLabel", "At factory");
        assertThat(money(b.get("charge"))).isEqualByComparingTo("60");
        assertThat(stock("EMPTY")).isEqualTo(60);
        assertThat(stock("FACTORY")).isEqualTo(40);
        assertThat(jdbc.queryForObject("select description from stock_movement where doc_no = ? and item_code = ? and bucket = 'EMPTY'",
                String.class, d.get("dispatchNo"), bottle)).isEqualTo("Dispatched to " + factoryName);

        Map<String, Object> kpis = ok(mvc.perform(get("/production/summary").cookie(accountant)), 200);
        assertThat(list(kpis.get("bottles")).stream().filter(x -> bottle.equals(x.get("bottleTypeCode"))).findFirst().orElseThrow())
                .containsEntry("empty", 60).containsEntry("atFactory", 40);

        // A later charge change does not change this batch's cost (BR-11).
        ok(send(put("/factories/" + factoryId), factory(factoryName, 75)), 200);
        Map<String, Object> r = ok(receive(id(b), today, 40, 0, null), 201);
        assertThat(money(((Map<?, ?>) r.get("saved")).get("cost"))).isEqualByComparingTo("2400");
        Map<String, Object> second = ok(dispatch(today, 10), 201);
        assertThat(money(batchOf(second).get("charge"))).isEqualByComparingTo("75");
    }

    @Test
    void sendingMoreThanInStoreIsBlockedAndNothingIsSaved() throws Exception {
        Integer batchesBefore = jdbc.queryForObject("select count(*) from factory_batch", Integer.class);
        dispatch(today, 101)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"));
        send(post("/factory-dispatches"), map("factoryId", factoryId, "date", today.toString(),
                "lines", List.of(map("bottleTypeCode", bottle, "qty", 0))))
                .andExpect(jsonPath("$.details.fields.lines").value("Enter the number of empty bottles sent for at least one bottle type."));
        send(post("/factory-dispatches"), map("date", today.toString(), "lines", List.of(map("bottleTypeCode", bottle, "qty", 5))))
                .andExpect(jsonPath("$.details.fields.factoryId").value("Select an active filling factory."));
        dispatch(today.plusDays(1), 5).andExpect(status().isBadRequest());

        // A bottle type the factory has no charge for: the whole dispatch is refused.
        BigDecimal litres = new BigDecimal("50").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        String other = (String) ok(send(post("/bottle-types"), Map.of("name", litres + "L N", "litres", litres)), 201).get("code");
        send(post("/factory-dispatches"), map("factoryId", factoryId, "date", today.toString(),
                "lines", List.of(map("bottleTypeCode", bottle, "qty", 5), map("bottleTypeCode", other, "qty", 1))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_CHARGE"))
                .andExpect(jsonPath("$.message").value(factoryName + " has no filling charge for " + litres + "L N. Set it under Suppliers & Factories."));
        jdbc.update("update bottle_type set active = false where code = ?", other);

        assertThat(stock("EMPTY")).isEqualTo(100);
        assertThat(stock("FACTORY")).isZero();
        assertThat(jdbc.queryForObject("select count(*) from factory_batch", Integer.class)).isEqualTo(batchesBefore);
    }

    @Test
    void oneDispatchSendsSeveralBottleTypesAsOneBatchEach() throws Exception {
        BigDecimal litres = new BigDecimal("50").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        String second = (String) ok(send(post("/bottle-types"), Map.of("name", litres + "L S", "litres", litres)), 201).get("code");
        jdbc.update("insert into factory_charge (factory_id, bottle_type_code, charge, effective_from) values (?, ?, 40, ?)", factoryId,
                second, today);
        send(post("/stock/adjustments"), map("date", today.toString(), "itemCode", second, "mode", "COUNT", "bucket", "EMPTY", "counted", 10,
                "reason", "test stock")).andExpect(status().isCreated());
        try {
            Map<String, Object> d = ok(send(post("/factory-dispatches"), map("factoryId", factoryId, "date", today.toString(),
                    "lines", List.of(map("bottleTypeCode", bottle, "qty", 30), map("bottleTypeCode", second, "qty", 4)))), 201);
            assertThat(list(d.get("batches"))).extracting(b -> b.get("bottleTypeCode")).containsExactlyInAnyOrder(bottle, second);
            assertThat(list(d.get("batches"))).extracting(b -> b.get("dispatchNo")).containsOnly(d.get("dispatchNo"));
            assertThat(d).containsEntry("totalBottles", 34);

            // Too many of one type: nothing of the other type is sent either.
            send(post("/factory-dispatches"), map("factoryId", factoryId, "date", today.toString(),
                    "lines", List.of(map("bottleTypeCode", bottle, "qty", 5), map("bottleTypeCode", second, "qty", 7))))
                    .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"));
            assertThat(stock("EMPTY")).isEqualTo(70);
        } finally {
            jdbc.update("update bottle_type set active = false where code = ?", second);
        }
    }

    // ------------------------------------------------------------------ S3 – receive filled

    @Test
    void aBatchCanComeBackInTwoPartsAndRejectsAreWrittenOff() throws Exception {
        LocalDate sent = today.minusDays(3);
        // The factory's charges start today; a dispatch three days ago needs the charge in force then.
        jdbc.update("insert into factory_charge (factory_id, bottle_type_code, charge, effective_from) values (?, ?, 60, ?)", factoryId, bottle,
                sent);
        Map<String, Object> b = batchOf(ok(dispatch(sent, 50), 201));
        long batchId = id(b);

        receive(batchId, sent.minusDays(1), 10, 0, null)
                .andExpect(jsonPath("$.details.fields.date").value("Return date cannot be before the dispatch date."));
        receive(batchId, today, 10, 2, " ")
                .andExpect(jsonPath("$.details.fields.rejectReason").value("Enter the reason for the rejected bottles."));
        receive(batchId, today, 0, 0, null)
                .andExpect(jsonPath("$.details.fields.filled").value("Enter filled and/or rejected bottles received."));

        Map<String, Object> first = ok(receive(batchId, today, 30, 2, "Failed seal test"), 201);
        Map<?, ?> batch = (Map<?, ?>) first.get("batch");
        assertThat(batch.get("status")).isEqualTo("PARTLY_RETURNED");
        assertThat(batch.get("atFactory")).isEqualTo(18);
        assertThat((String) ((Map<?, ?>) first.get("saved")).get("returnNo")).matches("FR-\\d{4,}");
        assertThat(stock("FACTORY")).isEqualTo(18);
        assertThat(stock("FILLED")).isEqualTo(30);
        assertThat(stock("WRITTEN_OFF")).isEqualTo(2);
        // Rejects are company damage "At factory" in the damage list.
        String damageNo = (String) first.get("damageNo");
        Map<String, Object> dmg = jdbc.queryForMap("select qty, responsibility, location, reason, source_type from damage_record where damage_no = ?",
                damageNo);
        assertThat(dmg).containsEntry("qty", 2).containsEntry("responsibility", "COMPANY").containsEntry("location", "AT_FACTORY")
                .containsEntry("reason", "Failed seal test").containsEntry("source_type", "RETURN");

        receive(batchId, today, 19, 0, null)
                .andExpect(jsonPath("$.details.fields.filled").value("Only 18 bottles of this batch are still at the factory."));
        Map<String, Object> rest = ok(receive(batchId, today, 18, 0, null), 201);
        Map<?, ?> done = (Map<?, ?>) rest.get("batch");
        assertThat(done.get("status")).isEqualTo("RETURNED");
        assertThat(done.get("atFactory")).isEqualTo(0);
        assertThat(money(done.get("cost"))).isEqualByComparingTo(BigDecimal.valueOf(48 * 60));
        assertThat((List<?>) done.get("returns")).hasSize(2);
        assertThat(rest.get("damageNo")).isNull();
        assertThat(getList("/factory-batches?open=true", admin)).noneMatch(x -> id(x) == batchId);
    }

    // ------------------------------------------------------------------ S4 – balances and payments

    @Test
    void factoryPaymentsByMonthAndTheFactoriesTabColumns() throws Exception {
        Map<String, Object> b = batchOf(ok(dispatch(today, 50), 201));
        ok(receive(id(b), today, 40, 0, null), 201);

        Map<String, Object> m = monthRow();
        assertThat(m).containsEntry("filled", 40).containsEntry("status", "UNPAID").containsEntry("factoryName", factoryName);
        assertThat(money(m.get("cost"))).isEqualByComparingTo("2400");

        Map<String, Object> f = getList("/factories", accountant).stream().filter(x -> id(x) == factoryId).findFirst().orElseThrow();
        assertThat(f).containsEntry("atFactory", 10);
        assertThat(money(f.get("owed"))).isEqualByComparingTo("2400");

        String period = today.toString().substring(0, 7);
        Map<String, Object> pay = map("factoryId", factoryId, "period", period, "date", today.toString(), "amount", 1000, "method", "BANK_TRANSFER");
        send(post("/factory-payments"), pay)
                .andExpect(jsonPath("$.details.fields.reference").value("Enter the cheque number or bank reference."));
        send(post("/factory-payments"), map("factoryId", factoryId, "period", "2026", "date", today.toString(), "amount", 10, "method", "CASH"))
                .andExpect(jsonPath("$.details.fields.period").value("Select the month."));
        pay.put("reference", "COM-1");
        Map<String, Object> paid = ok(send(post("/factory-payments"), pay), 201);
        assertThat((String) paid.get("fpNo")).matches("FP-\\d{4,}");
        assertThat(monthRow()).containsEntry("status", "PART_PAID");
        ok(send(post("/factory-payments"), map("factoryId", factoryId, "period", period, "date", today.toString(), "amount", 1400,
                "method", "CASH")), 201);
        m = monthRow();
        assertThat(m).containsEntry("status", "PAID").containsEntry("statusLabel", "Paid");
        assertThat(money(m.get("due"))).isEqualByComparingTo("0");
        f = getList("/factories", admin).stream().filter(x -> id(x) == factoryId).findFirst().orElseThrow();
        assertThat(money(f.get("owed"))).isEqualByComparingTo("0");
        assertThat(getList("/factory-payments", accountant)).filteredOn(p -> ((Map<?, ?>) p.get("factory")).get("id").equals((int) factoryId))
                .hasSize(2);
    }

    // ------------------------------------------------------------------ dispatch note and roles

    @Test
    void dispatchNotePrintsEveryBottleType() throws Exception {
        Map<String, Object> d = ok(dispatch(today, 25), 201);
        MvcResult pdf = mvc.perform(get("/print/dispatch-note/" + d.get("dispatchNo")).cookie(accountant)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf")).andReturn();
        PdfReader reader = new PdfReader(pdf.getResponse().getContentAsByteArray());
        String text = new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ");
        assertThat(text).contains("DISPATCH NOTE", (String) d.get("dispatchNo"), factoryName, "Empty " + bottleName,
                (String) batchOf(d).get("batchNo"), "Total bottles", "Vehicle: LH-4521", "Received by (factory)");
    }

    @Test
    void accountantCanViewButNotChange() throws Exception {
        Map<String, Object> d = ok(dispatch(today, 5), 201);
        long batchId = id(batchOf(d));
        for (String url : List.of("/production/summary", "/factory-batches", "/factory-batches/" + batchId, "/factory-dispatches/" + d.get("dispatchNo"),
                "/factory-balances", "/factory-payments")) {
            mvc.perform(get(url).cookie(accountant)).andExpect(status().isOk());
        }
        mvc.perform(body(post("/factory-dispatches"), accountant, map("factoryId", factoryId, "date", today.toString(), "lines", List.of())))
                .andExpect(status().isForbidden());
        mvc.perform(body(post("/factory-batches/" + batchId + "/returns"), accountant, map("date", today.toString(), "filled", 1)))
                .andExpect(status().isForbidden());
        mvc.perform(body(post("/factory-payments"), accountant, map("factoryId", factoryId, "date", today.toString())))
                .andExpect(status().isForbidden());
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        mvc.perform(get("/factory-batches").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/print/dispatch-note/" + d.get("dispatchNo")).cookie(staff)).andExpect(status().isForbidden());
    }
}
