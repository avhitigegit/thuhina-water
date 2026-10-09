/*
 * Every screen of the app (design 3.3 screen ↔ prototype map). Title and description are the page heading,
 * as in the prototype NAV (assets/js/layout.js). Which pages a user may open comes from the server menu
 * (/auth/me) – this list only holds the wording and the module that builds each screen.
 */
export interface RouteInfo {
  path: string;
  module: string;
  title: string;
  description: string;
  /** Build module in Master_Task_Breakdown.md */
  buildModule: string;
}

export const ROUTES: RouteInfo[] = [
  {
    path: "/dashboard",
    module: "Dashboard",
    title: "Dashboard",
    description: "Today at a glance",
    buildModule: "M12",
  },
  {
    path: "/master-data/bottles-products",
    module: "Master Data",
    title: "Bottles & Products",
    description:
      "Bottle types, other products, standard prices, deposits, customer agreed prices and accepted old bottles",
    buildModule: "M02",
  },
  {
    path: "/master-data/customers",
    module: "Master Data",
    title: "Customers",
    description:
      "Register customers (with the bottles given that day and any agreed price), edit and find them; click a customer for details and prices",
    buildModule: "M05",
  },
  {
    path: "/master-data/suppliers",
    module: "Master Data",
    title: "Suppliers & Factories",
    description: "Bottle and product suppliers, and the filling factories",
    buildModule: "M03",
  },
  {
    path: "/purchasing",
    module: "Purchasing",
    title: "Purchasing",
    description: "Quotations, purchase orders, goods receipt and supplier payments",
    buildModule: "M09",
  },
  {
    path: "/production",
    module: "Production",
    title: "Filling Factory",
    description: "Send empties, receive filled bottles, factory payments",
    buildModule: "M10",
  },
  {
    path: "/inventory",
    module: "Inventory",
    title: "Stock",
    description: "Bottle and product stock, damage, adjustments and low-stock alerts",
    buildModule: "M04",
  },
  {
    path: "/sales/enter-bills",
    module: "Sales & Deliveries",
    title: "Enter Bills",
    description: "Type in the day's paper bills quickly with the keyboard",
    buildModule: "M07",
  },
  {
    path: "/sales/bills",
    module: "Sales & Deliveries",
    title: "Sales & Bills",
    description: "All bills, plus first purchase, extra order, product sale and other entries",
    buildModule: "M06",
  },
  {
    path: "/sales/quotations",
    module: "Sales & Deliveries",
    title: "Customer Quotations",
    description:
      "Price quotations for customers and new organisations (government offices, factories): print or email, then mark accepted or not",
    buildModule: "M16",
  },
  {
    path: "/delivery/planning",
    module: "Delivery Schedule",
    title: "Delivery Planning",
    description:
      "Pick a date (normally tomorrow) to see who is due by route, the bottles needed and whether they are ready",
    buildModule: "M08",
  },
  {
    path: "/delivery/daily-list",
    module: "Delivery Schedule",
    title: "Daily Delivery List",
    description: "Who to deliver to each day, and bottles needed for coming days",
    buildModule: "M08",
  },
  {
    path: "/billing",
    module: "Billing & Payments",
    title: "Billing & Payments",
    description: "Customer balances, payments, statements and monthly invoices",
    buildModule: "M11",
  },
  {
    path: "/expenses",
    module: "Expenses",
    title: "Expenses",
    description:
      "Running expenses by month – electricity, water, salaries, rent, vehicle fuel and spare parts, and more",
    buildModule: "M15",
  },
  {
    path: "/migration",
    module: "Data Migration",
    title: "Data Migration",
    description: "Import existing customers and opening stock",
    buildModule: "M14",
  },
  {
    path: "/reports",
    module: "Reports",
    title: "Reports",
    description: "Choose a report and a date range",
    buildModule: "M12",
  },
  {
    path: "/admin",
    module: "Administration",
    title: "Administration",
    description: "Users, roles and audit log",
    buildModule: "M01",
  },
];

/** The route a pathname belongs to (exact or a sub-path such as /purchasing/123). */
export function findRoute(pathname: string): RouteInfo | undefined {
  return ROUTES.find((r) => pathname === r.path || pathname.startsWith(r.path + "/"));
}
