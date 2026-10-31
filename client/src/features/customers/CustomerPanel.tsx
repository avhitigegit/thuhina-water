"use client";

/*
 * Customer side panel (FR-11, prototype assets/js/customer-panel.js) – shared by Customers, and later Billing and
 * Sales. Header, banners (inactive, over the credit limit, delivery overdue), actions by permission, balance cards,
 * delivery and price summary, tabs History · Payments · Invoices · Prices. First purchase / Extra order / Collect
 * empties / Settle owed open their sale pop-ups here (M06); Enter bill opens the daily grid (M07). The History tab
 * lists the bills and payments with charge, paid and the running balance; Payments and Invoices (M11) are shown to roles
 * that see Billing, with the receipt and invoice prints.
 */
import Link from "next/link";
import { useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { MoneyText } from "@/components/shared/MoneyText";
import { SidePanel } from "@/components/shared/SidePanel";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Tabs } from "@/components/shared/Tabs";
import { useCan } from "@/features/auth/access";
import {
  invoiceUrl,
  openPdf,
  receiptUrl,
  useCustomerInvoices,
  useCustomerPayments,
} from "@/features/billing/api";
import { NewSaleDialog } from "@/features/sales/SaleDialogs";
import type { SaleKind } from "@/features/sales/labels";
import type {
  CustomerDetail,
  CustomerHistoryRow,
  CustomerPaymentRow,
  CustomerPriceRow,
  InvoiceRow,
} from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useCustomer, useCustomerHistory, useCustomerPrices } from "./api";
import { CustomerPriceDialog } from "./CustomerPriceDialog";
import { deliveryText, payLabel, STATE_BADGE } from "./labels";
import { PriceText } from "./PriceText";
import { StatusDialog } from "./StatusDialog";

type PanelTab = "hist" | "pay" | "inv" | "price";

const ENTRY: Record<string, { label: string; kind: "ok" | "info" | "" | "dark" }> = {
  REGISTERED: { label: "Registered", kind: "ok" },
  PRICE: { label: "Price", kind: "info" },
  CHANGED: { label: "Changed", kind: "" },
  SALE: { label: "Bill", kind: "dark" },
  PAYMENT: { label: "Payment", kind: "ok" },
};

/** Bills and payments show their business date; the other entries when they were made. */
const DATED = new Set(["SALE", "PAYMENT"]);

/** History: registration, changes, prices and bills (bill date; bill number links to Sales & Bills). */
function historyColumns(canSeeBills: boolean): Column<CustomerHistoryRow>[] {
  return [
    {
      key: "date",
      header: "Date",
      cell: (h) => (
        <span className="nowrap">{DATED.has(h.kind) ? formatDate(h.date) : formatDateTime(h.at)}</span>
      ),
    },
    {
      key: "entry",
      header: "Entry",
      cell: (h) => {
        const e = ENTRY[h.kind] ?? ENTRY.CHANGED;
        return (
          <>
            <Badge kind={e.kind}>{e.label}</Badge>
            {h.billNo && (
              <div className="small mono">
                {canSeeBills && h.kind === "SALE" ? (
                  <Link href={`/sales/bills?bill=${h.billNo}`}>{h.billNo}</Link>
                ) : (
                  h.billNo
                )}
              </div>
            )}
          </>
        );
      },
    },
    { key: "details", header: "Details", cell: (h) => <span className="small">{h.description}</span> },
    {
      key: "charge",
      header: "Charge",
      align: "num",
      cell: (h) => (h.amount != null && Number(h.amount) !== 0 ? formatMoney(h.amount) : ""),
    },
    {
      key: "paid",
      header: "Paid",
      align: "num",
      cell: (h) => (h.paid != null && Number(h.paid) !== 0 ? formatMoney(h.paid) : ""),
    },
    {
      key: "balance",
      header: "Balance",
      align: "num",
      cell: (h) => (h.balance != null ? formatMoney(h.balance) : ""),
    },
    { key: "by", header: "By", cell: (h) => <span className="small">{h.byName ?? h.by}</span> },
  ];
}

