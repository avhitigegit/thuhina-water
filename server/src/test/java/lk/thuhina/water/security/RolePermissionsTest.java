package lk.thuhina.water.security;

import static lk.thuhina.water.security.Permissions.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/** The permission matrix of design 8.2. */
class RolePermissionsTest {

    @Test
    void adminHasEveryPermission() throws IllegalAccessException {
        Set<String> all = new HashSet<>();
        for (Field f : Permissions.class.getFields()) {
            all.add((String) f.get(null));
        }
        assertThat(RolePermissions.of(Role.ADMIN)).containsExactlyInAnyOrderElementsOf(all);
    }

    @Test
    void accountantMatrix() {
        Set<String> acc = RolePermissions.of(Role.ACCOUNTANT);
        // Full
        assertThat(acc).contains(BILLING_VIEW, BILLING_EDIT, EXPENSES_VIEW, EXPENSES_EDIT, REPORTS_VIEW, DASHBOARD_VIEW);
        // View only
        assertThat(acc).contains(CUSTOMERS_VIEW, CUSTOMER_PRICES_VIEW, PARTNERS_VIEW, PURCHASING_VIEW, PRODUCTION_VIEW, QUOTATIONS_VIEW)
                .doesNotContain(CUSTOMERS_EDIT, CUSTOMER_PRICES_EDIT, PARTNERS_EDIT, PURCHASING_EDIT, PRODUCTION_EDIT, QUOTATIONS_EDIT);
        // None
        assertThat(acc).doesNotContain(MASTERDATA_VIEW, STOCK_VIEW, SALES_VIEW, DELIVERY_PLANNING_VIEW, DELIVERY_LIST_VIEW,
                ADMIN_MANAGE, MIGRATION_MANAGE, REPORTS_ALL, SALES_APPROVE_OVER_LIMIT);
    }

    @Test
    void deliveryStaffOnlySeesTheDailyList() {
        assertThat(RolePermissions.of(Role.DELIVERY_STAFF)).containsExactly(DELIVERY_LIST_VIEW);
    }

    @Test
    void landingPages() {
        assertThat(Role.ADMIN.landingPage()).isEqualTo("/dashboard");
        assertThat(Role.ACCOUNTANT.landingPage()).isEqualTo("/dashboard");
        assertThat(Role.DELIVERY_STAFF.landingPage()).isEqualTo("/delivery/daily-list");
    }
}
