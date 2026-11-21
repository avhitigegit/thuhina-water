"use client";

/*
 * Payments (one home for money): actions + Receive payment (billing.edit), Pay supplier (purchasing.edit), Pay factory
 * (production.edit); tabs Money in (customer payments and money paid on bills) · Money out (supplier and factory payments,
 * expenses). Money paid on a bill is entered with the bill ("Paid now", Enter Bills, My Deliveries) and an expense on the
 * Expenses page – both are listed here. Links: ?pay=C0012 (or ?pay=new) opens Receive payment, ?supplier=<id> (or new)
 * Pay supplier, ?factory=1 Pay factory.
 */
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { Can, useCan } from "@/features/auth/access";
import { clearLinkParams, lookup } from "@/features/billing/links";
import { PaymentSavedDialog, ReceivePaymentDialog } from "@/features/billing/PaymentDialogs";
import { CustomerPanel } from "@/features/customers/CustomerPanel";
import { useProductionSummary } from "@/features/production/api";
import { FactoryPaymentDialog } from "@/features/production/FactoryPaymentDialog";
import type { CustomerLookupRow, MoneyRow, PaymentSaved } from "@/lib/api/types";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useDebounced } from "@/lib/useDebounced";
import { useMoney, type Direction } from "./api";
import { PaySupplierDialog } from "./PaySupplierDialog";

const TABS = ["in", "out"] as const;

type Dialog =
  | { kind: "receive"; customer: CustomerLookupRow | null }
  | { kind: "saved"; saved: PaymentSaved }
  | { kind: "supplier"; supplierId: number | null }
  | { kind: "factory" }
  | null;

