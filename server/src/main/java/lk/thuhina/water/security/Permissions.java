package lk.thuhina.water.security;

/**
 * Permission strings (design 8.2). Endpoints use them with {@code @PreAuthorize("hasAuthority(...)")},
 * e.g. {@code @PreAuthorize("hasAuthority('" + Permissions.CUSTOMERS_EDIT + "')")}.
 * "view" = read and print; "edit" = everything else on that page. Roles get them in {@link RolePermissions}.
 */
public final class Permissions {

    public static final String DASHBOARD_VIEW = "dashboard.view";

    /** Bottles & Products: bottle types, products, standard prices and deposits, old-bottle brands. */
    public static final String MASTERDATA_VIEW = "masterdata.view";
    public static final String MASTERDATA_EDIT = "masterdata.edit";

    public static final String CUSTOMERS_VIEW = "customers.view";
    public static final String CUSTOMERS_EDIT = "customers.edit";

    /** Customer agreed prices: Admin sets / changes, Accountant views. */
    public static final String CUSTOMER_PRICES_VIEW = "customer-prices.view";
    public static final String CUSTOMER_PRICES_EDIT = "customer-prices.edit";

    /** Suppliers & Factories. */
    public static final String PARTNERS_VIEW = "partners.view";
    public static final String PARTNERS_EDIT = "partners.edit";

    public static final String PURCHASING_VIEW = "purchasing.view";
    public static final String PURCHASING_EDIT = "purchasing.edit";

    /** Filling Factory. */
    public static final String PRODUCTION_VIEW = "production.view";
    public static final String PRODUCTION_EDIT = "production.edit";

    public static final String STOCK_VIEW = "stock.view";
    public static final String STOCK_EDIT = "stock.edit";

    /** Enter Bills and Sales & Bills. */
    public static final String SALES_VIEW = "sales.view";
    public static final String SALES_EDIT = "sales.edit";
    /** Approve a sale over the customer's credit limit (Admin only, BR-09). */
    public static final String SALES_APPROVE_OVER_LIMIT = "sales.approve-over-limit";

    public static final String DELIVERY_PLANNING_VIEW = "delivery-planning.view";
    /** Daily Delivery List – view and print. */
    public static final String DELIVERY_LIST_VIEW = "delivery-list.view";

    public static final String BILLING_VIEW = "billing.view";
    public static final String BILLING_EDIT = "billing.edit";

    public static final String EXPENSES_VIEW = "expenses.view";
    public static final String EXPENSES_EDIT = "expenses.edit";

    /** Customer Quotations: Admin full, Accountant view + print. */
    public static final String QUOTATIONS_VIEW = "quotations.view";
    public static final String QUOTATIONS_EDIT = "quotations.edit";

    /** Reports page; Accountant sees Sales, Purchasing, Outstanding & aging, Profit, Expenses. */
    public static final String REPORTS_VIEW = "reports.view";
    /** All 10 reports (Admin). */
    public static final String REPORTS_ALL = "reports.all";

    /** Administration: users, settings, audit log. */
    public static final String ADMIN_MANAGE = "admin.manage";

    /** Data Migration (when the feature is switched on). */
    public static final String MIGRATION_MANAGE = "migration.manage";

    private Permissions() {
    }
}
