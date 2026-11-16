"use client";

/*
 * Upcoming bottle requirement (FR-45): the filled bottles needed for the next 14 days of one bottle type against the
 * stock, with the day the filled bottles run out. Shown on Delivery Planning (it used to be a tab of the Daily
 * Delivery List as well – one place now).
 */
import { useState } from "react";
import { useBottleTypes } from "@/features/masterdata/api";
import { formatDate, formatNumber } from "@/lib/format";
import { useRequirement } from "./api";
import { chartLabel } from "./labels";

/** Upcoming bottle requirement (FR-45, Admin): next 14 days of one bottle type against the stock. */
export function UpcomingNeed() {
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
