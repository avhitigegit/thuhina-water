"use client";

/*
 * Daily Delivery List (M08, prototype delivery/delivery-schedule.html, FR-42 – FR-45): who to deliver to on a date
 * (‹ date › Today, area), KPIs, the customer table and the printed delivery sheet. The Admin also sees "Enter bill"
 * per row (not for future dates) and the "Upcoming bottle requirement" tab. Delivery Staff: view + print only.
 */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DateField } from "@/components/shared/DateField";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { useCan } from "@/features/auth/access";
import { useAreas } from "@/features/customers/api";
import { useBottleTypes } from "@/features/masterdata/api";
import type { DueCustomer } from "@/lib/api/types";
import { addDaysIso, formatDate, formatMoney, formatNumber, isValidIso, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { cn } from "@/lib/utils";
import { deliverySheetUrl, useDueList, useRequirement } from "./api";
import { bottlesText, chartLabel, sum } from "./labels";

const TABS = ["day", "need"] as const;

export function DailyList({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const start = params.get("date");
  const [date, setDate] = useState(start && isValidIso(start) ? start : today);
  const [area, setArea] = useState("");
  const [tab, setTab] = useHashTab(TABS);
  const canPlan = useCan(P.DELIVERY_PLANNING_VIEW);
  const shown = canPlan ? tab : "day";

  return (
    <>
      <PageActions>
        <button
          className="btn primary"
          type="button"
          onClick={() => window.open(deliverySheetUrl(date, area), "_blank", "noopener")}
        >
          Print delivery sheet
        </button>
      </PageActions>
      {canPlan && (
        <Tabs
          tabs={[
            { id: "day", label: "Daily list" },
            { id: "need", label: "Upcoming bottle requirement" },
          ]}
          value={shown}
          onChange={setTab}
        />
      )}
      {shown === "day" ? (
        <DayPane date={date} setDate={setDate} area={area} setArea={setArea} today={today} />
      ) : (
        <NeedPane />
      )}
    </>
  );
}

function DayPane({
  date,
  setDate,
  area,
  setArea,
  today,
}: {
  date: string;
  setDate: (d: string) => void;
  area: string;
  setArea: (a: string) => void;
  today: string;
}) {
  const areas = useAreas();
  const list = useDueList(date, area);
  const canEnter = useCan(P.SALES_EDIT) && date <= today;
  const data = list.data;
  const items = data?.items ?? [];

  return (
    <>
      <div className="card">
        <div className="toolbar" style={{ margin: 0 }}>
          <button
            className="btn"
            type="button"
            aria-label="Previous day"
            onClick={() => setDate(addDaysIso(date, -1))}
          >
            ‹
          </button>
          <div className="field">
            <label htmlFor="ddate">Date</label>
            <DateField id="ddate" value={date} onChange={(d) => d && setDate(d)} today={today} />
          </div>
          <button
            className="btn"
            type="button"
            aria-label="Next day"
            onClick={() => setDate(addDaysIso(date, 1))}
          >
            ›
          </button>
          <button className="btn" type="button" onClick={() => setDate(today)}>
            Today
          </button>
          <div className="field">
            <label htmlFor="darea">Area</label>
            <select id="darea" value={area} onChange={(e) => setArea(e.target.value)}>
              <option value="">All areas</option>
              {(areas.data ?? []).map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {list.isError && <div className="banner bad">{list.error.message}</div>}
      {data && (
        <KpiGrid>
          <KpiCard
            label="Customers"
            value={items.length}
            hint={`${data.counts.entered} done · ${data.counts.overdue} overdue`}
          />
          {data.totals.load.map((b) => (
            <KpiCard key={b.bottleTypeCode} label={`Load ${b.label} filled`} value={b.qty} />
          ))}
          <KpiCard label="Empties to collect" value={data.totals.toCollect} />
          <KpiCard label="Money to collect" value={formatMoney(data.totals.moneyToCollect)} />
        </KpiGrid>
      )}

      <div className="card">
        <div className="card-head">
          <h2>
            {data ? `${data.dayName} ${formatDate(data.date)} – ${items.length} customers` : "Loading…"}
          </h2>
          {canEnter && (
            <Link className="btn sm" href="/sales/enter-bills">
              Enter bills
            </Link>
          )}
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Customer</th>
                <th>Phone</th>
                <th>Pays</th>
                <th className="num">Bottles held</th>
                <th className="num">Usual</th>
                <th className="num">Empties to collect</th>
                <th className="num">Owed</th>
                <th className="num">Balance</th>
                <th>Status</th>
                {canEnter && <th />}
              </tr>
            </thead>
            <tbody>
              {items.map((c) => (
                <DayRow key={c.id} c={c} canEnter={canEnter} />
              ))}
              {data && items.length === 0 && (
                <tr>
                  <td colSpan={canEnter ? 10 : 9} className="empty-state">
                    No customers due on this day.
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

function DayRow({ c, canEnter }: { c: DueCustomer; canEnter: boolean }) {
  const held = bottlesText(c.bottles, "held");
  const collect = sum(c.bottles, (b) => b.toCollect);
  const owed = sum(c.bottles, (b) => b.owed);
  const balance = Number(c.balance);
  return (
    <tr className={cn(c.entered ? "row-ok" : c.overdue && "row-bad")}>
      <td>
        <b>{c.name}</b>
        <div className="small muted">
          {c.address}, {c.areaName}
        </div>
        {c.notes && <div className="small">{c.notes}</div>}
      </td>
      <td className="nowrap">{c.phone}</td>
      <td>
        <StatusBadge status={c.payTypeLabel} />
      </td>
      <td className="num">{held || <Badge kind="info">New – deposit</Badge>}</td>
      <td className="num">
        <b>{bottlesText(c.bottles, "usual")}</b>
      </td>
      <td className="num">{collect > 0 && <Badge kind="warn">{collect}</Badge>}</td>
      <td className="num">{owed > 0 && <Badge kind="bad">{owed}</Badge>}</td>
      <td className="num">{balance > 0 && formatMoney(balance)}</td>
      <td>
        {c.entered ? (
          <>
            <StatusBadge status="Entered" />
            <div className="small muted">
              bill {c.enteredBills.map((b) => b.billNo).join(", ")} · next {formatDate(c.nextDeliveryDate)}
            </div>
          </>
        ) : c.overdue ? (
          <Badge kind="bad">Missed {formatDate(c.nextDeliveryDate)}</Badge>
        ) : (
          <StatusBadge status="Due" />
        )}
      </td>
      {canEnter && (
        <td className="actions">
          {!c.entered && (
            <Link className="btn sm" href={`/sales/enter-bills?cust=${c.code}`}>
              Enter bill
            </Link>
          )}
        </td>
      )}
    </tr>
  );
}

/** Upcoming bottle requirement (FR-45, Admin): next 14 days of one bottle type against the stock. */
function NeedPane() {
  const bottles = useBottleTypes();
  const active = (bottles.data ?? []).filter((b) => b.active);
  const [bottle, setBottle] = useState("");
  const req = useRequirement(bottle, true);
  const r = req.data;
  const max = Math.max(1, ...(r?.rows ?? []).map((d) => d.needed));
  // First day the filled bottles in store are not enough (r.runsOutOn also counts the bottles at the factory).
  const storeOut = r?.rows.find((d) => d.filledLeft < 0);

  return (
    <div className="card">
      <div className="card-head">
        <h2>Filled bottles needed – next 14 days</h2>
        {active.length > 1 && (
          <div className="field" style={{ margin: 0 }}>
            <select
              aria-label="Bottle type"
              value={bottle || r?.bottleTypeCode || ""}
              onChange={(e) => setBottle(e.target.value)}
            >
              {active.map((b) => (
                <option key={b.code} value={b.code}>
                  {b.name}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>
      {req.isError && <div className="banner bad">{req.error.message}</div>}
      {r && (
        <>
          <p>
            {r.label} stock now: <b>{r.filledInStore}</b> filled in store, <b>{r.atFactory}</b> at the
            factory, <b>{r.emptyInStore}</b> empties in store.
          </p>
          {storeOut ? (
            <div className="banner bad">
              Filled bottles in store run out on{" "}
              <b>
                {storeOut.dayName} {formatDate(storeOut.date)}
              </b>
              {r.runsOutOn ? (
                <>
                  , and even with the {r.atFactory} at the factory on{" "}
                  <b>
                    {r.rows.find((d) => d.date === r.runsOutOn)?.dayName} {formatDate(r.runsOutOn)}
                  </b>
                  . Send more empties to the factory before then.
                </>
              ) : (
                <>. Bring back the filled bottles from the factory before then.</>
              )}
            </div>
          ) : (
            <div className="banner ok">Enough filled bottles for the next 14 days of usual deliveries.</div>
          )}
          <div style={{ margin: "10px 0" }} aria-hidden>
            <div className="bars">
              {r.rows.map((d) => (
                <div
                  key={d.date}
                  className="bar"
                  style={{ height: `${Math.max(1, (d.needed / max) * 100)}%` }}
                  title={`${formatDate(d.date)}: ${d.needed} bottles`}
                >
                  <span>{d.needed}</span>
                </div>
              ))}
            </div>
            <div className="bar-labels">
              {r.rows.map((d) => (
                <span key={d.date}>{chartLabel(d.dayName, d.date)}</span>
              ))}
            </div>
          </div>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th>
                  <th className="num">Customers</th>
                  <th className="num">{r.label} needed</th>
                  <th className="num">Running total</th>
                  <th className="num">Filled left</th>
                  <th className="num">…plus at factory</th>
                </tr>
              </thead>
              <tbody>
                {r.rows.map((d) => (
                  <tr key={d.date}>
                    <td>
                      {d.dayName} {formatDate(d.date)}
                    </td>
                    <td className="num">{d.customers}</td>
                    <td className="num">
                      <b>{d.needed}</b>
                    </td>
                    <td className="num">{formatNumber(d.runningTotal)}</td>
                    <td className="num">
                      {d.filledLeft < 0 ? (
                        <b style={{ color: "var(--bad)" }}>{d.filledLeft}</b>
                      ) : (
                        d.filledLeft
                      )}
                    </td>
                    <td className="num">
                      {d.withFactory < 0 ? (
                        <b style={{ color: "var(--bad)" }}>{d.withFactory}</b>
                      ) : (
                        d.withFactory
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
