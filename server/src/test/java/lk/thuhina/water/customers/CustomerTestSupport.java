package lk.thuhina.water.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.audit.model.AuditLog;
import lk.thuhina.water.audit.repository.AuditLogRepository;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Helpers for customer tests. Each test gets its own area, customer type and bottle type (sizes 600.xx) with standard
 * prices, because all tests share one database.
 */
public abstract class CustomerTestSupport extends IntegrationTest {

    private static final AtomicInteger SIZE = new AtomicInteger();
    private static final AtomicInteger PHONE = new AtomicInteger(1000);

    @Autowired
    protected AuditLogRepository auditLog;
    @Autowired
    protected Clock clock;

    protected Cookie admin;
    protected long areaId;
    protected long typeId;
    protected String bottle;
    protected LocalDate today;

    @BeforeEach
    protected void setUpData() throws Exception {
        admin = login(createUser(Role.ADMIN));
        today = BusinessDates.today(clock);
        long n = System.nanoTime();
        areaId = id(mvc.perform(json(post("/areas"), Map.of("name", "Area " + n))).andReturn());
        typeId = id(mvc.perform(json(post("/customer-types"), Map.of("name", "Type " + n))).andReturn());
        BigDecimal litres = new BigDecimal("600").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        bottle = (String) read(mvc.perform(json(post("/bottle-types"), Map.of("name", litres + "L C", "litres", litres))).andReturn()).get("code");
        // Standard water price 300 for this type and bottle since January; deposit 1000.
        jdbc.update("insert into price_entry (kind, bottle_type_code, customer_type_id, price, effective_from, reason) "
                + "values ('WATER', ?, ?, 300, DATE '2026-01-01', 'test')", bottle, typeId);
        jdbc.update("insert into price_entry (kind, bottle_type_code, price, effective_from, reason) "
                + "values ('DEPOSIT', ?, 1000, DATE '2026-01-01', 'test')", bottle);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) {
        return json(b, admin, body);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Cookie cookie, Object body) {
        return b.cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> read(MvcResult r) throws Exception {
        return json.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    @SuppressWarnings("unchecked")
    protected List<Map<String, Object>> readList(MvcResult r) throws Exception {
        return json.readValue(r.getResponse().getContentAsString(), List.class);
    }

    protected long id(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isBetween(200, 201);
        return ((Number) read(r).get("id")).longValue();
    }

    /** A unique valid Sri Lankan mobile number: 077 9xx xxxx. */
    protected static String newPhone() {
        int n = PHONE.incrementAndGet();
        return String.format("077 9%02d %04d", n / 10000 % 100, n % 10000);
    }

    /** A valid new-customer request: Monday delivery, first delivery after today, cash. */
    protected Map<String, Object> customer(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("address", "12, Test Road");
        m.put("areaId", areaId);
        m.put("phone", newPhone());
        m.put("customerTypeId", typeId);
        m.put("deliveryDay", 1);
        m.put("cycleWeeks", 1);
        m.put("nextDeliveryDate", ScheduleRules.firstDelivery(today, DayOfWeek.MONDAY).toString());
        m.put("usualQty", Map.of(bottle, 2));
        m.put("payType", "CASH");
        return m;
    }

    /** Registers a customer and returns its id. */
    protected long register(Map<String, Object> request) throws Exception {
        MvcResult r = mvc.perform(json(post("/customers/register"), request)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        @SuppressWarnings("unchecked")
        Map<String, Object> c = (Map<String, Object>) read(r).get("customer");
        return ((Number) c.get("id")).longValue();
    }

    protected AuditLog lastAudit(String entity, String ref) {
        List<AuditLog> rows = auditLog.findByEntityAndRefOrderByIdAsc(entity, ref);
        assertThat(rows).as("audit %s %s", entity, ref).isNotEmpty();
        return rows.get(rows.size() - 1);
    }
}
