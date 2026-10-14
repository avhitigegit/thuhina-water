"use client";

/*
 * Delivery Planning (M08, prototype delivery/delivery-planning.html, FR-58, FR-45): the Admin's evening page. Pick a
 * date (tomorrow by default), a route (area) and due / all customers: KPIs, a ready check per bottle type (needed vs
 * filled in store, minus what is needed before then) and the customers grouped by route with last / this date / next /
 * week after. "Print load sheet" opens the PDF for the drivers.
 */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Fragment, useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DateField } from "@/components/shared/DateField";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { useAreas } from "@/features/customers/api";
import type { PlanRow, ReadyCheck } from "@/lib/api/types";
import { addDaysIso, formatDate, formatMoney, isValidIso, todayIso } from "@/lib/format";
import { loadSheetUrl, usePlanning } from "./api";
import { cycleText, qtyText } from "./labels";

type Show = "due" | "all";

export function Planning({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const start = params.get("date");
  const [date, setDate] = useState(start && isValidIso(start) ? start : addDaysIso(today, 1));
  const [area, setArea] = useState("");
  const [show, setShow] = useState<Show>("due");
  const areas = useAreas();
  const plan = usePlanning(date, area, show);
  const p = plan.data;
  const k = p?.kpis;

  return (
    <>
      <PageActions>
        <button
          className="btn primary"
          type="button"
          onClick={() => window.open(loadSheetUrl(date, area), "_blank", "noopener")}
        >
          Print load sheet
        </button>
      </PageActions>
      <div className="card">
        <div className="toolbar" style={{ margin: 0 }}>
          <div className="field">
            <label htmlFor="pdate">Date</label>
            <DateField id="pdate" value={date} onChange={(d) => d && setDate(d)} today={today} />
          </div>
          <button className="btn" type="button" onClick={() => setDate(today)}>
            Today
          </button>
          <button className="btn" type="button" onClick={() => setDate(addDaysIso(today, 1))}>
            Tomorrow
          </button>
          <button className="btn" type="button" onClick={() => setDate(addDaysIso(today, 7))}>
            Next week
          </button>
          <div className="field">
            <label htmlFor="parea">Route (area)</label>
            <select id="parea" value={area} onChange={(e) => setArea(e.target.value)}>
              <option value="">All routes</option>
              {(areas.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="pshow">Show</label>
            <select id="pshow" value={show} onChange={(e) => setShow(e.target.value as Show)}>
              <option value="due">Customers due on this date</option>
              <option value="all">All customers</option>
            </select>
          </div>
        </div>
      </div>

      {plan.isError && <div className="banner bad">{plan.error.message}</div>}
      {p && k && (
        <>
          <KpiGrid>
            <KpiCard
              label="Customers due"
              value={k.due}
              hint={`${k.entered} already entered · ${k.missed} missed earlier`}
            />
            {k.needed.map((b) => (
              <KpiCard
                key={b.bottleTypeCode}
                label={`${b.label} bottles needed`}
                value={b.qty}
                hint="usual quantities"
              />
            ))}
            <KpiCard label="Empties to collect" value={k.toCollect} hint="left at the door" />
            <KpiCard label="Bottles owed" value={k.owed} />
            <KpiCard label="Money to collect" value={formatMoney(k.moneyToCollect)} />
          </KpiGrid>

          <div className="ready-row">
            {p.ready.length === 0 ? (
              <div className="banner info">No bottles needed on this date.</div>
            ) : (
              p.ready.map((r) => <ReadyBanner key={r.bottleTypeCode} r={r} date={p.date} />)
            )}
          </div>
        </>
      )}

      <div className="card">
        <div className="card-head">
          <h2>
            {p
              ? `${p.dayName} ${formatDate(p.date)} – ` +
                (p.show === "due"
                  ? `${p.customers} customers due`
                  : `${p.customers} customers (${p.kpis.due} due on this date)`)
              : "Loading…"}
          </h2>
          <span className="small muted">Grouped by route (area)</span>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Customer</th>
                <th>Phone</th>
                <th>Pays</th>
                <th>Last delivery</th>
                <th className="num">This date</th>
                <th>Next</th>
                <th>Week after</th>
                <th className="num">Collect</th>
                <th className="num">Owed</th>
                <th className="num">Balance</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {p?.routes.map((route) => (
                <Fragment key={route.areaId}>
                  <tr className="route-head">
                    <td colSpan={11}>
                      {route.areaName} – {route.due} due · load {qtyText(route.load) || "nothing"}
                    </td>
                  </tr>
                  {route.rows.map((r) => (
                    <PlanLine key={r.id} r={r} />
                  ))}
                </Fragment>
              ))}
              {p && p.routes.length === 0 && (
                <tr>
                  <td colSpan={11} className="empty-state">
                    No customers due on this date.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </>
  );
}

function ReadyBanner({ r, date }: { r: ReadyCheck; date: string }) {
  const text = (
    <>
      <b>{r.name}:</b> {r.needed} needed on {formatDate(date)} · {r.filledInStore} filled in store now
      {r.neededBefore > 0 && ` (${r.neededBefore} needed before then)`} · {r.atFactory} at the factory.
    </>
  );
  if (r.ready)
    return (
      <div className="banner ok">
        ✔ {text} <b>Ready.</b>
      </div>
    );
  return (
    <div className="banner bad">
      ✖ {text} <b>Short by {r.shortBy}.</b>{" "}
      {r.factoryCovers && "Bottles at the factory will cover it if they come back in time. "}
      <Link href="/production?send=1">Send empties to the factory</Link>
    </div>
  );
}

function PlanLine({ r }: { r: PlanRow }) {
  const balance = Number(r.balance);
  return (
    <tr className={r.due ? undefined : "row-muted"}>
      <td>
        <Link href={`/master-data/customers?id=${r.code}`}>
          <b>{r.name}</b>
        </Link>
        <div className="small muted">
          {r.address} · {r.dayName.slice(0, 3)}, {cycleText(r.cycleWeeks)}
        </div>
      </td>
      <td className="nowrap">{r.phone}</td>
      <td>
        <StatusBadge status={r.payTypeLabel} />
      </td>
      <td>
        {r.last ? (
          <>
            {formatDate(r.last.date)}
            <div className="small muted">
              {qtyText(r.last.filled)}
              {r.last.leftAtDoor && " (left at door)"}
            </div>
          </>
        ) : (
          <span className="muted">–</span>
        )}
      </td>
      <td className="num">{r.due ? <b>{qtyText(r.thisDate)}</b> : "–"}</td>
      <td>{formatDate(r.next)}</td>
      <td>{formatDate(r.weekAfter)}</td>
      <td className="num">{r.toCollect > 0 && <Badge kind="warn">{r.toCollect}</Badge>}</td>
      <td className="num">{r.owed > 0 && <Badge kind="bad">{r.owed}</Badge>}</td>
      <td className="num">{balance > 0 && formatMoney(balance)}</td>
      <td>
        <Status r={r} />
        {r.overLimit && (
          <>
            {" "}
            <Badge kind="warn">Over limit</Badge>
          </>
        )}
      </td>
    </tr>
  );
}

function Status({ r }: { r: PlanRow }) {
  switch (r.status) {
    case "ENTERED":
      return <StatusBadge status="Entered" />;
    case "MISSED":
      return <Badge kind="bad">{r.statusLabel}</Badge>;
    case "DUE":
      return <StatusBadge status="Due" />;
    case "FIRST_DELIVERY":
      return <Badge kind="info">First delivery</Badge>;
    default:
      return <span className="muted">Not due</span>;
  }
}
