"use client";

/*
 * Reports (M12, prototype reports/reports.html, BRD section 8): report (the ones the role may run), From / To with quick
 * ranges, Apply, Export PDF and Export Excel; then KPIs, the chart (Sales by day, Expenses by month) and the tables with their
 * totals. The server makes the report and the exports, so the screen, the PDF and the Excel file show the same data.
 * ?r=profit opens a report.
 */
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { BarChart } from "@/components/shared/BarChart";
import { DateField } from "@/components/shared/DateField";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import type { ReportKpi, ReportTable } from "@/lib/api/types";
import { addDaysIso, formatDate, formatMoney, formatNumber, todayIso } from "@/lib/format";
import { cn } from "@/lib/utils";
import { exportUrl, useReport, useReportList } from "./api";

const isNumber = (v: string) => /^-?\d+(\.\d+)?$/.test(v);
const isDate = (v: string) => /^\d{4}-\d{2}-\d{2}$/.test(v);

/** A value as shown in a table: money "1,350.00", numbers "1,200", dates DD/MM/YYYY; words (e.g. "Total") as they are. */
export function cellText(value: string, type: string): string {
  if (!value) return "";
  if (type === "MONEY" && isNumber(value)) return formatMoney(value).replace("Rs. ", "");
  if (type === "NUMBER" && isNumber(value)) return formatNumber(value);
  if (type === "DATE" && isDate(value)) return formatDate(value);
  return value;
}

export function kpiText(k: ReportKpi): string {
  return k.type === "MONEY" && isNumber(k.value) ? formatMoney(k.value) : cellText(k.value, k.type);
}

const monthStart = (iso: string) => iso.slice(0, 8) + "01";

/** Quick ranges (prototype rangeBar; "Aug – Oct 2026" became "Last 3 months"). */
export function quickRange(kind: string, today: string): [string, string] | null {
  switch (kind) {
    case "today":
      return [today, today];
    case "week":
      return [addDaysIso(today, -6), today];
    case "month":
      return [monthStart(today), today];
    case "last": {
      const start = monthStart(addDaysIso(monthStart(today), -1));
      return [start, addDaysIso(monthStart(today), -1)];
    }
    case "quarter": {
      const [y, m] = today.split("-").map(Number);
      const d = new Date(Date.UTC(y, m - 1 - 2, 1));
      return [d.toISOString().slice(0, 10), today];
    }
    default:
      return null;
  }
}

