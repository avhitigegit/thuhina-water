"use client";

/*
 * Daily Delivery List (M08, prototype delivery/delivery-schedule.html, FR-42 – FR-44): who to deliver to on a date
 * (‹ date › Today, area), KPIs, the customer table in two tabs – Today (due on the date) and Missed (today only: delivery
 * date passed, nothing entered; red count, hidden when none) – and the printed delivery sheet – view and print only.
 * Bills are typed on Enter Bills (or on the phone, My Deliveries); the upcoming bottle requirement is on Delivery Planning.
 */
import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DateField } from "@/components/shared/DateField";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Tabs } from "@/components/shared/Tabs";
import { useAreas } from "@/features/customers/api";
import type { DueCustomer } from "@/lib/api/types";
import { addDaysIso, formatDate, formatMoney, isValidIso, todayIso } from "@/lib/format";
import { cn } from "@/lib/utils";
import { deliverySheetUrl, useDueList } from "./api";
import { bottlesText, splitDue, sum } from "./labels";

export function DailyList({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const start = params.get("date");
  const [date, setDate] = useState(start && isValidIso(start) ? start : today);
  const [area, setArea] = useState("");

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
      <DayPane date={date} setDate={setDate} area={area} setArea={setArea} today={today} />
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
  const data = list.data;
  const items = data?.items ?? [];
  const { today: dueList, missed } = splitDue(items);
  const [part, setPart] = useState<"today" | "missed">("today");
  const open = missed.length ? part : "today";
  const rows = open === "missed" ? missed : dueList;

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
            {!data
              ? "Loading…"
              : open === "missed"
                ? `Missed earlier – ${missed.length} customers, longest missed first`
                : `${data.dayName} ${formatDate(data.date)} – ${dueList.length} customers`}
          </h2>
        </div>
        {missed.length > 0 && (
          <Tabs
            tabs={[
              { id: "today", label: "Today", count: dueList.length },
              { id: "missed", label: "Missed", count: missed.length, countKind: "bad" },
            ]}
            value={open}
            onChange={setPart}
          />
        )}
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
              </tr>
            </thead>
            <tbody>
              {rows.map((c) => (
                <DayRow key={c.id} c={c} />
              ))}
              {data && rows.length === 0 && (
                <tr>
                  <td colSpan={9} className="empty-state">
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

function DayRow({ c }: { c: DueCustomer }) {
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
              bill {c.enteredBills.map((b) => b.billNo + (b.onPhone ? " (phone)" : "")).join(", ")} · next{" "}
              {formatDate(c.nextDeliveryDate)}
            </div>
          </>
        ) : c.overdue ? (
          <Badge kind="bad">Missed {formatDate(c.nextDeliveryDate)}</Badge>
        ) : (
          <StatusBadge status="Due" />
        )}
      </td>
    </tr>
  );
}
