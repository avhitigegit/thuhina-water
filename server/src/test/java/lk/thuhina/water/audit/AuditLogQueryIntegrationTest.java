package lk.thuhina.water.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Audit log tab search (M01 S3): filters, newest first, paging. */
class AuditLogQueryIntegrationTest extends IntegrationTest {

    private AppUser admin;
    private Cookie cookie;
    private String marker;
    private LocalDate today;

    @BeforeEach
    void setUp() throws Exception {
        admin = createUser(Role.ADMIN);
        cookie = login(admin);
        marker = "mk" + System.nanoTime();
        today = LocalDate.now(BusinessDates.ZONE);
        // Rows of this admin: two 10 days ago (one late in the evening), then a reversal and an update just before
        // the login of this test (which is the newest row).
        ZonedDateTime now = ZonedDateTime.now(BusinessDates.ZONE);
        insert(ZonedDateTime.of(today.minusDays(10), LocalTime.of(9, 0), BusinessDates.ZONE), "CREATE", "Customer", "C9001", "Registered " + marker + " one");
        insert(ZonedDateTime.of(today.minusDays(10), LocalTime.of(23, 30), BusinessDates.ZONE), "UPDATE", "Customer", "C9001", "Edited " + marker + " 50% off");
        insert(now.minusMinutes(2), "REVERSE", "Bill", "B009999", "Reversed " + marker);
        insert(now.minusMinutes(1), "UPDATE", "Bill", "B009998", "Note " + marker);
    }

    private void insert(ZonedDateTime ts, String action, String entity, String ref, String details) {
        jdbc.update("insert into audit_log (ts, user_id, username, role, action, entity, ref, details) values (?,?,?,?,?,?,?,?)",
                Timestamp.from(ts.toInstant()), admin.getId(), admin.getUsername(), "ADMIN", action, entity, ref, details);
    }

    private List<Map<String, Object>> items(String query) throws Exception {
        MvcResult r = mvc.perform(get("/audit-log?" + query).cookie(cookie)).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) json.readValue(r.getResponse().getContentAsString(), Map.class).get("items");
        return items;
    }

    @Test
    void filtersByUserNewestFirstWithNameAndRole() throws Exception {
        List<Map<String, Object>> rows = items("user=" + admin.getUsername() + "&size=200");
        assertThat(rows).extracting(r -> r.get("username")).containsOnly(admin.getUsername());
        // The LOGIN of setUp is the newest, then the two recent rows, then the older ones.
        assertThat(rows).extracting(r -> r.get("action")).containsExactly("LOGIN", "UPDATE", "REVERSE", "UPDATE", "CREATE");
        assertThat(rows.get(0)).containsEntry("fullName", admin.getFullName()).containsEntry("roleName", "Admin");
    }

    @Test
    void dateRangeIsInclusiveOfTheWholeToDay() throws Exception {
        String day = today.minusDays(10).toString();
        assertThat(items("user=" + admin.getUsername() + "&from=" + day + "&to=" + day))
                .extracting(r -> r.get("ref")).containsExactly("C9001", "C9001");
        assertThat(items("user=" + admin.getUsername() + "&from=" + today.minusDays(7)))
                .extracting(r -> r.get("action")).doesNotContain("CREATE");
    }

    @Test
    void actionRecordTypeAndTextSearch() throws Exception {
        assertThat(items("user=" + admin.getUsername() + "&action=REVERSE"))
                .singleElement().satisfies(r -> assertThat(r).containsEntry("ref", "B009999").containsEntry("entity", "Bill"));
        assertThat(items("user=" + admin.getUsername() + "&entity=Customer")).hasSize(2);
        // Text search: details, ref and record type, not case-sensitive.
        assertThat(items("q=" + marker.toUpperCase())).hasSize(4);
        assertThat(items("q=b009998")).extracting(r -> r.get("ref")).contains("B009998");
        // % and _ are plain characters in the search.
        assertThat(search(marker + " 50%")).singleElement()
                .satisfies(r -> assertThat((String) r.get("details")).contains("50% off"));
        assertThat(search(marker + "%")).isEmpty();
        assertThat(search(marker + "_")).isEmpty();
    }

    /** Text search with the value sent as a request parameter (no URL encoding by hand). */
    private List<Map<String, Object>> search(String q) throws Exception {
        MvcResult r = mvc.perform(get("/audit-log").param("q", q).cookie(cookie)).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) json.readValue(r.getResponse().getContentAsString(), Map.class).get("items");
        return items;
    }

    @Test
    void paging() throws Exception {
        mvc.perform(get("/audit-log?user=" + admin.getUsername() + "&page=1&size=2").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].action").value("REVERSE"));
        mvc.perform(get("/audit-log?size=100000").cookie(cookie))
                .andExpect(jsonPath("$.size").value(200));
    }

    @Test
    void toBeforeFromIsRefused() throws Exception {
        mvc.perform(get("/audit-log?from=" + today + "&to=" + today.minusDays(1)).cookie(cookie))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.to").value("'To' must be on or after 'From'."));
        mvc.perform(get("/audit-log?action=DELETE").cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    @Test
    void filterOptionsListUsersActionsAndRecordTypes() throws Exception {
        mvc.perform(get("/audit-log/filters").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users[?(@.username == '" + admin.getUsername() + "')].fullName").value(hasItem(admin.getFullName())))
                .andExpect(jsonPath("$.actions").value(org.hamcrest.Matchers.contains(
                        "CREATE", "UPDATE", "APPROVE", "REVERSE", "IMPORT", "LOGIN", "LOGOUT")))
                .andExpect(jsonPath("$.entities").value(hasItem("Customer")))
                .andExpect(jsonPath("$.entities").value(hasItem("Session")));
    }
}