export function Payments({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const canReceive = useCan(P.BILLING_EDIT);
  const canSupplier = useCan(P.PURCHASING_EDIT);
  const canFactory = useCan(P.PRODUCTION_EDIT);
  const [tab, setTab] = useHashTab(TABS);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [panelId, setPanelId] = useState<number | null>(null);

  const payParam = params.get("pay");
  const supplierParam = params.get("supplier");
  const factoryParam = params.get("factory");
  useEffect(() => {
    let cancelled = false;
    const open = (d: Dialog) => {
      if (!cancelled) setDialog(d);
    };
    if (payParam && canReceive) {
      void (payParam === "new" ? Promise.resolve(null) : lookup(payParam)).then((c) =>
        open({ kind: "receive", customer: c }),
      );
    } else if (supplierParam && canSupplier) {
      const id = /^\d+$/.test(supplierParam) ? Number(supplierParam) : null;
      void Promise.resolve().then(() => open({ kind: "supplier", supplierId: id }));
    } else if (factoryParam && canFactory) {
      void Promise.resolve().then(() => open({ kind: "factory" }));
    }
    return () => {
      cancelled = true;
    };
  }, [payParam, supplierParam, factoryParam, canReceive, canSupplier, canFactory]);

  const close = () => {
    setDialog(null);
    clearLinkParams();
  };
  const show = (d: Dialog, t: Direction) => {
    setTab(t);
    setDialog(d);
  };

  return (
    <>
      <PageActions>
        <Can permission={P.BILLING_EDIT}>
          <button
            className="btn primary"
            type="button"
            onClick={() => show({ kind: "receive", customer: null }, "in")}
          >
            + Receive payment
          </button>
        </Can>
        <Can permission={P.PURCHASING_EDIT}>
          <button
            className="btn"
            type="button"
            onClick={() => show({ kind: "supplier", supplierId: null }, "out")}
          >
            Pay supplier
          </button>
        </Can>
        <Can permission={P.PRODUCTION_EDIT}>
          <button className="btn" type="button" onClick={() => show({ kind: "factory" }, "out")}>
            Pay factory
          </button>
        </Can>
      </PageActions>

      <Tabs
        tabs={[
          { id: "in", label: "Money in" },
          { id: "out", label: "Money out" },
        ]}
        value={tab}
        onChange={setTab}
      />
      <MoneyTab key={tab} direction={tab} today={today} onOpenCustomer={setPanelId} />

      <CustomerPanel customerId={panelId} onClose={() => setPanelId(null)} />
      {dialog?.kind === "receive" && (
        <ReceivePaymentDialog
          initialCustomer={dialog.customer}
          today={today}
          onClose={close}
          onSaved={(saved) => setDialog({ kind: "saved", saved })}
        />
      )}
      {dialog?.kind === "saved" && <PaymentSavedDialog saved={dialog.saved} onClose={close} />}
      {dialog?.kind === "supplier" && (
        <PaySupplierDialog supplierId={dialog.supplierId} today={today} onClose={close} />
      )}
      {dialog?.kind === "factory" && <PayFactoryLoader today={today} onClose={close} />}
    </>
  );
}

/** The Pay factory pop-up lists the factories with what is owed to each. */
function PayFactoryLoader({ today, onClose }: { today: string; onClose: () => void }) {
  const summary = useProductionSummary();
  if (!summary.data) return null;
  return <FactoryPaymentDialog factories={summary.data.factories} today={today} onClose={onClose} />;
}

/** First day of the month of {@code today} (yyyy-mm-dd). */
function monthStart(today: string): string {
  return today.slice(0, 8) + "01";
}

const NOTE = {
  in: "Payments received and money paid on bills (Paid now on a sale, Enter Bills, My Deliveries).",
  out: "Supplier and factory payments, and expenses (expenses are added on the Expenses page).",
} as const;

function MoneyTab({
  direction,
  today,
  onOpenCustomer,
}: {
  direction: Direction;
  today: string;
  onOpenCustomer: (id: number) => void;
}) {
  const [from, setFrom] = useState<string | null>(monthStart(today));
  const [to, setTo] = useState<string | null>(today);
  const [search, setSearch] = useState("");
  const q = useDebounced(search, 300);
  const list = useMoney(direction, { from, to, q });
  const data = list.data;
  const inTab = direction === "in";

  const columns: Column<MoneyRow>[] = [
    { key: "date", header: "Date", cell: (r) => formatDate(r.date) },
    { key: "ref", header: "Ref", cell: (r) => <span className="mono">{r.ref}</span> },
    { key: "kind", header: "Type", cell: (r) => r.kindLabel },
    {
      key: "party",
      header: inTab ? "Customer" : "Paid to",
      cell: (r) => (
        <>
          {inTab && r.partyId ? (
            <button type="button" className="btn link" onClick={() => onOpenCustomer(r.partyId!)}>
              {r.party}
            </button>
          ) : (
            r.party
          )}
          {r.note && <div className="small muted">{r.note}</div>}
        </>
      ),
    },
    {
      key: "method",
      header: "Paid by",
      cell: (r) => (
        <>
          {r.methodLabel}
          {r.reference && <div className="small muted">{r.reference}</div>}
        </>
      ),
    },
    { key: "amount", header: "Amount", align: "num", cell: (r) => formatMoney(r.amount) },
    { key: "by", header: "Entered by", cell: (r) => <span className="small">{r.enteredBy}</span> },
  ];

  return (
    <div className="card">
      <div className="toolbar">
        <div className="field">
          <label htmlFor="money-from">From</label>
          <DateField id="money-from" value={from} onChange={setFrom} today={today} />
        </div>
        <div className="field">
          <label htmlFor="money-to">To</label>
          <DateField id="money-to" value={to} onChange={setTo} today={today} />
        </div>
        <div className="field grow">
          <label htmlFor="money-q">Search</label>
          <input
            id="money-q"
            placeholder={
              inTab ? "Customer, bill or receipt no., reference" : "Supplier, factory, expense, ref no."
            }
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>
      <p className="small muted">{NOTE[direction]}</p>
      {list.isError ? (
        <div className="banner bad">{list.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={data?.items}
          loading={list.isPending}
          rowKey={(r) => `${r.kind}-${r.id}`}
          empty={inTab ? "No money in for these dates." : "No money out for these dates."}
          footer={
            data && data.count > 0
              ? [
                  `${data.count} ${data.count === 1 ? "payment" : "payments"}`,
                  "",
                  "",
                  "",
                  "Total",
                  <b key="t">{formatMoney(data.total)}</b>,
                  "",
                ]
              : undefined
          }
        />
      )}
      {data && data.count > data.items.length && (
        <p className="small muted">
          Showing the newest {data.items.length} of {data.count} – choose fewer dates to see the rest. The
          total is for all {data.count}.
        </p>
      )}
    </div>
  );
}
