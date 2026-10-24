"use client";

/*
 * Expenses (M15, prototype expenses/expenses.html, FR-62, FR-63, BR-19): actions Expense types and + Add expense (Admin,
 * Accountant); month, type and search with Print month; KPIs (month total, previous month and the difference, entries,
 * biggest type); tabs Monthly expenses (list with total + by-type bars) · Month by month (type × last 6 months – click a
 * month to open it – Export Excel). Expenses count in the month they are for. ?new=1 opens Add expense, ?month=2026-09
 * opens that month.
 */
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { ReasonDialog } from "@/components/shared/ReasonDialog";
import { Badge } from "@/components/shared/StatusBadge";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { Can, useCan } from "@/features/auth/access";
import { exportUrl } from "@/features/reports/api";
import type { ExpenseMonthView, ExpenseRow, ExpenseSummary } from "@/lib/api/types";
import { daysInMonth, formatDate, formatMoney, formatNumber, MONTHS, todayIso } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useDebounced } from "@/lib/useDebounced";
import { cn } from "@/lib/utils";
import {
  expenseStatementUrl,
  useDeleteExpense,
  useExpenseMonth,
  useExpenseSummary,
  useExpenseTypes,
} from "./api";
import { ExpenseDialog, ExpenseTypesDialog } from "./ExpenseDialogs";

const TABS = ["list", "summary"] as const;

type Dialog =
  | { kind: "edit"; expense: ExpenseRow | null }
  | { kind: "delete"; expense: ExpenseRow }
  | { kind: "types" }
  | null;

/** "2026-10-02" → "Oct" (the month an expense was paid in, when it differs). */
function shortMonth(iso: string): string {
  return MONTHS[Number(iso.slice(5, 7)) - 1].slice(0, 3);
}

/** "September 2026" → "September ’26" (month-by-month headers). */
function shortLabel(label: string): string {
  return label.replace(" 20", " ’");
}

