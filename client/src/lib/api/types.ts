/*
 * API types, generated from the server's OpenAPI description (design 14):
 *   start the API (local profile), then `npm run api:types` → src/lib/api/schema.d.ts (do not edit that file).
 * Name the types each feature uses here.
 */
import type { components } from "./schema";

type Schemas = components["schemas"];

export type MeResponse = Schemas["MeResponse"];
export type UserInfo = Schemas["UserInfo"];
export type MenuModule = Schemas["MenuModule"];
export type MenuItem = Schemas["MenuItem"];
export type LoginRequest = Schemas["LoginRequest"];
export type ChangePasswordRequest = Schemas["ChangePasswordRequest"];

// Administration (M01)
export type UserResponse = Schemas["UserResponse"];
export type CreateUserRequest = Schemas["CreateUserRequest"];
export type UpdateUserRequest = Schemas["UpdateUserRequest"];
export type ResetPasswordResponse = Schemas["ResetPasswordResponse"];
export type RoleCode = CreateUserRequest["role"];
export type RolesMatrixResponse = Schemas["RolesMatrixResponse"];
export type AccessCell = Schemas["Cell"];
export type AuditEntry = Schemas["AuditEntryResponse"];
export type AuditPage = Schemas["PageResponseAuditEntryResponse"];
export type AuditFilterOptions = Schemas["AuditFilterOptions"];

/** GET/PUT /settings/company – a JSON object on the server, so typed here (design 4.3 app_setting "company"). */
export interface CompanySettings {
  name: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  regNo: string | null;
  /** Stored file key; set only by the logo upload. */
  logo: string | null;
  /** Address of the logo image (cache-safe), or null. */
  logoUrl: string | null;
}

// Master data – Bottles & Products (M02)
export type BottleType = Schemas["BottleTypeResponse"];
export type CreateBottleTypeRequest = Schemas["CreateBottleTypeRequest"];
export type UpdateBottleTypeRequest = Schemas["UpdateBottleTypeRequest"];
export type Product = Schemas["ProductResponse"];
export type CreateProductRequest = Schemas["CreateProductRequest"];
export type UpdateProductRequest = Schemas["UpdateProductRequest"];
export type CustomerType = Schemas["CustomerTypeResponse"];
export type Area = Schemas["AreaResponse"];
export type OldBottleBrand = Schemas["OldBottleBrandResponse"];
export type CreateOldBottleBrandRequest = Schemas["CreateOldBottleBrandRequest"];
export type UpdateOldBottleBrandRequest = Schemas["UpdateOldBottleBrandRequest"];
export type PriceValue = Schemas["PriceValue"];
/** `current` / `next` are null when there is no such price (springdoc drops "nullable" on object references). */
export type PriceCell = Omit<Schemas["PriceCell"], "current" | "next"> & {
  current: PriceValue | null;
  next: PriceValue | null;
};
export type PriceMatrix = Omit<Schemas["PriceMatrixResponse"], "water" | "deposits"> & {
  water: PriceCell[];
  deposits: PriceCell[];
};
export type PriceHistoryRow = Schemas["PriceHistoryRow"];
export type SetPriceRequest = Schemas["SetPriceRequest"];

// Suppliers & factories (M03)
export type Supplier = Schemas["SupplierResponse"];
export type SuppliedItem = Schemas["SuppliedItem"];
export type SupplierRequest = Schemas["SupplierRequest"];
export type Factory = Schemas["FactoryResponse"];
export type FactoryChargeView = Schemas["FactoryChargeView"];
export type FactoryRequest = Schemas["FactoryRequest"];

// Inventory – Stock (M04)
export type StockOverview = Schemas["StockOverview"];
export type BottleStock = Schemas["BottleStock"];
export type ProductStock = Schemas["ProductStock"];
export type LowStockAlert = Schemas["LowStockAlert"];
export type MovementRow = Schemas["MovementRow"];
export type MovementPage = Schemas["PageResponseMovementRow"];
export type DamageListRow = Schemas["DamageListRow"];
export type CompanyDamageRequest = Schemas["CompanyDamageRequest"];
export type DamageLocation = CompanyDamageRequest["location"];
export type AdjustmentRequest = Schemas["AdjustmentRequest"];
export type AdjustmentResponse = Schemas["AdjustmentResponse"];

