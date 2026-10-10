package lk.thuhina.water.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Helpers for the master data tests. All tests share one database, so each test makes its own bottle sizes
 * (unique per run) and customer type names.
 */
abstract class MasterDataTestSupport extends IntegrationTest {

    /** Sizes 200.01, 200.02 … – never used by the seed or by another test. */
    private static final AtomicInteger SIZE = new AtomicInteger();

    @Autowired
    protected AuditLogRepository auditLog;
    @Autowired
    protected Clock clock;

    protected Cookie admin;

    @BeforeEach
    void loginAdmin() throws Exception {
        admin = login(createUser(Role.ADMIN));
    }

    protected LocalDate today() {
        return BusinessDates.today(clock);
    }

    protected static BigDecimal uniqueLitres() {
        int n = SIZE.incrementAndGet();
        return new BigDecimal("200").add(BigDecimal.valueOf(n, 2));
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

    /** Creates an active bottle type of a new size; returns its code. */
    protected String newBottle() throws Exception {
        BigDecimal litres = uniqueLitres();
        MvcResult r = mvc.perform(json(post("/bottle-types"), admin, Map.of("name", litres + "L Test", "litres", litres))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return (String) read(r).get("code");
    }

    /** Creates an active customer type with a new name; returns its id. */
    protected long newCustomerType() throws Exception {
        MvcResult r = mvc.perform(json(post("/customer-types"), admin,
                Map.of("name", "Type " + System.nanoTime(), "description", "test"))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return ((Number) read(r).get("id")).longValue();
    }

    protected AuditLog lastAudit(String entity, String ref) {
        List<AuditLog> rows = auditLog.findByEntityAndRefOrderByIdAsc(entity, ref);
        assertThat(rows).as("audit for %s %s", entity, ref).isNotEmpty();
        return rows.get(rows.size() - 1);
    }
}
