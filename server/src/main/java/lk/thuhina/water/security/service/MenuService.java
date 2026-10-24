package lk.thuhina.water.security.service;

import static lk.thuhina.water.security.Permissions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import lk.thuhina.water.security.Role;
import lk.thuhina.water.security.RolePermissions;
import lk.thuhina.water.security.dto.MeResponse.MenuItem;
import lk.thuhina.water.security.dto.MeResponse.MenuModule;
import lk.thuhina.water.security.dto.RolesMatrixResponse;
import lk.thuhina.water.settings.service.SettingsService;
import org.springframework.stereotype.Service;

/**
 * Builds the sidebar for a role – the same modules, order and access as the prototype
 * ({@code prototype/assets/js/layout.js} NAV) and the permission matrix (design 8.2).
 * A page is shown when the role has its view permission; it is "view only" when the role lacks its edit permission.
 */
@Service
public class MenuService {

    /** Feature switch that shows the Data Migration menu (hidden until go-live). */
    public static final String FEATURE_DATA_MIGRATION = "dataMigration";

    private record Page(String key, String title, String path, String viewPermission, String editPermission) {
    }

    /** Access level of a role on a page or right (Roles &amp; access tab). */
    public enum Level { FULL, VIEW, NONE }

    /** A right that is not a page of its own (design 8.2 rows "Customer agreed prices", "Approve sale over credit limit"). */
    private record Right(String title, String viewPermission, String editPermission) {
    }

    private static final List<Right> EXTRA_RIGHTS = List.of(
            new Right("Customer agreed prices", CUSTOMER_PRICES_VIEW, CUSTOMER_PRICES_EDIT),
            new Right("Approve sale over credit limit", SALES_APPROVE_OVER_LIMIT, null));

    private static final String REPORTS_ACCOUNTANT = "Sales, Purchasing, Outstanding & aging, Profit, Expenses";

    private record Module(String module, String icon, String feature, List<Page> pages) {
    }

    private static final List<Module> MENU = List.of(
            new Module("Dashboard", "home", null, List.of(
                    new Page("dashboard", "Dashboard", "/dashboard", DASHBOARD_VIEW, null))),
            new Module("Master Data", "db", null, List.of(
                    new Page("bottles-products", "Bottles & Products", "/master-data/bottles-products", MASTERDATA_VIEW, MASTERDATA_EDIT),
                    new Page("customers", "Customers", "/master-data/customers", CUSTOMERS_VIEW, CUSTOMERS_EDIT),
                    new Page("suppliers", "Suppliers & Factories", "/master-data/suppliers", PARTNERS_VIEW, PARTNERS_EDIT))),
            new Module("Purchasing", "cart", null, List.of(
                    new Page("purchasing", "Purchasing", "/purchasing", PURCHASING_VIEW, PURCHASING_EDIT))),
            new Module("Production", "factory", null, List.of(
                    new Page("production", "Filling Factory", "/production", PRODUCTION_VIEW, PRODUCTION_EDIT))),
            new Module("Inventory", "box", null, List.of(
                    new Page("inventory", "Stock", "/inventory", STOCK_VIEW, STOCK_EDIT))),
            new Module("Sales & Deliveries", "truck", null, List.of(
                    new Page("enter-bills", "Enter Bills", "/sales/enter-bills", SALES_VIEW, SALES_EDIT),
                    new Page("sales-bills", "Sales & Bills", "/sales/bills", SALES_VIEW, SALES_EDIT),
                    new Page("quotations", "Customer Quotations", "/sales/quotations", QUOTATIONS_VIEW, QUOTATIONS_EDIT))),
            new Module("Delivery Schedule", "calendar", null, List.of(
                    new Page("delivery-planning", "Delivery Planning", "/delivery/planning", DELIVERY_PLANNING_VIEW, null),
                    new Page("daily-delivery-list", "Daily Delivery List", "/delivery/daily-list", DELIVERY_LIST_VIEW, null))),
            new Module("Billing & Payments", "money", null, List.of(
                    new Page("billing", "Billing & Payments", "/billing", BILLING_VIEW, BILLING_EDIT))),
            new Module("Expenses", "receipt", null, List.of(
                    new Page("expenses", "Expenses", "/expenses", EXPENSES_VIEW, EXPENSES_EDIT))),
            new Module("Data Migration", "upload", FEATURE_DATA_MIGRATION, List.of(
                    new Page("data-migration", "Data Migration", "/migration", MIGRATION_MANAGE, null))),
            new Module("Reports", "chart", null, List.of(
                    new Page("reports", "Reports", "/reports", REPORTS_VIEW, null))),
            new Module("Administration", "shield", null, List.of(
                    new Page("administration", "Administration", "/admin", ADMIN_MANAGE, null))));

