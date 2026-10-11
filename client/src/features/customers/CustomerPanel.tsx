"use client";

/*
 * Customer side panel (FR-11, prototype assets/js/customer-panel.js) – shared by Customers, and later Billing and
 * Sales. Header, banners (inactive, over the credit limit, delivery overdue), actions by permission, balance cards,
 * delivery and price summary, tabs History · Payments · Invoices · Prices. Sales actions open their screens (M06 –
 * M11); payments and invoices fill in with Billing (M11).
 */
import Link from "next/link";
import { useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { MoneyText } from "@/components/shared/MoneyText";
import { SidePanel } from "@/components/shared/SidePanel";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Tabs } from "@/components/shared/Tabs";
import { useCan } from "@/features/auth/access";
import type { CustomerDetail, CustomerHistoryRow, CustomerPriceRow } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useCustomer, useCustomerHistory, useCustomerPrices } from "./api";
import { CustomerPriceDialog } from "./CustomerPriceDialog";
import { deliveryText, payLabel, STATE_BADGE } from "./labels";
import { PriceText } from "./PriceText";
import { StatusDialog } from "./StatusDialog";

type PanelTab = "hist" | "pay" | "inv" | "price";

const historyColumns: Column<CustomerHistoryRow>[] = [
  { key: "date", header: "Date", cell: (h) => <span className="nowrap">{formatDateTime(h.at)}</span> },
  {
    key: "entry",
    header: "Entry",
    cell: (h) => (
      <Badge kind={h.kind === "REGISTERED" ? "ok" : h.kind === "PRICE" ? "info" : ""}>
        {h.kind === "REGISTERED" ? "Registered" : h.kind === "PRICE" ? "Price" : "Changed"}
      </Badge>
    ),
  },
  { key: "details", header: "Details", cell: (h) => <span className="small">{h.description}</span> },
  { key: "by", header: "By", cell: (h) => h.byName ?? h.by },
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
  const canBill = useCan(P.BILLING_VIEW);
  const canPay = useCan(P.BILLING_EDIT);
  const canQuote = useCan(P.QUOTATIONS_EDIT);
  const canSeePrices = useCan(P.CUSTOMER_PRICES_VIEW);
  const canSetPrices = useCan(P.CUSTOMER_PRICES_EDIT);
  const [tab, setTab] = useState<PanelTab>("hist");
  const [dialog, setDialog] = useState<"prices" | "status" | null>(null);
  const history = useCustomerHistory(c.id);
  const prices = useCustomerPrices(c.id, canSeePrices && tab === "price");
  const active = c.status === "ACTIVE";
  const held = c.bottles.reduce((s, b) => s + b.held, 0);
  const toCollect = c.bottles.reduce((s, b) => s + b.toCollect, 0);
  const owed = c.bottles.reduce((s, b) => s + b.owed, 0);
  const anyAgreed = c.prices.some((p) => p.agreed);
  const sales = (kind: string) => `/sales/bills?new=${kind}&cust=${c.code}`;

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
              {held > 0 ? (
                <Link className="btn" href={sales("extra")}>
                  Extra order
                </Link>
              ) : (
                <Link className="btn primary" href={sales("first")}>
                  First purchase
                </Link>
              )}
              {toCollect > 0 && (
                <Link className="btn" href={sales("collect")}>
                  Collect empties
                </Link>
              )}
              {owed > 0 && (
                <Link className="btn" href={sales("owed")}>
                  Settle owed
                </Link>
              )}
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
            { id: "pay", label: "Payments" },
            { id: "inv", label: "Invoices" },
            ...(canSeePrices ? [{ id: "price" as const, label: "Prices" }] : []),
          ]}
          value={tab}
          onChange={setTab}
        />
        {tab === "hist" && (
          <DataTable
            columns={historyColumns}
            rows={history.data}
            loading={history.isPending}
            rowKey={(h, i) => `${h.at}-${i}`}
            empty="No history yet."
          />
        )}
        {tab === "pay" && (
          <p className="muted">No payments yet. Payments are recorded in Billing &amp; Payments.</p>
        )}
        {tab === "inv" && (
          <p className="muted">
            {c.payType === "MONTHLY_BILL" ? "No invoices yet." : "Only Monthly bill customers get invoices."}
          </p>
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
