"use client";

/*
 * My Deliveries (M17) – the delivery person's phone screen, for today only. "To visit": today's customers (and missed ones)
 * with a search that also finds any other customer; tap one to enter the filled bottles left, the empties collected and
 * the money received (Cash, Bank transfer or Cheque with its number). With no bottles the money is saved as a payment.
 * "My day": what was saved today – bottles and money by method for the hand-over. A sale over the credit limit is
 * refused here; only the office can approve it.
 */
import { useState } from "react";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { Badge } from "@/components/shared/StatusBadge";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { useAreas } from "@/features/customers/api";
import { ApiError } from "@/lib/api/client";
import type { BillRequest, DueBottle, DueCustomer, MyDay, PayMethod, SalePreview } from "@/lib/api/types";
import { errorText, toNumber } from "@/lib/forms";
import { formatDate, formatDateTime, formatMoney, formatNumber, todayIso } from "@/lib/format";
import { toast } from "@/lib/toast";
import { cn } from "@/lib/utils";
import { useDueList } from "./api";
import {
  useFindCustomers,
  useMobileBillPreview,
  useMyDay,
  useSaveMobileBill,
  useSaveMobilePayment,
} from "./mobileApi";

const TABS = ["visit", "day"] as const;

export const METHODS: { id: PayMethod; label: string; ref: string }[] = [
  { id: "CASH", label: "Cash", ref: "" },
  { id: "BANK_TRANSFER", label: "Bank transfer", ref: "Transfer reference" },
  { id: "CHEQUE", label: "Cheque", ref: "Cheque number" },
];

