"use client";

/*
 * Monthly invoices (FR-49, prototype billing.html): month (this and the 3 before, default last month), one row per Monthly
 * bill customer – deliveries, this month, previous balance, payments, total due, Generated + number / Not generated,
 * View / Print. Generate invoices (confirm) makes one invoice per customer for the month; the toast says how many were
 * made and why the others were skipped. The current month can be viewed but not invoiced until it has ended (N-13). The
 * invoice pop-up lists every line with the totals and prints the PDF.
 */
import { useState } from "react";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { FormDialog } from "@/components/shared/FormDialog";
import { Badge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import type { GenerateResult, InvoiceMonthRow } from "@/lib/api/types";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { invoiceUrl, openPdf, useGenerateInvoices, useInvoice, useInvoicePreview } from "./api";

/** Amount without "Rs." for the invoice lines: "1,350.00", "-700.00". */
const plain = (v: number | string) => formatMoney(v).replace("Rs. ", "");

/** "1 invoice generated, 5 skipped (No deliveries and nothing due)". */
export function generatedText(r: GenerateResult): string {
  const n = r.created.length;
  let text = `${n} invoice${n === 1 ? "" : "s"} generated`;
  if (r.skipped.length) {
    const reasons = new Map<string, number>();
    r.skipped.forEach((s) => reasons.set(s.reason, (reasons.get(s.reason) ?? 0) + 1));
    const why =
      reasons.size === 1
        ? [...reasons.keys()][0]
        : [...reasons].map(([reason, count]) => `${reason}: ${count}`).join("; ");
    text += `, ${r.skipped.length} skipped (${why})`;
  }
  return text;
}

/** The first day after the month: "2026-11-01" for "2026-10". */
function nextMonthStart(period: string): string {
  const [y, m] = period.split("-").map(Number);
  return m === 12 ? `${y + 1}-01-01` : `${y}-${String(m + 1).padStart(2, "0")}-01`;
}

export function InvoicesTab({
  onOpen,
  onView,
  today = todayIso(),
}: {
  onOpen: (customerId: number) => void;
  onView: (invoiceId: number) => void;
  today?: string;
}) {
  const [period, setPeriod] = useState<string | null>(null);
  const preview = useInvoicePreview(period);
  const generate = useGenerateInvoices();
  const [confirm, setConfirm] = useState(false);
  const p = preview.data;
  const notEnded = !!p && p.period === today.slice(0, 7);

  function runGenerate() {
    if (!p) return;
    generate.mutate(p.period, {
      onSuccess: (r) => {
        toast(generatedText(r), "ok");
        setConfirm(false);
      },
      onError: (err) => {
        toast(errorText(err), "bad");
        setConfirm(false);
      },
    });
  }

  const columns: Column<InvoiceMonthRow>[] = [
    {
      key: "customer",
      header: "Customer",
      cell: (r) => (
        <>
          <button
            type="button"
            className="btn link"
            style={{ fontWeight: 600 }}
            onClick={() => onOpen(r.customerId)}
          >
            {r.name}
          </button>
          <div className="small muted">
            {r.code}
            {r.status !== "ACTIVE" && " · Inactive"}
          </div>
        </>
      ),
    },
    { key: "deliveries", header: "Deliveries", align: "num", cell: (r) => r.deliveries },
    { key: "month", header: "This month", align: "num", cell: (r) => formatMoney(r.thisMonth) },
    { key: "opening", header: "Previous balance", align: "num", cell: (r) => formatMoney(r.opening) },
    {
      key: "payments",
      header: "Payments",
      align: "num",
      cell: (r) => (Number(r.payments) ? "– " + formatMoney(r.payments) : ""),
    },
    { key: "due", header: "Total due", align: "num", cell: (r) => <b>{formatMoney(r.totalDue)}</b> },
    {
      key: "invoice",
      header: "Invoice",
      cell: (r) =>
        r.invoice ? (
          <>
            <Badge kind="ok">Generated</Badge> <span className="mono small">{r.invoice.invoiceNo}</span>
          </>
        ) : (
          <Badge kind="warn">Not generated</Badge>
        ),
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (r) =>
        r.invoice && (
          <button className="btn sm" type="button" onClick={() => onView(r.invoice!.id)}>
            View / Print
          </button>
        ),
    },
  ];

  return (
    <>
      <div className="card">
        <div className="toolbar" style={{ margin: 0 }}>
          <div className="field">
            <label htmlFor="inv-period">Month</label>
            <select id="inv-period" value={p?.period ?? ""} onChange={(e) => setPeriod(e.target.value)}>
              {p?.periods.map((o) => (
                <option key={o.period} value={o.period}>
                  {o.label}
                </option>
              ))}
            </select>
          </div>
          <span style={{ flex: 1 }} />
          <Can permission={P.BILLING_EDIT}>
            <button
              className="btn primary"
              type="button"
              disabled={!p || p.toGenerate === 0 || notEnded}
              onClick={() => setConfirm(true)}
            >
              Generate invoices
            </button>
          </Can>
        </div>
        <p className="small muted" style={{ marginTop: 6 }}>
          Monthly bill customers get one invoice per month listing every delivery.
        </p>
        {notEnded && (
          <div className="banner info" style={{ marginTop: 6 }}>
            {p.periodLabel} has not ended yet, so these are the totals so far. Its invoices can be made from{" "}
            {formatDate(nextMonthStart(p.period))}.
          </div>
        )}
      </div>
      <div className="card">
        {preview.isError ? (
          <div className="banner bad">{preview.error.message}</div>
        ) : (
          <DataTable
            columns={columns}
            rows={p?.rows}
            loading={preview.isPending}
            rowKey={(r) => r.customerId}
            empty="No Monthly bill customers."
          />
        )}
      </div>
      {confirm && p && (
        <ConfirmDialog
          open
          onOpenChange={(o) => !o && setConfirm(false)}
          title={`Generate invoices for ${p.periodLabel}?`}
          confirmLabel="Generate"
          busy={generate.isPending}
          onConfirm={runGenerate}
        >
          <p>
            {p.toGenerate} Monthly bill customer{p.toGenerate === 1 ? "" : "s"} without an invoice for{" "}
            {p.periodLabel}. Each gets an invoice listing all deliveries in the month; customers with nothing
            to bill are skipped.
          </p>
        </ConfirmDialog>
      )}
    </>
  );
}

export function InvoiceDialog({ invoiceId, onClose }: { invoiceId: number; onClose: () => void }) {
  const inv = useInvoice(invoiceId);
  const v = inv.data;
  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title={v ? `Invoice ${v.invoiceNo}` : "Invoice"}
      extraButtons={
        <button type="button" className="btn primary" onClick={() => openPdf(invoiceUrl(invoiceId))}>
          Print / PDF
        </button>
      }
    >
      {inv.isError && <div className="banner bad">{inv.error.message}</div>}
      {!v ? (
        !inv.isError && <p className="muted">Loading…</p>
      ) : (
        <>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 16, flexWrap: "wrap" }}>
            <p style={{ margin: 0 }}>
              <span className="muted">Bill to</span>
              <br />
              <b>{v.customer.name}</b> ({v.customer.code})
              <br />
              {v.customer.address}, {v.customer.area}
              <br />
              {v.customer.phone}
            </p>
            <div className="summary" style={{ minWidth: 240 }}>
              <span className="muted">Date</span>
              <b>{formatDate(v.invoiceDate)}</b>
              <span className="muted">Period</span>
              <b>
                {formatDate(v.from)} – {formatDate(v.to)}
              </b>
              <span className="muted">Due</span>
              <b>{formatDate(v.dueDate)}</b>
            </div>
          </div>
          <div className="table-wrap" style={{ marginTop: 12 }}>
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Bill</th>
                  <th>Description</th>
                  <th className="num">Qty</th>
                  <th className="num">Unit price</th>
                  <th className="num">Amount (Rs.)</th>
                </tr>
              </thead>
              <tbody>
                {v.lines.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="empty-state">
                      No deliveries this month
                    </td>
                  </tr>
                ) : (
                  v.lines.map((l, i) => (
                    <tr key={i}>
                      <td>{formatDate(l.date)}</td>
                      <td className="mono">{l.billNo}</td>
                      <td>{l.description}</td>
                      <td className="num">{l.qty ?? ""}</td>
                      <td className="num">{l.unitPrice == null ? "" : plain(l.unitPrice)}</td>
                      <td className="num">{plain(l.amount)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 10 }}>
            <div className="summary" style={{ minWidth: 300 }}>
              <span>Previous balance</span>
              <b className="num">{formatMoney(v.opening)}</b>
              <span>Payments received</span>
              <b className="num">– {formatMoney(v.payments)}</b>
              <span>This month</span>
              <b className="num">{formatMoney(v.amount)}</b>
              <span className="total">Total due by {formatDate(v.dueDate)}</span>
              <b className="num total" data-testid="inv-total">
                {formatMoney(v.totalDue)}
              </b>
            </div>
          </div>
          <p className="small muted" style={{ marginTop: 12 }}>
            VAT not applied (to be confirmed). Please quote {v.invoiceNo} with your payment.
          </p>
        </>
      )}
    </FormDialog>
  );
}
