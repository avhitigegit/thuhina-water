package lk.thuhina.water.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Roles &amp; access tab = design 8.2 (M01 S4). */
class RolesMatrixIntegrationTest extends IntegrationTest {

    /** area → "Admin | Accountant | Delivery Staff" cells, e.g. "FULL | VIEW | NONE". */
    private Map<String, String> matrix;
    private Map<String, List<Map<String, Object>>> cells;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void load() throws Exception {
        Cookie admin = login(createUser(Role.ADMIN));
        MvcResult r = mvc.perform(get("/roles/matrix").cookie(admin)).andExpect(status().isOk()).andReturn();
        Map<String, Object> body = json.readValue(r.getResponse().getContentAsString(), Map.class);
        assertThat((List<Map<String, Object>>) body.get("roles")).extracting(m -> m.get("roleName"))
                .containsExactly("Admin", "Accountant", "Delivery Staff");
        matrix = new LinkedHashMap<>();
        cells = new LinkedHashMap<>();
        for (Map<String, Object> row : (List<Map<String, Object>>) body.get("rows")) {
            List<Map<String, Object>> access = (List<Map<String, Object>>) row.get("access");
            matrix.put((String) row.get("area"), String.join(" | ", access.stream().map(c -> (String) c.get("level")).toList()));
            cells.put((String) row.get("area"), access);
        }
    }

    @Test
    void everyMenuPageAndSpecialRightMatchesDesign82() {
        assertThat(matrix).containsExactly(
                Map.entry("Dashboard", "FULL | VIEW | NONE"),
                Map.entry("Master Data › Bottles & Products", "FULL | NONE | NONE"),
                Map.entry("Master Data › Customers", "FULL | VIEW | NONE"),
                Map.entry("Master Data › Suppliers & Factories", "FULL | VIEW | NONE"),
                Map.entry("Purchasing", "FULL | VIEW | NONE"),
                Map.entry("Filling Factory", "FULL | VIEW | NONE"),
                Map.entry("Stock", "FULL | NONE | NONE"),
                Map.entry("Sales & Deliveries › Enter Bills", "FULL | NONE | NONE"),
                Map.entry("Sales & Deliveries › Sales & Bills", "FULL | NONE | NONE"),
                Map.entry("Sales & Deliveries › Customer Quotations", "FULL | VIEW | NONE"),
                Map.entry("Delivery Schedule › Delivery Planning", "FULL | NONE | NONE"),
                Map.entry("Delivery Schedule › Daily Delivery List", "FULL | NONE | VIEW"),
                Map.entry("Billing & Payments", "FULL | FULL | NONE"),
                Map.entry("Expenses", "FULL | FULL | NONE"),
                Map.entry("Data Migration", "FULL | NONE | NONE"),
                Map.entry("Reports", "FULL | VIEW | NONE"),
                Map.entry("Administration", "FULL | NONE | NONE"),
                Map.entry("Customer agreed prices", "FULL | VIEW | NONE"),
                Map.entry("Approve sale over credit limit", "FULL | NONE | NONE"));
    }

    @Test
    void notesAsInTheDesign() {
        assertThat(note("Reports", 0)).isEqualTo("All 10 reports");
        assertThat(note("Reports", 1)).isEqualTo("Sales, Purchasing, Outstanding & aging, Profit, Expenses");
        assertThat(note("Delivery Schedule › Daily Delivery List", 2)).isEqualTo("View + print");
        assertThat(note("Delivery Schedule › Daily Delivery List", 0)).isNull();
        assertThat(note("Sales & Deliveries › Customer Quotations", 1)).isEqualTo("View + print");
        assertThat(note("Data Migration", 0)).isEqualTo("When switched on");
        assertThat(note("Data Migration", 1)).isNull();
    }

    private String note(String area, int role) {
        return (String) cells.get(area).get(role).get("note");
    }
}