const paymentColumns: Column<CustomerPaymentRow>[] = [
  {
    key: "receipt",
    header: "Receipt",
    cell: (p) => (
      <button
        className="btn link mono"
        type="button"
        title="Print the receipt"
        onClick={() => openPdf(receiptUrl(p.id))}
      >
        {p.receiptNo}
      </button>
    ),
  },
  { key: "date", header: "Date", cell: (p) => formatDate(p.date) },
  { key: "method", header: "Method", cell: (p) => p.methodLabel },
  {
    key: "ref",
    header: "Reference",
    cell: (p) => (
      <>
        {p.reference}
        {p.invoiceNo && <div className="small muted">for {p.invoiceNo}</div>}
      </>
    ),
  },
  { key: "amount", header: "Amount", align: "num", cell: (p) => <MoneyText value={p.amount} /> },
];

const invoiceColumns: Column<InvoiceRow>[] = [
  {
    key: "invoice",
    header: "Invoice",
    cell: (i) => <Link href={`/billing?invoice=${i.id}`}>{i.invoiceNo}</Link>,
  },
  { key: "month", header: "Month", cell: (i) => i.periodLabel },
  { key: "amount", header: "This month", align: "num", cell: (i) => <MoneyText value={i.amount} /> },
  { key: "total", header: "Total due", align: "num", cell: (i) => <MoneyText value={i.totalDue} /> },
  {
    key: "print",
    header: "",
    className: "actions",
    cell: (i) => (
      <button className="btn sm" type="button" onClick={() => openPdf(invoiceUrl(i.id))}>
        Print
      </button>
    ),
  },
];

const priceColumns: Column<CustomerPriceRow>[] = [
  { key: "bottle", header: "Bottle", cell: (e) => e.label },
  {
    key: "price",
    header: "Price",
    align: "num",
    cell: (e) => (
      <>
        <b>
          <MoneyText value={e.price} />
        </b>
        {e.standardAtSet != null && (
          <div className="small muted">standard {formatMoney(e.standardAtSet)}</div>
        )}
      </>
    ),
  },
  { key: "from", header: "From", cell: (e) => formatDate(e.validFrom) },
  { key: "until", header: "Until", cell: (e) => (e.validUntil ? formatDate(e.validUntil) : "–") },
  {
    key: "reason",
    header: "Reason",
    cell: (e) => (
      <>
        {e.reason}
        <div className="small muted">{e.createdByName ?? e.createdBy}</div>
      </>
    ),
  },
  {
    key: "state",
    header: "",
    cell: (e) => {
      const s = STATE_BADGE[e.state] ?? { label: e.state, kind: "" as const };
      return <Badge kind={s.kind}>{s.label}</Badge>;
    },
  },
];

