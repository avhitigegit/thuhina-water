package lk.thuhina.water.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** Landing page, menu per role and permission checks (design 8.2). */
class MenuAndPermissionIntegrationTest extends IntegrationTest {

    @Test
    void adminSeesEveryModuleAndDataMigrationIsHiddenBySetting() throws Exception {
        Cookie cookie = login(createUser(Role.ADMIN));
        mvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingPage").value("/dashboard"))
                .andExpect(jsonPath("$.menu[*].module").value(contains("Dashboard", "Master Data", "Purchasing", "Production",
                        "Inventory", "Sales & Deliveries", "Delivery Schedule", "Billing & Payments", "Expenses",
                        "Data Migration", "Reports", "Administration")))
                .andExpect(jsonPath("$.menu[?(@.module == 'Data Migration')].hidden").value(contains(true)))
                .andExpect(jsonPath("$.menu[?(@.module == 'Master Data')].items[*].view").value(contains(false, false, false)));
    }

    @Test
    void accountantGetsTheReducedMenuWithViewOnlyPages() throws Exception {
        Cookie cookie = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingPage").value("/dashboard"))
                .andExpect(jsonPath("$.menu[*].module").value(contains("Dashboard", "Master Data", "Purchasing", "Production",
                        "Sales & Deliveries", "Billing & Payments", "Expenses", "Reports")))
                .andExpect(jsonPath("$.menu[?(@.module == 'Master Data')].items[*].key").value(contains("customers", "suppliers")))
                .andExpect(jsonPath("$.menu[?(@.module == 'Master Data')].items[*].view").value(contains(true, true)))
                .andExpect(jsonPath("$.menu[?(@.module == 'Sales & Deliveries')].items[*].key").value(contains("quotations")))
                .andExpect(jsonPath("$.menu[?(@.module == 'Billing & Payments')].items[0].view").value(contains(false)))
                .andExpect(jsonPath("$.permissions").value(not(hasItem(Permissions.STOCK_VIEW))));
    }

    @Test
    void deliveryStaffOnlyGetsTheDailyDeliveryList() throws Exception {
        Cookie cookie = login(createUser(Role.DELIVERY_STAFF));
        mvc.perform(get("/auth/me").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.landingPage").value("/delivery/daily-list"))
                .andExpect(jsonPath("$.menu.length()").value(1))
                .andExpect(jsonPath("$.menu[0].items[*].path").value(contains("/delivery/daily-list")))
                .andExpect(jsonPath("$.permissions").value(contains(Permissions.DELIVERY_LIST_VIEW)));
    }

    @Test
    void endpointWithoutPermissionGives403InTheStandardFormat() throws Exception {
        for (Role role : new Role[] {Role.ACCOUNTANT, Role.DELIVERY_STAFF}) {
            Cookie cookie = login(createUser(role));
            mvc.perform(get("/settings/company").cookie(cookie))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                    .andExpect(jsonPath("$.message").value("You do not have permission to do this."));
            mvc.perform(put("/settings/company").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                            .content(body(Map.of("phone", "011 000 0000"))))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void unknownPathGives404ForLoggedInUser() throws Exception {
        Cookie cookie = login(createUser(Role.ADMIN));
        mvc.perform(get("/no-such-endpoint").cookie(cookie))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