    private final SettingsService settings;

    public MenuService(SettingsService settings) {
        this.settings = settings;
    }

    public List<MenuModule> menuFor(Role role) {
        Set<String> perms = RolePermissions.of(role);
        List<MenuModule> result = new ArrayList<>();
        for (Module m : MENU) {
            List<MenuItem> items = m.pages().stream()
                    .filter(p -> perms.contains(p.viewPermission()))
                    .map(p -> new MenuItem(p.key(), p.title(), p.path(),
                            p.editPermission() != null && !perms.contains(p.editPermission())))
                    .toList();
            if (!items.isEmpty()) {
                boolean hidden = m.feature() != null && !settings.isFeatureEnabled(m.feature());
                result.add(new MenuModule(m.module(), m.icon(), hidden, m.pages().size() > 1, items));
            }
        }
        return result;
    }

    /**
     * Roles &amp; access tab (M01, design 8.2): every menu page and the special rights × the three roles,
     * from the same menu and permission map as the sidebar and the endpoint checks.
     */
    public RolesMatrixResponse rolesMatrix() {
        List<Role> roles = List.of(Role.values());
        List<RolesMatrixResponse.Row> rows = new ArrayList<>();
        for (Module m : MENU) {
            for (Page p : m.pages()) {
                String area = m.pages().size() > 1 ? m.module() + " › " + p.title() : p.title();
                rows.add(new RolesMatrixResponse.Row(area, roles.stream()
                        .map(r -> cell(r, p.viewPermission(), p.editPermission(), note(m, p, r)))
                        .toList()));
            }
        }
        for (Right right : EXTRA_RIGHTS) {
            rows.add(new RolesMatrixResponse.Row(right.title(), roles.stream()
                    .map(r -> cell(r, right.viewPermission(), right.editPermission(), null))
                    .toList()));
        }
        return new RolesMatrixResponse(
                roles.stream().map(r -> new RolesMatrixResponse.RoleColumn(r.name(), r.label())).toList(), rows);
    }

    /**
     * FULL / VIEW / NONE as in design 8.2: no view permission → none; edit permission missing → view.
     * Pages that have no edit permission at all (Dashboard, Daily Delivery List, Reports) are read-only screens:
     * the Admin is shown as full, the other roles as view (8.2 "View", "View + print").
     */
    public static Level level(Role role, String viewPermission, String editPermission) {
        Set<String> perms = RolePermissions.of(role);
        if (!perms.contains(viewPermission)) {
            return Level.NONE;
        }
        if (editPermission == null) {
            return role == Role.ADMIN ? Level.FULL : Level.VIEW;
        }
        return perms.contains(editPermission) ? Level.FULL : Level.VIEW;
    }

    private static RolesMatrixResponse.Cell cell(Role role, String view, String edit, String note) {
        Level level = level(role, view, edit);
        return new RolesMatrixResponse.Cell(level.name(), level == Level.NONE ? null : note);
    }

    /** Extra words in a cell where design 8.2 has them. */
    private static String note(Module m, Page p, Role role) {
        Set<String> perms = RolePermissions.of(role);
        if (p.viewPermission().equals(REPORTS_VIEW)) {
            return perms.contains(REPORTS_ALL) ? "All 10 reports" : REPORTS_ACCOUNTANT;
        }
        if (p.viewPermission().equals(DELIVERY_LIST_VIEW) && !perms.contains(DELIVERY_PLANNING_VIEW)) {
            return "View + print";
        }
        if (p.editPermission() != null && !perms.contains(p.editPermission())
                && p.viewPermission().equals(QUOTATIONS_VIEW)) {
            return "View + print";
        }
        if (m.feature() != null) {
            return "When switched on";
        }
        return null;
    }
}
