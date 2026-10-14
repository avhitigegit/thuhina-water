"use client";

/*
 * Demo data (M12 S4, local / UAT only): makes realistic history for days without delivery bills – deliveries (some left at
 * the door or short of empties), extra orders, product sales, weekly factory batches, payments and monthly invoices – through
 * the real services. The tab is shown only when the server offers the generator (never in production).
 */
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import type { DemoDataResult, DemoDataStatus } from "@/lib/api/types";
import { formatDate, todayIso } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { toast } from "@/lib/toast";
import { useGenerateDemoData } from "@/features/reports/api";

export function DemoDataTab({ status, today = todayIso() }: { status: DemoDataStatus; today?: string }) {
  const generate = useGenerateDemoData();
  const [from, setFrom] = useState<string | null>(status.suggestedFrom);
  const [to, setTo] = useState<string | null>(status.suggestedTo);
  const [error, setError] = useState("");
  const [result, setResult] = useState<DemoDataResult | null>(null);

  function run() {
    if (!from || !to) {
      setError("Enter dates as DD/MM/YYYY.");
      return;
    }
    setError("");
    setResult(null);
    generate.mutate(
      { from, to },
      {
        onSuccess: (r) => {
          setResult(r);
          toast(r.message, "ok");
        },
        onError: (e) => setError(errorText(e)),
      },
    );
  }

  return (
    <div className="card">
      <h2>Demo data</h2>
      <div className="banner warn">
        For the local and UAT systems only. It adds history to the database: delivery bills for the due
        customers, extra orders, product sales, the weekly factory batch, payments and monthly invoices – for
        days that have no delivery bills yet.
      </div>
      <p className="small muted">
        {status.lastDeliveryBill
          ? `The last delivery bill is dated ${formatDate(status.lastDeliveryBill)}.`
          : "There are no delivery bills yet."}{" "}
        It can take a minute for two months.
      </p>
      {error && <div className="banner bad">{error}</div>}
      <div className="toolbar">
        <div className="field">
          <label htmlFor="demo-from">From</label>
          <DateField id="demo-from" value={from} onChange={setFrom} today={today} />
        </div>
        <div className="field">
          <label htmlFor="demo-to">To</label>
          <DateField id="demo-to" value={to} onChange={setTo} today={today} />
        </div>
        <button className="btn primary" type="button" disabled={generate.isPending} onClick={run}>
          {generate.isPending ? "Making demo data…" : "Make demo data"}
        </button>
      </div>
      {result && (
        <div data-testid="demo-result">
          <div className="summary" style={{ maxWidth: 420 }}>
            <span>Delivery bills</span>
            <b className="num">{result.bills}</b>
            <span>– left at the door</span>
            <b className="num">{result.leftAtDoor}</b>
            <span>– short of empties</span>
            <b className="num">{result.shortages}</b>
            <span>Extra orders</span>
            <b className="num">{result.extraOrders}</b>
            <span>Product sales</span>
            <b className="num">{result.productSales}</b>
            <span>Factory dispatches / returns</span>
            <b className="num">
              {result.dispatches} / {result.returns}
            </b>
            <span>Payments</span>
            <b className="num">{result.payments}</b>
            <span>Monthly invoices</span>
            <b className="num">{result.invoices}</b>
            <span>Skipped</span>
            <b className="num">{result.skipped}</b>
          </div>
          {result.problems.length > 0 && (
            <>
              <div className="section-title">Skipped steps</div>
              <ul className="small">
                {result.problems.map((p, i) => (
                  <li key={i}>{p}</li>
                ))}
              </ul>
            </>
          )}
        </div>
      )}
    </div>
  );
}
