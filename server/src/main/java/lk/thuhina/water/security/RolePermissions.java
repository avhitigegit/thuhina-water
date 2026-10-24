package lk.thuhina.water.security;

import static lk.thuhina.water.security.Permissions.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/** Role → permissions, exactly as the permission matrix in design 8.2. Roles are fixed (no role editing screen). */
public final class RolePermissions {

    private static final Map<Role, Set<String>> MAP = new EnumMap<>(Role.class);

    static {
        MAP.put(Role.ADMIN, Set.of(
                DASHBOARD_VIEW,
                MASTERDATA_VIEW, MASTERDATA_EDIT,
                CUSTOMERS_VIEW, CUSTOMERS_EDIT,
                CUSTOMER_PRICES_VIEW, CUSTOMER_PRICES_EDIT,
                PARTNERS_VIEW, PARTNERS_EDIT,
                PURCHASING_VIEW, PURCHASING_EDIT,
                PRODUCTION_VIEW, PRODUCTION_EDIT,
                STOCK_VIEW, STOCK_EDIT,
                SALES_VIEW, SALES_EDIT, SALES_APPROVE_OVER_LIMIT,
                DELIVERY_PLANNING_VIEW, DELIVERY_LIST_VIEW,
                BILLING_VIEW, BILLING_EDIT,
                EXPENSES_VIEW, EXPENSES_EDIT,
                QUOTATIONS_VIEW, QUOTATIONS_EDIT,
                REPORTS_VIEW, REPORTS_ALL,
                ADMIN_MANAGE,
                MIGRATION_MANAGE));

        MAP.put(Role.ACCOUNTANT, Set.of(
                DASHBOARD_VIEW,
                CUSTOMERS_VIEW,
                CUSTOMER_PRICES_VIEW,
                PARTNERS_VIEW,
                PURCHASING_VIEW,
                PRODUCTION_VIEW,
                BILLING_VIEW, BILLING_EDIT,
                EXPENSES_VIEW, EXPENSES_EDIT,
                QUOTATIONS_VIEW,
                REPORTS_VIEW));

        MAP.put(Role.DELIVERY_STAFF, Set.of(
                DELIVERY_LIST_VIEW));
    }

    private RolePermissions() {
    }

    public static Set<String> of(Role role) {
        return MAP.get(role);
    }
}