// Customers (M05)
export type CustomerRequest = Schemas["CustomerRequest"];
export type PayType = CustomerRequest["payType"];
export type CustomerListRow = Schemas["CustomerListRow"];
export type CustomerList = Schemas["CustomerListResponse"];
export type CustomerBottles = Schemas["BottleBalance"];
export type ScheduledPrice = Schemas["ScheduledPrice"];
/** `next` is null when no agreed-price change is scheduled (springdoc drops "nullable" on object references). */
export type WaterPriceView = Omit<Schemas["WaterPriceView"], "next"> & { next: ScheduledPrice | null };
export type CustomerDetail = Omit<Schemas["CustomerDetail"], "prices"> & { prices: WaterPriceView[] };
/** `bills` = the first-purchase bills of the bottles given on the registration date (empty when editing). */
export type CustomerSaved = { customer: CustomerDetail; agreedPricesSet: number; bills: TxnDetail[] };
export type CustomerHistoryRow = Schemas["HistoryRow"];
export type CustomerLookupRow = Schemas["LookupRow"];
export type CustomerPriceRow = Schemas["CustomerPriceRow"];
export type SetCustomerPriceRequest = Schemas["SetCustomerPriceRequest"];
export type EndCustomerPriceRequest = Schemas["EndCustomerPriceRequest"];
export type DeactivateRequest = Schemas["DeactivateRequest"];

// Sales & Bills (M06)
export type SaleLine = Schemas["SaleLine"];
export type SaleProblem = Schemas["Problem"];
export type BottlesAfter = Schemas["BottlesAfter"];
export type CreditView = Schemas["CreditView"];
/** `credit` is null without a customer (walk-in product sale). */
export type SalePreview = Omit<Schemas["SalePreview"], "credit"> & { credit: CreditView | null };
export type RegistrationPreview = Schemas["RegistrationPreview"];
export type BillLink = Schemas["BillLink"];
export type TxnRow = Schemas["TxnRow"];
export type TxnList = Schemas["TxnListResponse"];
/** `reversalOf` / `reversedBy` are null when there is no such link. */
export type TxnDetail = Omit<Schemas["TxnDetail"], "reversalOf" | "reversedBy"> & {
  reversalOf: BillLink | null;
  reversedBy: BillLink | null;
};
export type ScheduleChange = Schemas["ScheduleChange"];
/** `schedule` is null for bills that do not move the next delivery. */
export type SaleResult = { txn: TxnDetail; schedule: ScheduleChange | null };
export type PaperBillCheck = Omit<Schemas["PaperBillCheck"], "txn"> & { txn: TxnRow | null };
export type FirstPurchaseRequest = Schemas["FirstPurchaseRequest"];
export type ExtraOrderRequest = Schemas["ExtraOrderRequest"];
export type CollectEmptiesRequest = Schemas["CollectEmptiesRequest"];
export type CustomerDamageRequest = Schemas["CustomerDamageRequest"];
export type SettleOwedRequest = Schemas["SettleOwedRequest"];
export type ProductSaleRequest = Schemas["ProductSaleRequest"];
export type PayMethod = NonNullable<ProductSaleRequest["payMethod"]>;
export type ReverseRequest = Schemas["ReverseRequest"];

// Enter Bills (M07) – due list (design 5.4) and the suggested paper bill number
export type DueBottle = Schemas["DueBottle"];
export type EnteredBill = Schemas["EnteredBill"];
export type DueCustomer = Schemas["DueCustomer"];
export type DueCounts = Schemas["DueCounts"];
export type DueList = Schemas["DueList"];
export type NextPaperBill = Schemas["NextPaperBill"];
export type BottleQty = Schemas["BottleQty"];
export type DueTotals = Schemas["DueTotals"];
export type PlanKpis = Schemas["PlanKpis"];
export type LastDelivery = Schemas["LastDelivery"];
/** `last` is null before a customer's first delivery (the generator drops null on object fields). */
export type PlanRow = Omit<Schemas["PlanRow"], "last"> & { last: LastDelivery | null };
export type PlanRoute = Omit<Schemas["PlanRoute"], "rows"> & { rows: PlanRow[] };
export type Planning = Omit<Schemas["Planning"], "routes"> & { routes: PlanRoute[] };
export type ReadyCheck = Schemas["ReadyCheck"];
export type Requirement = Schemas["Requirement"];
export type RequirementDay = Schemas["RequirementDay"];

