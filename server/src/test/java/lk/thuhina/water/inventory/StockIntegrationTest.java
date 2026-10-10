package lk.thuhina.water.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.audit.model.AuditLog;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Stock page API (M04): damage, adjustments, minimum levels, alerts, movements, access. */
class StockIntegrationTest extends IntegrationTest {

    /** Bottle sizes 500.01, 500.02 … – only this class uses them. */
    private static final AtomicInteger SIZE = new AtomicInteger();

    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    Clock clock;

    private Cookie admin;
    private String bottle;
    private String label;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(createUser(Role.ADMIN));
        today = BusinessDates.today(clock);
        BigDecimal litres = new BigDecimal("500").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        label = litres.stripTrailingZeros().toPlainString() + "L";
        bottle = (String) read(mvc.perform(json(post("/bottle-types"), Map.of("name", label + " Stock", "litres", litres)))
                .andExpect(status().isCreated()).andReturn()).get("code");
        // Start with 10 filled and 4 empty, by physical counts.
        count("FILLED", 10);
        count("EMPTY", 4);
    }

    // ------------------------------------------------------------------ helpers

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) {
        return b.cookie(admin).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(MvcResult r) throws Exception {
        return json.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    private void count(String bucket, int counted) throws Exception {
        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", bottle, "mode", "COUNT",
                "bucket", bucket, "counted", counted, "reason", "Opening count"))).andExpect(status().isCreated());
    }

    private Map<String, Object> damage(int qty, String location) {
        Map<String, Object> m = new HashMap<>();
        m.put("date", today.toString());
        m.put("bottleTypeCode", bottle);
        m.put("qty", qty);
        m.put("location", location);
        m.put("reason", "Dropped while loading");
        return m;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> myBottle() throws Exception {
        List<Map<String, Object>> bottles = (List<Map<String, Object>>) read(mvc.perform(get("/stock").cookie(admin)).andReturn()).get("bottles");
        return bottles.stream().filter(b -> bottle.equals(b.get("code"))).findFirst().orElseThrow();
    }

    private AuditLog lastAudit(String entity, String ref) {
        List<AuditLog> rows = auditLog.findByEntityAndRefOrderByIdAsc(entity, ref);
        assertThat(rows).isNotEmpty();
        return rows.get(rows.size() - 1);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void recording999DamagedFilledBottlesIsBlockedAndNothingChanges() throws Exception {
        mvc.perform(json(post("/stock/damage"), damage(999, "FILLED_IN_STORE")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"))
                .andExpect(jsonPath("$.message").value("Filled in store (" + label
                        + ") cannot go below zero – available 10, needed 999. Record a stock adjustment first."));
        assertThat(myBottle()).containsEntry("filled", 10).containsEntry("writtenOff", 0);
        assertThat(jdbc.queryForObject("select count(*) from damage_record where bottle_type_code = ?", Integer.class, bottle)).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void companyDamageIsWrittenOffAndListedWithMovements() throws Exception {
        MvcResult r = mvc.perform(json(post("/stock/damage"), damage(3, "DURING_DELIVERY")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ref").value(matchesPattern("DMG-\\d{4,}")))
                .andExpect(jsonPath("$.where").value("During delivery"))
                .andReturn();
        String ref = (String) read(r).get("ref");
        assertThat(myBottle()).containsEntry("filled", 7).containsEntry("writtenOff", 3).containsEntry("inCirculation", 11);
        assertThat(lastAudit("Damage (company)", ref).getDetails())
                .isEqualTo("3 × " + label + " During delivery – Dropped while loading. Written off at company cost.");

        List<Map<String, Object>> list = json.readValue(mvc.perform(get("/stock/damages").cookie(admin)).andReturn()
                .getResponse().getContentAsString(), List.class);
        assertThat(list).anySatisfy(d -> assertThat(d).containsEntry("ref", ref).containsEntry("kind", "DAMAGED")
                .containsEntry("responsibility", "COMPANY").containsEntry("qty", 3));

        Map<String, Object> moves = read(mvc.perform(get("/stock/movements?item=" + bottle).cookie(admin)).andReturn());
        Map<String, Object> top = ((List<Map<String, Object>>) moves.get("items")).get(0);
        assertThat(top).containsEntry("docNo", ref).containsEntry("filled", -3).containsEntry("writtenOff", 3)
                .containsEntry("description", "Company damage – written off (During delivery)")
                .containsEntry("sourceType", "DAMAGE");
        assertThat(top.get("createdBy")).isNotNull();
        assertThat(moves.get("total")).isEqualTo(3); // two opening counts + the damage
    }

    @Test
    void physicalCountSetsTheBucketAndLostBottlesAreWrittenOff() throws Exception {
        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", bottle, "mode", "COUNT",
                        "bucket", "FILLED", "counted", 8, "reason", "Month-end count")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.systemQty").value(10))
                .andExpect(jsonPath("$.delta").value(-2))
                .andExpect(jsonPath("$.adjNo").value(matchesPattern("ADJ-\\d{4,}")));
        assertThat(myBottle()).containsEntry("filled", 8);

        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", bottle, "mode", "LOST",
                        "bucket", "EMPTY", "qty", 3, "reason", "Not returned by driver")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").value("3 × " + label + " lost from Empty in store – written off"));
        assertThat(myBottle()).containsEntry("empty", 1).containsEntry("writtenOff", 3);

        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", bottle, "mode", "COUNT",
                        "bucket", "FILLED", "counted", 8, "reason", "Recount")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("NO_CHANGE"))
                .andExpect(jsonPath("$.message").value("Counted quantity matches the system. No adjustment needed."));
        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", bottle, "mode", "LOST",
                        "bucket", "EMPTY", "qty", 5, "reason", "x")))
                .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"));
        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", bottle, "mode", "COUNT",
                        "bucket", "CUSTOMERS", "counted", 1, "reason", "x")))
                .andExpect(jsonPath("$.details.fields.bucket").value("Select the stock status to adjust."));
        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.plusDays(1).toString(), "itemCode", bottle, "mode", "COUNT",
                        "bucket", "FILLED", "counted", 1, "reason", "x")))
                .andExpect(jsonPath("$.details.fields.date").value("Date cannot be after today (" + BusinessDates.format(today) + ")."));
    }

    @Test
    void productCountAndOpeningStockShowInMovements() throws Exception {
        MvcResult p = mvc.perform(json(post("/products"), Map.of("name", "Stock Pump", "sellingPrice", 500, "costPrice", 300,
                "openingStock", 6))).andExpect(status().isCreated()).andExpect(jsonPath("$.stockQty").value(6)).andReturn();
        String code = (String) read(p).get("code");

        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", code, "mode", "COUNT",
                        "counted", 4, "reason", "Count")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bucket").value("STOCK"))
                .andExpect(jsonPath("$.description").value("Stock Pump: system 6 → counted 4"));
        mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", code, "mode", "LOST",
                        "qty", 1, "reason", "x")))
                .andExpect(jsonPath("$.details.fields.mode").value("Products are adjusted by a physical count."));

        mvc.perform(get("/stock/movements?item=" + code).cookie(admin))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].product").value(-2))
                .andExpect(jsonPath("$.items[1].product").value(6))
                .andExpect(jsonPath("$.items[1].description").value("Opening stock – new product"));
        mvc.perform(get("/stock").cookie(admin))
                .andExpect(jsonPath("$.products[?(@.code == '" + code + "')].costValue").value(org.hamcrest.Matchers.contains(1200.0)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void minimumLevelDrivesTheLowStockAlert() throws Exception {
        mvc.perform(json(put("/stock/min-levels/" + bottle), Map.of("minFilled", 15)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.minFilled").value(15));
        assertThat(lastAudit("Minimum stock level", bottle).getDetails()).isEqualTo("Filled " + label + ": – → 15");
        assertThat(myBottle()).containsEntry("minFilled", 15).containsEntry("low", true);

        List<Map<String, Object>> alerts = json.readValue(mvc.perform(get("/stock/alerts").cookie(admin)).andReturn()
                .getResponse().getContentAsString(), List.class);
        assertThat(alerts).anySatisfy(a -> assertThat(a).containsEntry("bottleTypeCode", bottle)
                .containsEntry("message", "Low stock: only 10 filled " + label + " bottles in store (minimum 15). 0 are at the factory."));

        mvc.perform(json(put("/stock/min-levels/" + bottle), Map.of("minFilled", 10))).andExpect(status().isOk());
        assertThat(lastAudit("Minimum stock level", bottle).getDetails()).isEqualTo("Filled " + label + ": 15 → 10");
        assertThat(myBottle()).containsEntry("low", false);
        mvc.perform(json(put("/stock/min-levels/" + bottle), Map.of("minFilled", -1)))
                .andExpect(jsonPath("$.details.fields.minFilled").value("Minimum level cannot be negative."));
    }

    @Test
    void damageValidation() throws Exception {
        Map<String, Object> d = damage(0, "AT_FACTORY");
        mvc.perform(json(post("/stock/damage"), d))
                .andExpect(jsonPath("$.details.fields.qty").value("Enter the number of damaged bottles."));
        d = damage(1, null);
        d.put("reason", " ");
        mvc.perform(json(post("/stock/damage"), d))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.location").value("Select where the damage happened."))
                .andExpect(jsonPath("$.details.fields.reason").value("Enter the reason."));
    }

    @Test
    void onlyAdminUsesTheStockPage() throws Exception {
        for (Role role : new Role[] {Role.ACCOUNTANT, Role.DELIVERY_STAFF}) {
            Cookie cookie = login(createUser(role));
            mvc.perform(get("/stock").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/stock/movements").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(post("/stock/damage").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                    .content(body(damage(1, "AT_FACTORY")))).andExpect(status().isForbidden());
        }
    }
}