function PanelBody({ c, onEdit }: { c: CustomerDetail; onEdit?: (c: CustomerDetail) => void }) {
  const canEdit = useCan(P.CUSTOMERS_EDIT);
  const canSell = useCan(P.SALES_EDIT);
  const canSeeBills = useCan(P.SALES_VIEW);
  const canBill = useCan(P.BILLING_VIEW);
  const canPay = useCan(P.BILLING_EDIT);
  const canQuote = useCan(P.QUOTATIONS_EDIT);
  const canSeePrices = useCan(P.CUSTOMER_PRICES_VIEW);
  const canSetPrices = useCan(P.CUSTOMER_PRICES_EDIT);
  const [tab, setTab] = useState<PanelTab>("hist");
  const [dialog, setDialog] = useState<"prices" | "status" | null>(null);
  const [sale, setSale] = useState<SaleKind | null>(null);
  const history = useCustomerHistory(c.id);
  const prices = useCustomerPrices(c.id, canSeePrices && tab === "price");
  const payments = useCustomerPayments(c.id, canBill && tab === "pay");
  const invoices = useCustomerInvoices(c.id, canBill && tab === "inv");
  const active = c.status === "ACTIVE";
  const held = c.bottles.reduce((s, b) => s + b.held, 0);
  const toCollect = c.bottles.reduce((s, b) => s + b.toCollect, 0);
  const owed = c.bottles.reduce((s, b) => s + b.owed, 0);
  const anyAgreed = c.prices.some((p) => p.agreed);
  const saleButton = (kind: SaleKind, label: string, primary?: boolean) => (
    <button className={primary ? "btn primary" : "btn"} type="button" onClick={() => setSale(kind)}>
      {label}
    </button>
  );

  return (
    <>
      {!active && (
        <div className="banner warn">
          <b>Inactive{c.statusDate ? ` since ${formatDate(c.statusDate)}` : ""}.</b> {c.statusReason} History
          is kept.
        </div>
      )}
      {c.overLimit && (
        <div className="banner bad">
          Over the credit limit of {formatMoney(c.creditLimit)}. New sales need Admin approval.
        </div>
      )}
      {c.overdue && (
        <div className="banner warn">Delivery overdue since {formatDate(c.nextDeliveryDate)}.</div>
      )}

      <div className="card">
        <div className="muted">
          {c.code} · {c.address}, {c.areaName} · {c.phone}
        </div>
        <div style={{ margin: "4px 0 12px" }}>
          <StatusBadge status={active ? "Active" : "Inactive"} /> <Badge>{c.typeName}</Badge>{" "}
          <StatusBadge status={payLabel(c.payType)} />
          {c.whatsappOptIn && (
            <>
              {" "}
              <Badge kind="ok">WhatsApp {c.whatsappNumber}</Badge>
            </>
          )}
          {anyAgreed && (
            <>
              {" "}
              <Badge kind="info">Agreed price</Badge>
            </>
          )}
        </div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", marginBottom: 12 }}>
          {canEdit && onEdit && (
            <button className="btn" type="button" onClick={() => onEdit(c)}>
              Edit
            </button>
          )}
          {canSell && active && (
            <>
              <Link className="btn primary" href={`/sales/enter-bills?cust=${c.code}`}>
                Enter bill
              </Link>
              {held > 0 ? saleButton("extra", "Extra order") : saleButton("first", "First purchase", true)}
              {toCollect > 0 && saleButton("collect", "Collect empties")}
              {owed > 0 && saleButton("owed", "Settle owed")}
            </>
          )}
          {canPay && c.outstanding > 0 && (
            <Link className="btn" href={`/billing?pay=${c.code}`}>
              Receive payment
            </Link>
          )}
          {canBill && (
            <Link className="btn" href={`/billing?statement=${c.code}`}>
              Statement
            </Link>
          )}
          {canQuote && (
            <Link className="btn" href={`/sales/quotations?new=1&cust=${c.code}`}>
              Quotation
            </Link>
          )}
          {canSetPrices && (
            <button className="btn" type="button" onClick={() => setDialog("prices")}>
              Prices
            </button>
          )}
          {canEdit && (
            <button
              className={active ? "btn danger" : "btn"}
              type="button"
              onClick={() => setDialog("status")}
            >
              {active ? "Deactivate" : "Reactivate"}
            </button>
          )}
        </div>

        <div className="balances">
          {/* The main (largest) bottle type always; others only when the customer has some (prototype). */}
          {c.bottles
            .filter((b, i) => i === 0 || b.held + b.toCollect + b.owed > 0)
            .map((b) => (
              <div key={b.bottleTypeCode} style={{ display: "contents" }}>
                <div className="bal">
                  <div className="label">Bottles held ({b.label})</div>
                  <div className="value">{b.held}</div>
                </div>
                <div className={b.toCollect ? "bal hl" : "bal"}>
                  <div className="label">Empties to collect ({b.label})</div>
                  <div className="value">{b.toCollect}</div>
                </div>
                <div className={b.owed ? "bal bad" : "bal"}>
                  <div className="label">Bottles owed ({b.label})</div>
                  <div className="value">{b.owed}</div>
                </div>
              </div>
            ))}
          <div className={c.outstanding > 0 ? "bal hl" : "bal"}>
            <div className="label">Outstanding</div>
            <div className="value" style={{ fontSize: "1rem" }}>
              <MoneyText value={c.outstanding} />
            </div>
          </div>
          {c.payType !== "CASH" && (
            <div className="bal">
              <div className="label">Credit limit</div>
              <div className="value" style={{ fontSize: "1rem" }}>
                <MoneyText value={c.creditLimit} />
              </div>
              <div className="small muted">{c.termsDays} days</div>
            </div>
          )}
        </div>

        <div className="summary" style={{ marginTop: 12 }}>
          <span className="muted">Delivery</span>
          <b>{deliveryText(c.deliveryDay, c.cycleWeeks, c.nextDeliveryDate)}</b>
          <span className="muted">Usual quantity</span>
          <b>
            {Object.entries(c.usualQty)
              .filter(([, q]) => q > 0)
              .map(([code, q]) => `${q} × ${c.prices.find((p) => p.bottleTypeCode === code)?.label ?? code}`)
              .join(", ") || "–"}
          </b>
          <span className="muted">Water price</span>
          <b>
            {c.prices
              .filter((p) => p.price != null)
              .map((p) => (
                <div key={p.bottleTypeCode}>
                  {p.label} <PriceText price={p} showNext />
                </div>
              ))}
          </b>
          <span className="muted">Customer since</span>
          <b>{formatDate(c.registeredOn)}</b>
          {c.notes && (
            <>
              <span className="muted">Notes</span>
              <b>{c.notes}</b>
            </>
          )}
        </div>
      </div>

      <div className="card">
        <Tabs
          tabs={[
            { id: "hist", label: "History" },
            ...(canBill
              ? [
                  { id: "pay" as const, label: "Payments" },
                  { id: "inv" as const, label: "Invoices" },
                ]
              : []),
            ...(canSeePrices ? [{ id: "price" as const, label: "Prices" }] : []),
          ]}
          value={tab}
          onChange={setTab}
        />
        {tab === "hist" && (
          <DataTable
            columns={historyColumns(canSeeBills)}
            rows={history.data}
            loading={history.isPending}
            rowKey={(h, i) => `${h.at}-${i}`}
            empty="No history yet."
          />
        )}
        {tab === "pay" && (
          <DataTable
            columns={paymentColumns}
            rows={payments.data?.items}
            loading={payments.isPending}
            rowKey={(p) => p.id}
            empty="No payments."
          />
        )}
        {tab === "inv" && (
          <DataTable
            columns={invoiceColumns}
            rows={invoices.data}
            loading={invoices.isPending}
            rowKey={(i) => i.id}
            empty={
              c.payType === "MONTHLY_BILL" ? "No invoices yet." : "Only Monthly bill customers get invoices."
            }
          />
        )}
        {tab === "price" && (
          <DataTable
            columns={priceColumns}
            rows={prices.data}
            loading={prices.isPending}
            rowKey={(e) => e.id}
            empty={`Standard ${c.typeName} prices – no agreed prices.`}
          />
        )}
      </div>

      {dialog === "prices" && <CustomerPriceDialog customer={c} onClose={() => setDialog(null)} />}
      {dialog === "status" && <StatusDialog customer={c} onClose={() => setDialog(null)} />}
      {sale && (
        <NewSaleDialog
          kind={sale}
          initialCustomer={{
            id: c.id,
            code: c.code,
            name: c.name,
            phone: c.phone,
            areaName: c.areaName,
            status: c.status,
          }}
          onClose={() => setSale(null)}
        />
      )}
    </>
  );
}

export function CustomerPanel({
  customerId,
  onClose,
  onEdit,
}: {
  customerId: number | null;
  onClose: () => void;
  onEdit?: (c: CustomerDetail) => void;
}) {
  const customer = useCustomer(customerId);
  return (
    <SidePanel
      open={customerId !== null}
      onOpenChange={(o) => !o && onClose()}
      title={customer.data?.name ?? "Customer"}
    >
      {customer.isError ? (
        <div className="banner bad">{customer.error.message}</div>
      ) : !customer.data ? (
        <p className="muted">Loading…</p>
      ) : (
        <PanelBody c={customer.data} onEdit={onEdit} />
      )}
    </SidePanel>
  );
}