function TableCard({ t }: { t: ReportTable }) {
  const num = (type: string) => type === "MONEY" || type === "NUMBER";
  return (
    <div className="card">
      <h2>{t.title}</h2>
      <div className="table-wrap report-table">
        <table>
          <thead>
            <tr>
              {t.columns.map((c, i) => (
                <th key={i} className={cn(num(c.type) && "num")}>
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {t.rows.length === 0 ? (
              <tr>
                <td colSpan={t.columns.length} className="empty-state">
                  Nothing in this period.
                </td>
              </tr>
            ) : (
              t.rows.map((row, r) => {
                const bold = t.boldRows.includes(r);
                return (
                  <tr key={r}>
                    {row.map((v, i) => (
                      <td key={i} className={cn(num(t.columns[i]?.type ?? "") && "num")}>
                        {bold ? (
                          <b>{cellText(v, t.columns[i]?.type ?? "TEXT")}</b>
                        ) : (
                          cellText(v, t.columns[i]?.type ?? "TEXT")
                        )}
                      </td>
                    ))}
                  </tr>
                );
              })
            )}
          </tbody>
          {t.footer && t.rows.length > 0 && (
            <tfoot>
              <tr>
                {t.footer.map((v, i) => (
                  <td key={i} className={cn(num(t.columns[i]?.type ?? "") && "num")}>
                    {cellText(v, t.columns[i]?.type ?? "TEXT")}
                  </td>
                ))}
              </tr>
            </tfoot>
          )}
        </table>
      </div>
    </div>
  );
}

export function Reports({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const list = useReportList();
  const [key, setKey] = useState<string | null>(params.get("r"));
  const reports = list.data ?? [];
  const chosen = reports.find((r) => r.key === key)?.key ?? reports[0]?.key ?? null;
  const initial = quickRange("last", today)!;
  const [from, setFrom] = useState<string | null>(initial[0]);
  const [to, setTo] = useState<string | null>(today);
  const [quick, setQuick] = useState("");
  const [applied, setApplied] = useState({ from: initial[0], to: today });
  const [error, setError] = useState("");
  const report = useReport(chosen, applied.from, applied.to);
  const v = report.data;

  function apply(f = from, t = to) {
    if (!f || !t) {
      setError("Enter dates as DD/MM/YYYY.");
      return;
    }
    if (f > t) {
      setError("The From date must be on or before the To date.");
      return;
    }
    setError("");
    setApplied({ from: f, to: t });
  }

  function chooseQuick(kind: string) {
    setQuick(kind);
    const r = quickRange(kind, today);
    if (!r) return;
    setFrom(r[0]);
    setTo(r[1]);
    apply(r[0], r[1]);
  }

  return (
    <>
      <div className="card no-print">
        <div className="toolbar" style={{ margin: 0 }}>
          <div className="field">
            <label htmlFor="rep-key">Report</label>
            <select id="rep-key" value={chosen ?? ""} onChange={(e) => setKey(e.target.value)}>
              {reports.map((r) => (
                <option key={r.key} value={r.key}>
                  {r.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="rep-from">From</label>
            <DateField id="rep-from" value={from} onChange={setFrom} today={today} />
          </div>
          <div className="field">
            <label htmlFor="rep-to">To</label>
            <DateField id="rep-to" value={to} onChange={setTo} today={today} />
          </div>
          <div className="field">
            <label htmlFor="rep-quick">Quick</label>
            <select id="rep-quick" value={quick} onChange={(e) => chooseQuick(e.target.value)}>
              <option value="">—</option>
              <option value="today">Today</option>
              <option value="week">Last 7 days</option>
              <option value="month">This month</option>
              <option value="last">Last month</option>
              <option value="quarter">Last 3 months</option>
            </select>
          </div>
          <button className="btn primary" type="button" onClick={() => apply()}>
            Apply
          </button>
          <span style={{ flex: 1 }} />
          <button
            className="btn"
            type="button"
            disabled={!chosen}
            onClick={() =>
              chosen && window.open(exportUrl(chosen, "pdf", applied.from, applied.to), "_blank", "noopener")
            }
          >
            Export PDF
          </button>
          <a
            className={cn("btn", !chosen && "disabled")}
            href={chosen ? exportUrl(chosen, "xlsx", applied.from, applied.to) : undefined}
            download
          >
            Export Excel
          </a>
        </div>
      </div>

      {error && <div className="banner bad">{error}</div>}
      {list.isError && <div className="banner bad">{list.error.message}</div>}
      {report.isError && <div className="banner bad">{report.error.message}</div>}
      {v && (
        <>
          <p className="small muted" style={{ margin: "0 0 8px" }} data-testid="rep-range">
            {v.name} report – {formatDate(v.from)} to {formatDate(v.to)}
          </p>
          {v.kpis.length > 0 && (
            <KpiGrid>
              {v.kpis.map((k) => (
                <KpiCard key={k.label} label={k.label} value={kpiText(k)} alert={k.alert} />
              ))}
            </KpiGrid>
          )}
          {v.chart && (
            <div className="card">
              <h2>{v.chart.title}</h2>
              <BarChart
                label={`${v.name} – ${v.chart.title}`}
                points={v.chart.points.map((p, i) => ({
                  key: String(i),
                  label: p.label,
                  value: Number(p.value),
                  title: p.title,
                  shown: formatMoney(p.value),
                }))}
              />
            </div>
          )}
          {v.tables.map((t, i) => (
            <TableCard key={`${v.key}-${i}`} t={t} />
          ))}
          {v.note && <p className="small muted">{v.note}</p>}
        </>
      )}
    </>
  );
}
