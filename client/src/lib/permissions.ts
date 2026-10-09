/*
 * Permission names – the same strings as the server (lk.thuhina.water.security.Permissions, design 8.2).
 * The client only uses them to hide buttons and links; the server checks every call.
 */
export const P = {
  DASHBOARD_VIEW: "dashboard.view",
  MASTERDATA_VIEW: "masterdata.view",
  MASTERDATA_EDIT: "masterdata.edit",
  CUSTOMERS_VIEW: "customers.view",
  CUSTOMERS_EDIT: "customers.edit",
  CUSTOMER_PRICES_VIEW: "customer-prices.view",
  CUSTOMER_PRICES_EDIT: "customer-prices.edit",
  PARTNERS_VIEW: "partners.view",
  PARTNERS_EDIT: "partners.edit",
  PURCHASING_VIEW: "purchasing.view",
  PURCHASING_EDIT: "purchasing.edit",
  PRODUCTION_VIEW: "production.view",
  PRODUCTION_EDIT: "production.edit",
  STOCK_VIEW: "stock.view",
  STOCK_EDIT: "stock.edit",
  SALES_VIEW: "sales.view",
  SALES_EDIT: "sales.edit",
  SALES_APPROVE_OVER_LIMIT: "sales.approve-over-limit",
  DELIVERY_PLANNING_VIEW: "delivery-planning.view",
  DELIVERY_LIST_VIEW: "delivery-list.view",
  BILLING_VIEW: "billing.view",
  BILLING_EDIT: "billing.edit",
  EXPENSES_VIEW: "expenses.view",
  EXPENSES_EDIT: "expenses.edit",
  QUOTATIONS_VIEW: "quotations.view",
  QUOTATIONS_EDIT: "quotations.edit",
  REPORTS_VIEW: "reports.view",
  REPORTS_ALL: "reports.all",
  ADMIN_MANAGE: "admin.manage",
  MIGRATION_MANAGE: "migration.manage",
} as const;

export type Permission = (typeof P)[keyof typeof P];