// Purchasing (M09)
export type ItemQty = Schemas["ItemQty"];
export type SupplierLink = Schemas["SupplierLink"];
export type PoLink = Schemas["PoLink"];
export type QrRequest = Schemas["QrRequest"];
/** `chosen` and `po` are null until a quotation is chosen / a PO is made (the generator drops null on object fields). */
export type QrRow = Omit<Schemas["QrRow"], "chosen" | "po"> & {
  chosen: SupplierLink | null;
  po: PoLink | null;
};
export type QrDetail = Omit<Schemas["QrDetail"], "po"> & { po: PoLink | null };
export type AskedSupplier = Schemas["AskedSupplier"];
export type QuoteColumn = Schemas["QuoteColumn"];
export type QuotePrice = Schemas["QuotePrice"];
/** A blank unit price (null) = not quoted (the generator makes nested fields required). */
export type QuotationRequestBody = Omit<Schemas["QuotationRequestBody"], "prices"> & {
  prices: { itemCode: string; unitPrice: number | null }[];
};
export type AttachmentResponse = Schemas["AttachmentResponse"];
export type PoRow = Schemas["PoRow"];
export type PoDetail = Schemas["PoDetail"];
export type PoLineView = Schemas["PoLineView"];
/** qty 0 removes a line; its price may then be left out. */
export type PoUpdateRequest = Omit<Schemas["PoUpdateRequest"], "lines"> & {
  lines: { itemCode: string; qty: number; unitPrice: number | null }[];
};
export type PoHistoryRow = Schemas["PoHistoryRow"];
export type ReceiptView = Schemas["ReceiptView"];
export type ReceiptRequest = Schemas["ReceiptRequest"];
export type ReceiptResult = Schemas["ReceiptResult"];
export type SupplierBalance = Schemas["SupplierBalance"];
export type PaymentRequest = Schemas["PaymentRequest"];
export type PaymentRow = Schemas["PaymentRow"];

// Filling Factory (M10)
export type FactoryLink = Schemas["FactoryLink"];
export type DispatchRequest = Schemas["DispatchRequest"];
export type DispatchView = Schemas["DispatchView"];
export type BatchView = Schemas["BatchView"];
export type ReturnView = Schemas["ReturnView"];
export type ReturnRequest = Schemas["ReturnRequest"];
export type ReturnResult = Schemas["ReturnResult"];
export type FactoryBottleStock = Schemas["FactoryBottleStock"];
export type FactoryOwed = Schemas["FactoryOwed"];
export type ProductionSummary = Schemas["ProductionSummary"];
export type FactoryMonth = Schemas["FactoryMonth"];
export type FactoryPaymentRequest = Schemas["FactoryPaymentRequest"];
export type FactoryPaymentRow = Schemas["FactoryPaymentRow"];

// Billing & payments (M11)
export type BillingCustomerInfo = Schemas["CustomerInfo"];
export type AccountRow = Schemas["AccountRow"];
export type AgingTotals = Schemas["AgingTotals"];
export type AccountList = Schemas["AccountListResponse"];
export type AccountSummary = Schemas["AccountSummary"];
export type InvoiceLink = Schemas["InvoiceLink"];
export type AccountView = Schemas["AccountView"];
export type CustomerPaymentRequest = Schemas["CustomerPaymentRequest"];
export type CustomerPaymentRow = Schemas["CustomerPaymentRow"];
export type PaymentSaved = Schemas["PaymentSaved"];
export type PaymentList = Schemas["PaymentListResponse"];
export type RecentMoney = Schemas["RecentMoney"];
export type PeriodOption = Schemas["PeriodOption"];
/** `invoice` is null until generated (springdoc drops "nullable" on object references). */
export type InvoiceMonthRow = Omit<Schemas["InvoiceMonthRow"], "invoice"> & { invoice: InvoiceLink | null };
export type InvoicePreview = Omit<Schemas["InvoicePreview"], "rows"> & { rows: InvoiceMonthRow[] };
export type SkippedCustomer = Schemas["SkippedCustomer"];
export type InvoiceRow = Schemas["InvoiceRow"];
export type GenerateResult = Schemas["GenerateResult"];
export type InvoiceLine = Schemas["InvoiceLine"];
export type InvoiceView = Schemas["InvoiceView"];

// Expenses (M15)
export type ExpenseType = Schemas["ExpenseTypeView"];
export type ExpenseTypeRequest = Schemas["ExpenseTypeRequest"];
export type ExpenseRow = Schemas["ExpenseRow"];
export type ExpenseRequest = Schemas["ExpenseRequest"];
export type ExpenseSaved = Schemas["ExpenseSaved"];
export type ExpenseMonthView = Schemas["ExpenseMonthView"];
export type ExpenseSummary = Schemas["ExpenseSummary"];
export type ExpenseSummaryRow = Schemas["ExpenseSummaryRow"];
