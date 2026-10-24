"use client";

/*
 * Customer Quotations (M16, prototype sales/quotations.html, FR-64 – FR-66): + New quotation (Admin); KPIs (waiting for an
 * answer, drafts, accepted, expired / not accepted); search and status (incl. Expired); list with the next step and Open.
 * Links: ?id=12 opens a quotation; ?new=1&cust=C0004 starts one for a customer (customer panel "Quotation").
 * Accountant: view and print only.
 */
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Can, useCan } from "@/features/auth/access";
import { findMatch } from "@/features/customers/CustomerPicker";
import { api } from "@/lib/api/client";
import type { CqListRow, CqView, CustomerLookupRow } from "@/lib/api/types";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useDebounced } from "@/lib/useDebounced";
import { useQuotations } from "./api";
import { QuotationForm } from "./QuotationForm";
import { QuotationView } from "./QuotationView";

const STATUSES = [
  { value: "DRAFT", label: "Draft" },
  { value: "SENT", label: "Sent" },
  { value: "EXPIRED", label: "Expired" },
  { value: "ACCEPTED", label: "Accepted" },
  { value: "REJECTED", label: "Not accepted" },
];

type Dialog =
  | { kind: "view"; id: number }
  | {
      kind: "form";
      mode: "new" | "edit" | "copy";
      source: CqView | null;
      customer?: CustomerLookupRow | null;
    }
  | null;

export function Quotations({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const canEdit = useCan(P.QUOTATIONS_EDIT);
  const [search, setSearch] = useState("");
  const q = useDebounced(search.trim(), 250);
  const [status, setStatus] = useState("");
  const list = useQuotations(status, q);
  const [dialog, setDialog] = useState<Dialog>(null);
  const k = list.data?.kpis;

  // ?id=12 opens a quotation; ?new=1&cust=C0004 starts one for that customer.
  const idParam = params.get("id");
  const newParam = params.get("new");
  const custParam = params.get("cust");
  useEffect(() => {
    let cancelled = false;
    const open = (d: Dialog) => !cancelled && setDialog(d);
    if (idParam && /^\d+$/.test(idParam)) {
      void Promise.resolve().then(() => open({ kind: "view", id: Number(idParam) }));
    } else if (newParam === "1" && canEdit) {
      const find = custParam
        ? api<CustomerLookupRow[]>("/customers/lookup", { query: { q: custParam } })
            .then(
              (rows) =>
                findMatch(custParam, rows) ?? rows.find((r) => r.code === custParam.toUpperCase()) ?? null,
            )
            .catch(() => null)
        : Promise.resolve(null);
      void find.then((c) => open({ kind: "form", mode: "new", source: null, customer: c }));
    }
    return () => {
      cancelled = true;
    };
  }, [idParam, newParam, custParam, canEdit]);

  const columns: Column<CqListRow>[] = [
    {
      key: "no",
      header: "Quotation",
      cell: (r) => (
        <>
          <b className="mono">{r.cqNo}</b>
          <div className="small muted">{formatDate(r.quoteDate)}</div>
        </>
      ),
    },
    {
      key: "to",
      header: "To",
      cell: (r) => (
        <>
          <b>{r.toName}</b>{" "}
          {r.customerCode ? <Badge kind="info">{r.customerCode}</Badge> : <Badge>New</Badge>}
          <div className="small muted">{[r.contact, r.designation].filter(Boolean).join(", ")}</div>
        </>
      ),
    },
    {
      key: "subject",
      header: "Subject",
      cell: (r) => (
        <>
          {r.subject || "–"}
          <div className="small muted">
            {r.lineCount} line{r.lineCount === 1 ? "" : "s"} · {r.customerTypeName} prices
          </div>
        </>
      ),
    },
    { key: "total", header: "Total", align: "num", cell: (r) => formatMoney(r.total) },
    { key: "valid", header: "Valid until", cell: (r) => formatDate(r.validUntil) },
    {
      key: "status",
      header: "Status",
      cell: (r) => (
        <>
          <StatusBadge status={r.shownLabel} />
          {r.sentViaText && r.shown === "SENT" && <div className="small muted">{r.sentViaText}</div>}
        </>
      ),
    },
    { key: "next", header: "Next step", cell: (r) => <span className="small">{r.nextStep}</span> },
    {
      key: "open",
      header: "",
      className: "actions",
      cell: (r) => (
        <button
          className="btn sm primary"
          type="button"
          onClick={() => setDialog({ kind: "view", id: r.id })}
        >
          Open
        </button>
      ),
    },
  ];

  return (
    <>
      <Can permission={P.QUOTATIONS_EDIT}>
        <PageActions>
          <button
            className="btn primary"
            type="button"
            onClick={() => setDialog({ kind: "form", mode: "new", source: null })}
          >
            + New quotation
          </button>
        </PageActions>
      </Can>

      {k && (
        <KpiGrid>
          <KpiCard label="Waiting for an answer" value={k.waiting} hint={formatMoney(k.waitingTotal)} />
          <KpiCard label="Drafts" value={k.drafts} />
          <KpiCard label="Accepted" value={k.accepted} hint={formatMoney(k.acceptedTotal)} />
          <KpiCard label="Expired / not accepted" value={k.expiredOrRejected} />
        </KpiGrid>
      )}
      <div className="card">
        <div className="toolbar">
          <div className="field grow">
            <label htmlFor="cq-q">Search</label>
            <input
              id="cq-q"
              placeholder="Quotation no., organisation, contact or subject"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="cq-st">Status</label>
            <select id="cq-st" value={status} onChange={(e) => setStatus(e.target.value)}>
              <option value="">All</option>
              {STATUSES.map((s) => (
                <option key={s.value} value={s.value}>
                  {s.label}
                </option>
              ))}
            </select>
          </div>
        </div>
        {list.isError ? (
          <div className="banner bad">{list.error.message}</div>
        ) : (
          <DataTable
            columns={columns}
            rows={list.data?.items}
            loading={list.isPending}
            rowKey={(r) => r.id}
            empty="No quotations match."
          />
        )}
      </div>

      {dialog?.kind === "view" && (
        <QuotationView
          id={dialog.id}
          today={today}
          onClose={() => setDialog(null)}
          onEdit={(src) => setDialog({ kind: "form", mode: "edit", source: src })}
          onCopy={(src) => setDialog({ kind: "form", mode: "copy", source: src })}
        />
      )}
      {dialog?.kind === "form" && (
        <QuotationForm
          key={`${dialog.mode}-${dialog.source?.id ?? "new"}`}
          mode={dialog.mode}
          source={dialog.source}
          initialCustomer={dialog.customer}
          today={today}
          onClose={() => setDialog(null)}
          onSaved={(saved) => setDialog({ kind: "view", id: saved.quotation.id })}
        />
      )}
    </>
  );
}