export function Expenses({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const canEdit = useCan(P.EXPENSES_EDIT);
  const [tab, setTab] = useHashTab(TABS);
  const [month, setMonth] = useState(() => params.get("month") ?? "");
  const [type, setType] = useState("");
  const [search, setSearch] = useState("");
  const q = useDebounced(search.trim(), 250);
  const view = useExpenseMonth({ month, type, q });
  const types = useExpenseTypes();
  const summary = useExpenseSummary(tab === "summary");
  const remove = useDeleteExpense();
  const [dialog, setDialog] = useState<Dialog>(() =>
    params.get("new") === "1" && canEdit ? { kind: "edit", expense: null } : null,
  );
  const v = view.data;

  function deleteExpense(e: ExpenseRow, reason: string) {
    remove.mutate(
      { id: e.id, reason, version: e.version },
      {
        onSuccess: () => {
          toast("Expense deleted", "ok");
          setDialog(null);
        },
        onError: (err) => toast(errorText(err), "bad"),
      },
    );
  }

  const columns: Column<ExpenseRow>[] = [
    { key: "no", header: "No.", cell: (e) => <span className="nowrap">{e.expenseNo}</span> },
    {
      key: "paid",
      header: "Paid on",
      cell: (e) => (
        <>
          <span className="nowrap">{formatDate(e.paidDate)}</span>
          {e.paidInOtherMonth && (
            <div className="small muted" title="Paid in a different month">
              paid {shortMonth(e.paidDate)}
            </div>
          )}
        </>
      ),
    },
    { key: "type", header: "Type", cell: (e) => <Badge>{e.typeName}</Badge> },
    {
      key: "details",
      header: "Details",
      cell: (e) => (
        <>
          {e.description || "–"}
          <div className="small muted">
            {e.paidTo ?? ""}
            {e.billNo && ` · bill ${e.billNo}`}
          </div>
        </>
      ),
    },
    {
      key: "by",
      header: "Paid by",
      cell: (e) => (
        <>
          {e.methodLabel}
          {e.reference && <div className="small muted">{e.reference}</div>}
        </>
      ),
    },
    { key: "amount", header: "Amount", align: "num", cell: (e) => formatMoney(e.amount) },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (e) =>
        canEdit && (
          <>
            <button className="btn sm" type="button" onClick={() => setDialog({ kind: "edit", expense: e })}>
              Edit
            </button>{" "}
            <button
              className="btn sm danger"
              type="button"
              onClick={() => setDialog({ kind: "delete", expense: e })}
            >
              Delete
            </button>
          </>
        ),
    },
  ];

  const prev = Number(v?.previousTotal ?? 0);
  const diff = Number(v?.difference ?? 0);
  const n = v?.items.length ?? 0;

  return (
    <>
      <Can permission={P.EXPENSES_EDIT}>
        <PageActions>
          <button
            className="btn"
            type="button"
            disabled={!types.data}
            onClick={() => setDialog({ kind: "types" })}
          >
            Expense types
          </button>
          <button
            className="btn primary"
            type="button"
            disabled={!v || !types.data}
            onClick={() => setDialog({ kind: "edit", expense: null })}
          >
            + Add expense
          </button>
        </PageActions>
      </Can>

      <div className="card no-print">
        <div className="toolbar">
          <div className="field">
            <label htmlFor="ex-f-month">Month</label>
            <select id="ex-f-month" value={v?.month ?? month} onChange={(e) => setMonth(e.target.value)}>
              {v?.months.map((m) => (
                <option key={m.month} value={m.month}>
                  {m.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="ex-f-type">Type</label>
            <select id="ex-f-type" value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">All types</option>
              {types.data?.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field grow">
            <label htmlFor="ex-f-q">Search</label>
            <input
              id="ex-f-q"
              placeholder="Description, paid to, bill no. or reference"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <button
            className="btn"
            type="button"
            disabled={!v}
            onClick={() => v && window.open(expenseStatementUrl(v.month), "_blank", "noopener")}
          >
            Print month
          </button>
        </div>
      </div>

      {view.isError && <div className="banner bad">{view.error.message}</div>}
      {v && (
        <KpiGrid>
          <KpiCard label={`Total for ${v.monthLabel}`} value={formatMoney(v.total)} />
          <KpiCard
            label={v.previousLabel}
            value={formatMoney(v.previousTotal)}
            hint={
              prev ? (
                <span style={diff > 0 ? { color: "var(--bad)" } : undefined}>
                  {diff >= 0 ? "+" : "−"}
                  {formatMoney(Math.abs(diff))} this month
                </span>
              ) : undefined
            }
          />
          <KpiCard label="Entries" value={v.entries} />
          <KpiCard
            label="Biggest expense type"
            value={
              v.byType[0] ? (
                <span style={{ fontSize: "1rem" }}>
                  {v.byType[0].name}
                  <div className="small muted">{formatMoney(v.byType[0].total)}</div>
                </span>
              ) : (
                "–"
              )
            }
          />
        </KpiGrid>
      )}

      <Tabs
        tabs={[
          { id: "list", label: "Monthly expenses" },
          { id: "summary", label: "Month by month" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "list" && (
        <div className="grid side" style={{ alignItems: "start" }}>
          <div className="card">
            <h2>Expenses for {v?.monthLabel ?? ""}</h2>
            <DataTable
              columns={columns}
              rows={v?.items}
              loading={view.isPending}
              rowKey={(e) => e.id}
              empty="No expenses entered for this month yet."
              footer={[
                "Total",
                "",
                "",
                `${n} entr${n === 1 ? "y" : "ies"}`,
                "",
                <b key="t">{formatMoney(v?.shownTotal)}</b>,
                "",
              ]}
            />
          </div>
          <div className="card">
            <h2>By type</h2>
            {v && <TypeBars view={v} />}
          </div>
        </div>
      )}
      {tab === "summary" && (
        <SummaryCard
          summary={summary.data}
          loading={summary.isPending}
          error={summary.isError ? summary.error.message : null}
          onOpenMonth={(m) => {
            setMonth(m);
            setTab("list");
          }}
        />
      )}

      {dialog?.kind === "edit" && v && types.data && (
        <ExpenseDialog
          expense={dialog.expense}
          month={v.month}
          months={v.months}
          types={types.data}
          payees={v.payees}
          today={today}
          onClose={() => setDialog(null)}
          onSaved={(saved) => {
            // Show the month the expense is for (prototype).
            if (saved.expense.forMonth !== v.month) setMonth(saved.expense.forMonth);
          }}
        />
      )}
      {dialog?.kind === "types" && types.data && v && (
        <ExpenseTypesDialog
          types={types.data}
          monthLabel={v.monthLabel}
          monthTotals={new Map(v.byType.map((t) => [t.typeId, t.total]))}
          onClose={() => setDialog(null)}
        />
      )}
      {dialog?.kind === "delete" && (
        <ReasonDialog
          open
          onOpenChange={(o) => !o && setDialog(null)}
          title={`Delete ${dialog.expense.expenseNo}?`}
          label="Reason for deleting"
          confirmLabel="Delete"
          danger
          busy={remove.isPending}
          onConfirm={(reason) => deleteExpense(dialog.expense, reason)}
        >
          <p>
            {dialog.expense.typeName} {formatMoney(dialog.expense.amount)} for {dialog.expense.forMonthLabel}.
            The reason is kept in the audit log.
          </p>
        </ReasonDialog>
      )}
    </>
  );
}

function TypeBars({ view }: { view: ExpenseMonthView }) {
  if (!view.byType.length) return <p className="empty-state">Nothing yet.</p>;
  const max = Math.max(...view.byType.map((t) => Number(t.total)), 1);
  return (
    <div>
      {view.byType.map((t) => (
        <div key={t.typeId} className="hbar">
          <span>{t.name}</span>
          <div className="track">
            <div className="fill" style={{ width: `${(Number(t.total) / max) * 100}%` }} />
          </div>
          <span className="num">{formatMoney(t.total)}</span>
        </div>
      ))}
    </div>
  );
}

/** The last day of a month ("2026-10" → "2026-10-31"), but not after today. */
function monthEnd(month: string, today = todayIso()): string {
  const [y, m] = month.split("-").map(Number);
  const end = `${month}-${String(daysInMonth(y, m)).padStart(2, "0")}`;
  return end > today ? today : end;
}

function SummaryCard({
  summary,
  loading,
  error,
  onOpenMonth,
}: {
  summary: ExpenseSummary | undefined;
  loading: boolean;
  error: string | null;
  onOpenMonth: (month: string) => void;
}) {
  const months = summary?.months ?? [];
  const amount = (v: number | string) => (Number(v) ? formatNumber(v) : <span className="muted">–</span>);

  // A real Excel file (.xlsx): the Expenses report for the months shown – the same "By type and month" table, and every
  // entry on a second sheet.
  const excelUrl = summary
    ? exportUrl("expenses", "xlsx", `${summary.from}-01`, monthEnd(summary.to))
    : undefined;

  return (
    <div className="card">
      <div className="card-head">
        <h2>Month by month</h2>
        <a className={cn("btn", !summary && "disabled")} href={excelUrl} download>
          Export Excel
        </a>
      </div>
      <p className="small muted">Expenses are counted in the month they are for. Click a month to open it.</p>
      {error ? (
        <div className="banner bad">{error}</div>
      ) : loading || !summary ? (
        <p className="muted">Loading…</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Type</th>
                {months.map((m) => (
                  <th key={m.month} className="num">
                    <button className="btn link" type="button" onClick={() => onOpenMonth(m.month)}>
                      {shortLabel(m.label)}
                    </button>
                  </th>
                ))}
                <th className="num">Total</th>
              </tr>
            </thead>
            <tbody>
              {summary.rows.map((r) => (
                <tr key={r.typeId}>
                  <td>
                    <b>{r.name}</b>
                  </td>
                  {r.amounts.map((a, i) => (
                    <td key={months[i].month} className="num">
                      {amount(a)}
                    </td>
                  ))}
                  <td className="num">
                    <b>{formatNumber(r.total)}</b>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td>Total</td>
                {months.map((m) => (
                  <td key={m.month} className="num">
                    <b>{formatNumber(m.total)}</b>
                  </td>
                ))}
                <td className="num">
                  <b data-testid="summary-total">{formatNumber(summary.total)}</b>
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </div>
  );
}
