package lk.thuhina.water.partners;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.audit.model.AuditLog;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.partners.service.FactoryService;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Suppliers and filling factories (M03 S5). */
class PartnersIntegrationTest extends IntegrationTest {

    /** Bottle sizes 300.01, 300.02 … – not used by any other test class. */
    private static final AtomicInteger SIZE = new AtomicInteger();

    @Autowired
    AuditLogRepository auditLog;
    @Autowired
    FactoryService factories;
    @Autowired
    Clock clock;

    private Cookie admin;
    private String bottle;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(createUser(Role.ADMIN));
        BigDecimal litres = new BigDecimal("300").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        bottle = (String) read(mvc.perform(json(post("/bottle-types"), admin, Map.of("name", litres + "L P", "litres", litres)))
                .andExpect(status().isCreated()).andReturn()).get("code");
    }

    // ------------------------------------------------------------------ helpers

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Cookie cookie, Object body) {
        return b.cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> read(MvcResult r) throws Exception {
        return json.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readList(MvcResult r) throws Exception {
        return json.readValue(r.getResponse().getContentAsString(), List.class);
    }

    private Map<String, Object> supplier(List<String> items, Object terms) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Bottle Maker " + System.nanoTime());
        m.put("contact", "Mr. Test");
        m.put("phone", "011 222 3333");
        m.put("email", "sales@maker.lk");
        m.put("address", "Colombo");
        m.put("termsDays", terms);
        m.put("items", items);
        return m;
    }

    /** A charge for every active bottle type (other tests add bottle types), {@code ours} for this test's bottle. */
    private Map<String, Object> factory(int ours) throws Exception {
        Map<String, Object> charges = new LinkedHashMap<>();
        for (Map<String, Object> b : readList(mvc.perform(get("/bottle-types?activeOnly=true").cookie(admin)).andReturn())) {
            charges.put((String) b.get("code"), b.get("code").equals(bottle) ? ours : 50);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("name", "Third Filler " + System.nanoTime());
        m.put("address", "Kandy Road");
        m.put("licence", "SLS 614");
        m.put("termsDays", 30);
        m.put("charges", charges);
        return m;
    }

    private AuditLog lastAudit(String entity, String ref) {
        List<AuditLog> rows = auditLog.findByEntityAndRefOrderByIdAsc(entity, ref);
        assertThat(rows).isNotEmpty();
        return rows.get(rows.size() - 1);
    }

    // ------------------------------------------------------------------ suppliers

    @Test
    void supplierNeedsNameAPhoneAnItemAndTermsFromTheList() throws Exception {
        Map<String, Object> s = supplier(List.of(), 30);
        s.put("name", " ");
        s.put("phone", "");
        mvc.perform(json(post("/suppliers"), admin, s))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Supplier name and phone are required."))
                .andExpect(jsonPath("$.details.fields.phone").value("Supplier name and phone are required."));
        mvc.perform(json(post("/suppliers"), admin, supplier(List.of(), 30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.items").value("Select at least one item the supplier supplies."));
        mvc.perform(json(post("/suppliers"), admin, supplier(List.of(bottle), 21)))
                .andExpect(jsonPath("$.details.fields.termsDays").value("Choose the payment terms from the list."));
        mvc.perform(json(post("/suppliers"), admin, supplier(List.of(bottle), null)))
                .andExpect(jsonPath("$.details.fields.termsDays").value("Choose the payment terms from the list."));
        mvc.perform(json(post("/suppliers"), admin, supplier(List.of("B999"), 30)))
                .andExpect(jsonPath("$.details.fields.items").value("Unknown item B999."));
        Map<String, Object> badEmail = supplier(List.of(bottle), 30);
        badEmail.put("email", "sales-at-maker");
        mvc.perform(json(post("/suppliers"), admin, badEmail))
                .andExpect(jsonPath("$.details.fields.email").value("Enter a valid email address."));
    }

    @Test
    void supplierIsSavedWithItemsTermsAndAudit() throws Exception {
        MvcResult r = mvc.perform(json(post("/suppliers"), admin, supplier(List.of(bottle), 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("S\\d{2,}")))
                .andExpect(jsonPath("$.termsLabel").value("Cash on delivery"))
                .andExpect(jsonPath("$.items[0].code").value(bottle))
                .andExpect(jsonPath("$.items[0].type").value("BOTTLE"))
                .andExpect(jsonPath("$.items[0].name").value(org.hamcrest.Matchers.startsWith("Empty ")))
                .andReturn();
        Map<String, Object> s = read(r);
        String code = (String) s.get("code");
        assertThat(lastAudit("Supplier", code).getDetails()).contains(" – Empty ").endsWith(", Cash on delivery");

        // Suppliers of an item (used to tick suppliers on a quotation request).
        List<Map<String, Object>> ofItem = readList(mvc.perform(get("/suppliers?item=" + bottle).cookie(admin)).andReturn());
        assertThat(ofItem).extracting(x -> x.get("code")).containsExactly(code);

        // Change terms and deactivate; inactive suppliers are not offered for the item any more.
        Map<String, Object> edit = supplier(List.of(bottle), 45);
        edit.put("name", s.get("name"));
        edit.put("active", false);
        edit.put("version", s.get("version"));
        mvc.perform(json(put("/suppliers/" + s.get("id")), admin, edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsLabel").value("45 days"))
                .andExpect(jsonPath("$.active").value(false));
        assertThat(lastAudit("Supplier", code).getDetails()).contains("terms Cash on delivery → 45 days; Inactive");
        assertThat(readList(mvc.perform(get("/suppliers?item=" + bottle).cookie(admin)).andReturn())).isEmpty();
    }

    @Test
    void anItemMadeInactiveCanStayButCannotBeAddedNew() throws Exception {
        Map<String, Object> s = read(mvc.perform(json(post("/suppliers"), admin, supplier(List.of(bottle), 30))).andReturn());
        mvc.perform(json(put("/bottle-types/" + bottle), admin, Map.of("name", "Old size", "active", false))).andExpect(status().isOk());

        Map<String, Object> edit = supplier(List.of(bottle), 14);
        edit.put("name", s.get("name"));
        mvc.perform(json(put("/suppliers/" + s.get("id")), admin, edit)).andExpect(status().isOk());

        mvc.perform(json(post("/suppliers"), admin, supplier(List.of(bottle), 30)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.items").value("Empty Old size is not active."));
    }

    // ------------------------------------------------------------------ factories

    @Test
    void aThirdFactoryCanBeAddedWithItsOwnCharges() throws Exception {
        mvc.perform(json(post("/factories"), admin, factory(55)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("F\\d{2,}")))
                .andExpect(jsonPath("$.termsLabel").value("30 days"))
                .andExpect(jsonPath("$.charges[?(@.bottleTypeCode == '" + bottle + "')].charge").value(org.hamcrest.Matchers.contains(55.0)));
    }

    @Test
    void factoryNeedsANameAndAChargeForEveryActiveBottleType() throws Exception {
        Map<String, Object> f = factory(0);
        f.put("name", "");
        mvc.perform(json(post("/factories"), admin, f))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Enter the factory name."));

        f = factory(0);
        String label = bottle.substring(1).replace('_', '.') + "L";
        mvc.perform(json(post("/factories"), admin, f))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields['charges." + bottle + "']").value("Enter the charge per " + label + " bottle (more than 0)."));

        f = factory(40);
        f.put("termsDays", 90);
        mvc.perform(json(post("/factories"), admin, f))
                .andExpect(jsonPath("$.details.fields.termsDays").value("Choose the payment terms from the list."));
    }

    @Test
    void aChargeChangeIsSavedFromTodayAndHistoryIsKept() throws Exception {
        Map<String, Object> created = read(mvc.perform(json(post("/factories"), admin, factory(60))).andExpect(status().isCreated()).andReturn());
        long id = ((Number) created.get("id")).longValue();
        LocalDate today = BusinessDates.today(clock);
        // An older charge from last month.
        jdbc.update("insert into factory_charge (factory_id, bottle_type_code, charge, effective_from, created_by) values (?, ?, 58, ?, 'test')",
                id, bottle, today.minusDays(30));

        Map<String, Object> edit = factory(65);
        edit.put("name", created.get("name"));
        edit.put("version", created.get("version"));
        mvc.perform(json(put("/factories/" + id), admin, edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.charges[?(@.bottleTypeCode == '" + bottle + "')].charge").value(org.hamcrest.Matchers.contains(65.0)));
        assertThat(lastAudit("Filling factory", (String) created.get("code")).getDetails())
                .contains("charge " + bottle.substring(1).replace('_', '.') + "L Rs. 60.00 → Rs. 65.00");

        // The charge in force: last month 58, today 65 (the later of the two saved today).
        assertThat(factories.currentCharge(id, bottle, today.minusDays(1))).contains(new BigDecimal("58.00"));
        assertThat(factories.currentCharge(id, bottle, today)).contains(new BigDecimal("65.00"));
        assertThat(factories.currentCharge(id, bottle, today.minusDays(31))).isEmpty();

        List<Map<String, Object>> history = readList(mvc.perform(get("/factories/" + id + "/charges").cookie(admin)).andReturn())
                .stream().filter(c -> bottle.equals(c.get("bottleTypeCode"))).toList();
        assertThat(history).extracting(c -> c.get("charge") + " " + c.get("current"))
                .containsExactly("65.0 true", "60.0 false", "58.0 false");

        assertThatThrownBy(() -> jdbc.update("update factory_charge set charge = 1 where factory_id = ?", id))
                .hasMessageContaining("insert-only");
    }

    // ------------------------------------------------------------------ access

    @Test
    void accountantSeesBothListsButCannotChangeThem() throws Exception {
        Map<String, Object> s = read(mvc.perform(json(post("/suppliers"), admin, supplier(List.of(bottle), 30))).andReturn());
        Map<String, Object> f = read(mvc.perform(json(post("/factories"), admin, factory(60))).andReturn());

        Cookie acc = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/suppliers").cookie(acc)).andExpect(status().isOk());
        mvc.perform(get("/factories").cookie(acc)).andExpect(status().isOk());
        mvc.perform(get("/factories/" + f.get("id") + "/charges").cookie(acc)).andExpect(status().isOk());
        mvc.perform(json(post("/suppliers"), acc, supplier(List.of(bottle), 30))).andExpect(status().isForbidden());
        mvc.perform(json(put("/suppliers/" + s.get("id")), acc, supplier(List.of(bottle), 30))).andExpect(status().isForbidden());
        mvc.perform(json(post("/factories"), acc, factory(60))).andExpect(status().isForbidden());
        mvc.perform(json(put("/factories/" + f.get("id")), acc, factory(60))).andExpect(status().isForbidden());

        Cookie ds = login(createUser(Role.DELIVERY_STAFF));
        mvc.perform(get("/suppliers").cookie(ds)).andExpect(status().isForbidden());
        mvc.perform(get("/factories").cookie(ds)).andExpect(status().isForbidden());
    }
}