export function MyDeliveries({ today = todayIso() }: { today?: string }) {
  const [tab, setTab] = useHashTab(TABS);
  const [visiting, setVisiting] = useState<DueCustomer | null>(null);

  if (visiting) {
    return <VisitForm customer={visiting} onDone={() => setVisiting(null)} />;
  }
  return (
    <div className="mob">
      <Tabs
        tabs={[
          { id: "visit", label: "To visit" },
          { id: "day", label: "My day" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "visit" ? <VisitList today={today} onOpen={setVisiting} /> : <DayPane />}
    </div>
  );
}

// ------------------------------------------------------------------ list of customers

function VisitList({ today, onOpen }: { today: string; onOpen: (c: DueCustomer) => void }) {
  const [area, setArea] = useState("");
  const [q, setQ] = useState("");
  const areas = useAreas();
  const due = useDueList(today, area);
  const found = useFindCustomers(q);

  const text = q.trim().toLowerCase();
  const items = due.data?.items ?? [];
  const shown = text
    ? items.filter((c) =>
        [c.name, c.code, c.address, c.phone].some((v) => (v ?? "").toLowerCase().includes(text)),
      )
    : items;
  // Customers not on today's list (another day's route, or no delivery planned today).
  const listed = new Set(items.map((c) => c.id));
  const others = text.length >= 2 ? (found.data ?? []).filter((c) => !listed.has(c.id)) : [];
  const done = items.filter((c) => c.entered).length;

  return (
    <>
      <div className="mob-filters">
        <input
          type="search"
          placeholder="Find a customer – name, code, phone"
          aria-label="Find a customer"
          value={q}
          onChange={(e) => setQ(e.target.value)}
        />
        <select aria-label="Area" value={area} onChange={(e) => setArea(e.target.value)}>
          <option value="">All areas</option>
          {(areas.data ?? [])
            .filter((a) => a.active)
            .map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
        </select>
      </div>
      <p className="mob-count muted" data-testid="mob-count">
        {due.isPending
          ? "Loading today's customers…"
          : `${formatDate(today)}: ${items.length} customer(s) to visit · ${done} done`}
      </p>
      {due.isError && <div className="banner bad">{errorText(due.error)}</div>}
      <ul className="mob-list">
        {shown.map((c) => (
          <CustomerCard key={c.id} c={c} onOpen={onOpen} />
        ))}
      </ul>
      {text && shown.length === 0 && others.length === 0 && !found.isFetching && (
        <p className="muted">No customer found.</p>
      )}
      {others.length > 0 && (
        <>
          <h3 className="mob-sub">Other customers</h3>
          <ul className="mob-list">
            {others.map((c) => (
              <CustomerCard key={c.id} c={c} onOpen={onOpen} />
            ))}
          </ul>
        </>
      )}
    </>
  );
}

function usualText(bottles: DueBottle[]): string {
  const parts = bottles.filter((b) => b.usual > 0).map((b) => `${b.usual} × ${b.label}`);
  return parts.length ? parts.join(", ") : "—";
}

function CustomerCard({ c, onOpen }: { c: DueCustomer; onOpen: (c: DueCustomer) => void }) {
  const toCollect = c.bottles.reduce((n, b) => n + b.toCollect, 0);
  return (
    <li>
      <button type="button" className={cn("mob-card", c.entered && "done")} onClick={() => onOpen(c)}>
        <span className="mob-card-head">
          <b>{c.name}</b>
          <span className="muted">{c.code}</span>
          {c.entered && <Badge kind="ok">Done</Badge>}
          {c.overdue && <Badge kind="bad">Missed {c.daysLate} day(s)</Badge>}
        </span>
        <span className="muted">
          {c.address} · {c.areaName}
        </span>
        <span>
          Usual: <b>{usualText(c.bottles)}</b>
          {toCollect > 0 && <> · Empties waiting: {toCollect}</>}
        </span>
        {c.balance > 0 && <span className="mob-owes">Balance due {formatMoney(c.balance)}</span>}
      </button>
    </li>
  );
}

// ------------------------------------------------------------------ one visit

interface Qty {
  filled: number;
  empties: number;
}

function Stepper({
  label,
  value,
  onChange,
  disabled,
}: {
  label: string;
  value: number;
  onChange: (n: number) => void;
  disabled?: boolean;
}) {
  return (
    <div className="mob-step">
      <span>{label}</span>
      <div>
        <button
          type="button"
          className="btn"
          aria-label={`${label} – less`}
          disabled={disabled || value <= 0}
          onClick={() => onChange(value - 1)}
        >
          −
        </button>
        <input
          type="number"
          inputMode="numeric"
          min={0}
          aria-label={label}
          value={value}
          disabled={disabled}
          onChange={(e) => onChange(Math.max(0, Math.floor(Number(e.target.value) || 0)))}
        />
        <button
          type="button"
          className="btn"
          aria-label={`${label} – more`}
          disabled={disabled}
          onClick={() => onChange(value + 1)}
        >
          +
        </button>
      </div>
    </div>
  );
}

/** Bottle types to show: the customer's usual ones and any they hold or have waiting; the rest behind "Other bottle type". */
function mainBottles(c: DueCustomer): DueBottle[] {
  const main = c.bottles.filter((b) => b.usual > 0 || b.held > 0 || b.toCollect > 0 || b.owed > 0);
  return main.length ? main : c.bottles.slice(0, 1);
}

export function VisitForm({ customer: c, onDone }: { customer: DueCustomer; onDone: () => void }) {
  const [leftAtDoor, setLeftAtDoor] = useState(false);
  const [qty, setQty] = useState<Record<string, Qty>>(() =>
    Object.fromEntries(c.bottles.map((b) => [b.bottleTypeCode, { filled: b.filled, empties: b.empties }])),
  );
  const [showAll, setShowAll] = useState(false);
  const [paidText, setPaidText] = useState("");
  const [method, setMethod] = useState<PayMethod>("CASH");
  const [reference, setReference] = useState("");
  const [bank, setBank] = useState("");
  const [note, setNote] = useState("");
  const [again, setAgain] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const saveBill = useSaveMobileBill();
  const savePayment = useSaveMobilePayment();

  const main = mainBottles(c);
  const visible = showAll ? c.bottles : main;
  const paid = toNumber(paidText) ?? 0;
  const lines = c.bottles
    .map((b) => ({
      bottleTypeCode: b.bottleTypeCode,
      filled: qty[b.bottleTypeCode]?.filled ?? 0,
      empties: leftAtDoor ? 0 : (qty[b.bottleTypeCode]?.empties ?? 0),
    }))
    .filter((l) => l.filled > 0 || l.empties > 0);
  const moneyOnly = lines.length === 0;
  const methodInfo = METHODS.find((m) => m.id === method)!;

  const body: BillRequest | null = moneyOnly
    ? null
    : {
        customerId: c.id,
        date: todayIso(),
        kind: leftAtDoor ? "LEFT_AT_DOOR" : "EXCHANGE",
        lines,
        paid,
        payMethod: paid > 0 ? method : undefined,
        payReference: paid > 0 && method !== "CASH" ? reference : undefined,
        note: note || undefined,
        // Left at door on the phone: the delivery person spoke to the customer (FR-38).
        phoneConfirmed: leftAtDoor || undefined,
      };
  const preview = useMobileBillPreview(body);
  const p: SalePreview | undefined = moneyOnly ? undefined : preview.data;
  const billAmount = p?.amount ?? 0;
  const totalDue = c.balance + billAmount;

  function set(code: string, field: keyof Qty, n: number) {
    setQty((prev) => ({ ...prev, [code]: { ...(prev[code] ?? { filled: 0, empties: 0 }), [field]: n } }));
  }

  async function onSave() {
    setError(null);
    try {
      if (moneyOnly) {
        if (paid <= 0) {
          setError("Enter the bottles left or collected, or the money received.");
          return;
        }
        const r = await savePayment.mutateAsync({
          customerId: c.id,
          amount: paid,
          method,
          reference: method !== "CASH" ? reference : undefined,
          bank: method === "CHEQUE" && bank ? bank : undefined,
          note: note || undefined,
        });
        setSaved(`${r.payment.receiptNo}: ${r.message}`);
        toast(`Payment ${r.payment.receiptNo} saved`, "ok");
        return;
      }
      if (c.entered && !again) {
        setError('Tick "This is another delivery today" to save a second bill.');
        return;
      }
      const r = await saveBill.mutateAsync(body!);
      const next = r.schedule ? ` Next delivery ${formatDate(r.schedule.after)}.` : "";
      setSaved(
        `Bill ${r.txn.billNo} saved – ${formatMoney(r.txn.amount)}, received ${formatMoney(r.txn.paid)}.${next}`,
      );
      toast(`Bill ${r.txn.billNo} saved`, "ok");
    } catch (e) {
      if (e instanceof ApiError && e.code === "CREDIT_LIMIT") {
        setError(
          `${e.message.replace(" Admin approval is required.", "")} Collect money now, or ask the office to approve.`,
        );
      } else {
        setError(errorText(e));
      }
    }
  }

  if (saved) {
    return (
      <div className="mob">
        <div className="banner ok" data-testid="mob-saved">
          {saved}
        </div>
        <button type="button" className="btn primary mob-wide" onClick={onDone}>
          Back to my customers
        </button>
      </div>
    );
  }

  const problems = (p?.problems ?? []).filter((x) => x.field !== "payReference" || paid > 0);
  const saving = saveBill.isPending || savePayment.isPending;

  return (
    <div className="mob">
      <button type="button" className="btn sm" onClick={onDone}>
        ‹ Back
      </button>
      <div className="card mob-who">
        <b>{c.name}</b> <span className="muted">{c.code}</span>
        <div className="muted">{c.address}</div>
        {c.phone && (
          <a href={`tel:${c.phone.replace(/\s/g, "")}`} className="mob-tel">
            Call {c.phone}
          </a>
        )}
        <div>
          Balance before: <b>{formatMoney(c.balance)}</b> · {c.payTypeLabel}
        </div>
        {c.notes && <div className="banner info">{c.notes}</div>}
        {c.entered && (
          <div className="banner warn">
            Already entered today: {c.enteredBills.map((b) => `${b.billNo} (${b.kindLabel})`).join(", ")}.
            <label className="mob-check">
              <input type="checkbox" checked={again} onChange={(e) => setAgain(e.target.checked)} /> This is
              another delivery today
            </label>
          </div>
        )}
      </div>

      <div className="mob-toggle" role="radiogroup" aria-label="Customer at home?">
        <button
          type="button"
          role="radio"
          aria-checked={!leftAtDoor}
          className={cn("btn", !leftAtDoor && "primary")}
          onClick={() => setLeftAtDoor(false)}
        >
          Customer at home
        </button>
        <button
          type="button"
          role="radio"
          aria-checked={leftAtDoor}
          className={cn("btn", leftAtDoor && "primary")}
          onClick={() => setLeftAtDoor(true)}
        >
          Nobody home – left at door
        </button>
      </div>

      {visible.map((b) => (
        <div className="card mob-bottle" key={b.bottleTypeCode}>
          <div className="mob-bottle-head">
            <b>{b.label}</b>
            <span className="muted">
              {b.price != null ? `${formatMoney(b.price)} each` : "No price"} · holds {b.held}
              {b.toCollect > 0 && ` · ${b.toCollect} empties waiting`}
              {b.owed > 0 && ` · owes ${b.owed}`}
            </span>
          </div>
          <Stepper
            label={`Filled left – ${b.label}`}
            value={qty[b.bottleTypeCode]?.filled ?? 0}
            onChange={(n) => set(b.bottleTypeCode, "filled", n)}
          />
          <Stepper
            label={`Empties collected – ${b.label}`}
            value={leftAtDoor ? 0 : (qty[b.bottleTypeCode]?.empties ?? 0)}
            onChange={(n) => set(b.bottleTypeCode, "empties", n)}
            disabled={leftAtDoor}
          />
        </div>
      ))}
      {!showAll && c.bottles.length > main.length && (
        <button type="button" className="btn sm" onClick={() => setShowAll(true)}>
          + Other bottle type
        </button>
      )}

      <div className="card mob-money">
        {!moneyOnly && (
          <div className="mob-sum" data-testid="mob-sum">
            <span>This bill</span>
            <b>{preview.isFetching && !p ? "…" : formatMoney(billAmount)}</b>
            <span>Total due (with balance)</span>
            <b>{formatMoney(totalDue)}</b>
          </div>
        )}
        {moneyOnly && <p className="muted">No bottles – the money is saved as a payment.</p>}
        <div className="field">
          <label htmlFor="mob-paid">Money received</label>
          <input
            id="mob-paid"
            type="number"
            inputMode="decimal"
            min={0}
            placeholder="0.00"
            value={paidText}
            onChange={(e) => setPaidText(e.target.value)}
          />
          <div className="mob-quick">
            {!moneyOnly && billAmount > 0 && (
              <button type="button" className="btn sm" onClick={() => setPaidText(String(billAmount))}>
                This bill {formatMoney(billAmount)}
              </button>
            )}
            {totalDue > 0 && totalDue !== billAmount && (
              <button type="button" className="btn sm" onClick={() => setPaidText(String(totalDue))}>
                All due {formatMoney(totalDue)}
              </button>
            )}
            <button type="button" className="btn sm" onClick={() => setPaidText("")}>
              Nothing
            </button>
          </div>
        </div>
        {paid > 0 && (
          <>
            <div className="mob-toggle" role="radiogroup" aria-label="Paid by">
              {METHODS.map((m) => (
                <button
                  key={m.id}
                  type="button"
                  role="radio"
                  aria-checked={method === m.id}
                  className={cn("btn", method === m.id && "primary")}
                  onClick={() => setMethod(m.id)}
                >
                  {m.label}
                </button>
              ))}
            </div>
            {method !== "CASH" && (
              <div className="field req">
                <label htmlFor="mob-ref">{methodInfo.ref}</label>
                <input
                  id="mob-ref"
                  value={reference}
                  maxLength={100}
                  onChange={(e) => setReference(e.target.value)}
                />
              </div>
            )}
            {method === "CHEQUE" && moneyOnly && (
              <div className="field">
                <label htmlFor="mob-bank">Bank</label>
                <input id="mob-bank" value={bank} maxLength={100} onChange={(e) => setBank(e.target.value)} />
              </div>
            )}
          </>
        )}
        <div className={cn("field", leftAtDoor && "req")}>
          <label htmlFor="mob-note">Note</label>
          <input
            id="mob-note"
            value={note}
            maxLength={200}
            placeholder={leftAtDoor ? "e.g. Left at the gate, customer agreed by phone" : "Optional"}
            onChange={(e) => setNote(e.target.value)}
          />
        </div>
      </div>

      {problems.length > 0 && (
        <div className="banner warn" data-testid="mob-problems">
          {problems.map((x, i) => (
            <div key={i}>{x.message}</div>
          ))}
        </div>
      )}
      {p?.credit?.exceeds && (
        <div className="banner bad">
          Over the credit limit ({formatMoney(p.credit.limit)}) – collect money now, or ask the office to
          approve.
        </div>
      )}
      {error && <div className="banner bad">{error}</div>}
      <button type="button" className="btn primary mob-wide mob-save" disabled={saving} onClick={onSave}>
        {saving ? "Saving…" : moneyOnly ? "Save payment" : "Save delivery"}
      </button>
    </div>
  );
}

// ------------------------------------------------------------------ my day

function DayPane() {
  const day = useMyDay();
  if (day.isPending) return <p className="muted">Loading…</p>;
  if (day.isError) return <div className="banner bad">{errorText(day.error)}</div>;
  return <DaySummary day={day.data} />;
}

export function DaySummary({ day }: { day: MyDay }) {
  return (
    <>
      <KpiGrid>
        <KpiCard label="Cash to hand over" value={formatMoney(day.money.cash)} />
        <KpiCard label="Bank transfer" value={formatMoney(day.money.bankTransfer)} />
        <KpiCard label="Cheques" value={formatMoney(day.money.cheque)} />
        <KpiCard
          label="Total received"
          value={formatMoney(day.money.total)}
          hint={`${day.bills} bill(s) · ${day.payments} payment(s)`}
        />
      </KpiGrid>
      {day.bottles.length > 0 && (
        <div className="card mob-day-bottles" data-testid="mob-day-bottles">
          {day.bottles.map((b) => (
            <div key={b.bottleTypeCode}>
              <b>{b.label}</b>: {formatNumber(b.filled)} filled left · {formatNumber(b.empties)} empties
              collected
            </div>
          ))}
        </div>
      )}
      {day.entries.length === 0 ? (
        <p className="muted">Nothing saved today yet.</p>
      ) : (
        <ul className="mob-list">
          {day.entries.map((e) => (
            <li key={`${e.entry}-${e.id}`} className={cn("card mob-entry", e.cancelled && "cancelled")}>
              <div className="mob-card-head">
                <b>{e.customerName}</b>
                <span className="muted">{e.number}</span>
                {e.cancelled && <Badge kind="bad">Cancelled by office</Badge>}
              </div>
              <div className="muted">
                {formatDateTime(e.at)} · {e.kindLabel}
                {e.entry === "BILL" &&
                  ` · ${e.filled} filled / ${e.empties} empties · bill ${formatMoney(e.amount)}`}
              </div>
              {e.received > 0 && (
                <div>
                  Received <b>{formatMoney(e.received)}</b> – {e.method}
                  {e.reference && ` (${e.reference})`}
                </div>
              )}
            </li>
          ))}
        </ul>
      )}
    </>
  );
}
