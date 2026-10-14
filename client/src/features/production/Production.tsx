"use client";

/*
 * Filling Factory (M10, prototype production/production.html, FR-23 – FR-26): actions Send empties and Receive filled
 * bottles (Admin); KPIs (empty and at factory per bottle type, owed to each factory); tabs Batches · Factory payments.
 * ?send=1 opens Send empties (links from the Stock page and Delivery Planning). Accountant: view only.
 */
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { MoneyText } from "@/components/shared/MoneyText";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { Can, useCan } from "@/features/auth/access";
import type { BatchView, DispatchView, FactoryMonth, FactoryPaymentRow } from "@/lib/api/types";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useBatches, useFactoryBalances, useFactoryPayments, useProductionSummary } from "./api";
import {
  DispatchSentDialog,
  FactoryPaymentDialog,
  openDispatchNote,
  ReceiveFilledDialog,
  SendEmptiesDialog,
} from "./ProductionDialogs";

const TABS = ["batches", "payments"] as const;

type Dialog =
  | { kind: "send" }
  | { kind: "sent"; dispatch: DispatchView }
  | { kind: "receive"; batchId: number | null }
  | { kind: "pay" }
  | null;

export function Production({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const canEdit = useCan(P.PRODUCTION_EDIT);
  const [tab, setTab] = useHashTab(TABS);
  const summary = useProductionSummary();
  const batches = useBatches();
  const [dialog, setDialog] = useState<Dialog>(() =>
    params.get("send") === "1" && canEdit ? { kind: "send" } : null,
  );
  const open = (batches.data ?? []).filter((b) => b.atFactory > 0);
  const s = summary.data;

  return (
    <>
      <Can permission={P.PRODUCTION_EDIT}>
        <PageActions>
          <button
            className="btn primary"
            type="button"
            disabled={!s}
            onClick={() => setDialog({ kind: "send" })}
          >
            Send empties
          </button>
          <button
            className="btn primary"
            type="button"
            disabled={!batches.data}
            onClick={() => setDialog({ kind: "receive", batchId: null })}
          >
            Receive filled bottles
          </button>
        </PageActions>
      </Can>

      {summary.isError && <div className="banner bad">{summary.error.message}</div>}
      {s && (
        <KpiGrid>
          {s.bottles.flatMap((b) => [
            <KpiCard key={b.bottleTypeCode + "e"} label={`Empty ${b.label} in store`} value={b.empty} />,
            <KpiCard key={b.bottleTypeCode + "f"} label={`${b.label} at factory`} value={b.atFactory} />,
          ])}
          {s.factories.map((f) => (
            <KpiCard key={f.id} label={`Owed to ${f.name}`} value={formatMoney(f.owed)} />
          ))}
        </KpiGrid>
      )}

      <Tabs
        tabs={[
          { id: "batches", label: "Batches" },
          { id: "payments", label: "Factory payments" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "batches" && (
        <BatchesTab
          batches={batches.data}
          loading={batches.isPending}
          error={batches.isError ? batches.error.message : null}
          onReceive={(id) => setDialog({ kind: "receive", batchId: id })}
        />
      )}
      {tab === "payments" && <PaymentsTab onPay={() => setDialog({ kind: "pay" })} />}

      {dialog?.kind === "send" && s && (
        <SendEmptiesDialog
          bottles={s.bottles}
          today={today}
          onClose={() => setDialog(null)}
          onSent={(d) => setDialog({ kind: "sent", dispatch: d })}
        />
      )}
      {dialog?.kind === "sent" && (
        <DispatchSentDialog dispatch={dialog.dispatch} onClose={() => setDialog(null)} />
      )}
      {dialog?.kind === "receive" && (
        <ReceiveFilledDialog
          open={open}
          batchId={dialog.batchId}
          today={today}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "pay" && <PayDialogLoader today={today} onClose={() => setDialog(null)} />}
    </>
  );
}

function BatchesTab({
  batches,
  loading,
  error,
  onReceive,
}: {
  batches: BatchView[] | undefined;
  loading: boolean;
  error: string | null;
  onReceive: (id: number) => void;
}) {
  const columns: Column<BatchView>[] = [
    {
      key: "batch",
      header: "Batch",
      cell: (b) => (
        <>
          <b className="mono">{b.batchNo}</b>
          <div className="small muted mono">{b.dispatchNo}</div>
        </>
      ),
    },
    { key: "sent", header: "Sent", cell: (b) => formatDate(b.date) },
    { key: "factory", header: "Factory", cell: (b) => b.factory.name },
    { key: "bottle", header: "Bottle", cell: (b) => b.bottleLabel },
    { key: "qty", header: "Qty sent", align: "num", cell: (b) => b.qtySent },
    { key: "filled", header: "Filled back", align: "num", cell: (b) => b.filled },
    { key: "rejected", header: "Rejected", align: "num", cell: (b) => b.rejected || "" },
    {
      key: "left",
      header: "Still at factory",
      align: "num",
      cell: (b) => (b.atFactory ? <b style={{ color: "var(--warn)" }}>{b.atFactory}</b> : "0"),
    },
    { key: "cost", header: "Filling cost", align: "num", cell: (b) => formatMoney(b.cost) },
    { key: "status", header: "Status", cell: (b) => <StatusBadge status={b.statusLabel} /> },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (b) => (
        <>
          {b.atFactory > 0 && (
            <Can permission={P.PRODUCTION_EDIT}>
              <button className="btn sm" type="button" onClick={() => onReceive(b.id)}>
                Receive
              </button>{" "}
            </Can>
          )}
          <button
            className="btn sm"
            type="button"
            title="Print the dispatch note"
            onClick={() => openDispatchNote(b.dispatchNo)}
          >
            Print
          </button>
        </>
      ),
    },
  ];
  return (
    <div className="card">
      <p className="small muted">
        Empties go to the factory in batches and come back filled and sealed. The factory charges a fixed
        price per bottle filled.
      </p>
      {error ? (
        <div className="banner bad">{error}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={batches}
          loading={loading}
          rowKey={(b) => b.id}
          empty="No batches yet."
        />
      )}
    </div>
  );
}

function PaymentsTab({ onPay }: { onPay: () => void }) {
  const balances = useFactoryBalances();
  const payments = useFactoryPayments();
  const monthColumns: Column<FactoryMonth>[] = [
    { key: "month", header: "Month", cell: (m) => m.periodLabel },
    { key: "factory", header: "Factory", cell: (m) => m.factoryName },
    { key: "filled", header: "Bottles filled", align: "num", cell: (m) => m.filled.toLocaleString("en-US") },
    { key: "rejected", header: "Rejected (not charged)", align: "num", cell: (m) => m.rejected },
    { key: "cost", header: "Filling cost", align: "num", cell: (m) => formatMoney(m.cost) },
    { key: "paid", header: "Paid", align: "num", cell: (m) => formatMoney(m.paid) },
    { key: "status", header: "Status", cell: (m) => <StatusBadge status={m.statusLabel} /> },
  ];
  const payColumns: Column<FactoryPaymentRow>[] = [
    { key: "ref", header: "Ref", cell: (p) => <span className="mono">{p.fpNo}</span> },
    { key: "date", header: "Date", cell: (p) => formatDate(p.date) },
    { key: "factory", header: "Factory", cell: (p) => p.factory.name },
    { key: "for", header: "For", cell: (p) => p.periodLabel },
    {
      key: "method",
      header: "Method",
      cell: (p) => (
        <>
          {p.methodLabel}
          {p.reference && <div className="small muted">{p.reference}</div>}
        </>
      ),
    },
    { key: "amount", header: "Amount", align: "num", cell: (p) => <MoneyText value={p.amount} /> },
  ];
  return (
    <>
      <div className="card">
        <div className="card-head">
          <h2>Filling cost by month</h2>
          <Can permission={P.PRODUCTION_EDIT}>
            <button className="btn primary" type="button" disabled={!balances.data} onClick={onPay}>
              + Record payment
            </button>
          </Can>
        </div>
        {balances.isError ? (
          <div className="banner bad">{balances.error.message}</div>
        ) : (
          <DataTable
            columns={monthColumns}
            rows={balances.data}
            loading={balances.isPending}
            rowKey={(m) => `${m.factoryId}-${m.period}`}
            empty="No filling cost yet."
          />
        )}
      </div>
      <div className="card">
        <h2>Payments made</h2>
        {payments.isError ? (
          <div className="banner bad">{payments.error.message}</div>
        ) : (
          <DataTable
            columns={payColumns}
            rows={payments.data}
            loading={payments.isPending}
            rowKey={(p) => p.id}
            empty="No payments yet."
          />
        )}
      </div>
    </>
  );
}

/** The payment pop-up needs the unpaid months (oldest first, as in the prototype). */
function PayDialogLoader({ today, onClose }: { today: string; onClose: () => void }) {
  const balances = useFactoryBalances();
  if (!balances.data) return null;
  const unpaid = balances.data
    .filter((m) => m.status !== "PAID")
    .slice()
    .reverse();
  return <FactoryPaymentDialog unpaid={unpaid} today={today} onClose={onClose} />;
}
