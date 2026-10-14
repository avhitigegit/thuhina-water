"use client";

/*
 * Filling Factory pop-ups (prototype production/production.html):
 *   Send empties – one entry for every bottle type (factory, date, vehicle, note; a row per bottle type with empty in store,
 *     sent now, empty after, at factory after, charge, cost), with the "below zero" and "no charge" warnings (BR-12);
 *   Dispatch sent – the batches made and Print dispatch note;
 *   Receive filled – batch, date, filled (default all still at the factory), rejected + reason, and the calculation;
 *   Record factory payment – factory and month from the unpaid list, amount (default = due), method, reference.
 */
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useFactories } from "@/features/partners/api";
import { ApiError } from "@/lib/api/client";
import type { BatchView, DispatchView, FactoryBottleStock, FactoryMonth } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { toast } from "@/lib/toast";
import { dispatchNoteUrl, usePayFactory, useReceiveFilled, useSendEmpties } from "./api";

/** The message to show for a failed save: field messages of a 400, else the error message. */
export function errorText(err: unknown): string {
  if (err instanceof ApiError) {
    const fields = Object.values(err.fieldErrors);
    return fields.length ? fields.join(" ") : err.message;
  }
  return "Something went wrong. Please try again.";
}

export function openDispatchNote(dispatchNo: string) {
  window.open(dispatchNoteUrl(dispatchNo), "_blank", "noopener");
}

// ------------------------------------------------------------------ send empties (FR-23)

export function SendEmptiesDialog({
  bottles,
  today,
  onClose,
  onSent,
}: {
  bottles: FactoryBottleStock[];
  today: string;
  onClose: () => void;
  onSent: (d: DispatchView) => void;
}) {
  const factories = useFactories();
  const send = useSendEmpties();
  const active = (factories.data ?? []).filter((f) => f.active);
  const [factory, setFactory] = useState("");
  const [date, setDate] = useState<string | null>(today);
  const [vehicle, setVehicle] = useState("");
  const [note, setNote] = useState("");
  const [qty, setQty] = useState<Record<string, string>>(() =>
    Object.fromEntries(bottles.map((b) => [b.bottleTypeCode, String(b.empty)])),
  );
  const [error, setError] = useState("");
  const factoryId = factory || (active[0] ? String(active[0].id) : "");
  const chosen = active.find((f) => String(f.id) === factoryId);
  const chargeOf = (code: string) => chosen?.charges.find((c) => c.bottleTypeCode === code)?.charge ?? null;

  let totalQty = 0;
  let totalCost = 0;
  const warnings: string[] = [];
  for (const b of bottles) {
    const q = Number(qty[b.bottleTypeCode]) || 0;
    const ch = chargeOf(b.bottleTypeCode);
    if (q > b.empty) warnings.push(`Only ${b.empty} empty ${b.label} in store.`);
    if (q > 0 && ch === null && chosen)
      warnings.push(
        `${chosen.name} has no filling charge for ${b.label} – set it under Suppliers & Factories.`,
      );
    totalQty += q;
    totalCost += q * Number(ch ?? 0);
  }

  function submit() {
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    send.mutate(
      {
        factoryId: factoryId ? Number(factoryId) : undefined,
        date,
        vehicleNo: vehicle,
        note,
        lines: bottles.map((b) => ({
          bottleTypeCode: b.bottleTypeCode,
          qty: Number(qty[b.bottleTypeCode]) || 0,
        })),
      },
      {
        onSuccess: (d) => {
          toast(`Dispatch ${d.dispatchNo} sent`, "ok");
          onSent(d);
        },
        onError: (err) => setError(errorText(err)),
      },
    );
  }

  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title="Send empties to the factory"
      onSubmit={submit}
      submitLabel="Send"
      submitting={send.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid">
        <div className="field req span2">
          <label htmlFor="fd-factory">Filling factory</label>
          <select id="fd-factory" value={factoryId} onChange={(e) => setFactory(e.target.value)}>
            {active.map((f) => (
              <option key={f.id} value={f.id}>
                {f.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field req">
          <label htmlFor="fd-date">Date</label>
          <DateField id="fd-date" value={date} onChange={setDate} today={today} />
        </div>
        <div className="field">
          <label htmlFor="fd-vehicle">Vehicle</label>
          <input
            id="fd-vehicle"
            placeholder="e.g. LH-4521"
            value={vehicle}
            onChange={(e) => setVehicle(e.target.value)}
          />
        </div>
        <div className="field span2">
          <label htmlFor="fd-note">Note</label>
          <input
            id="fd-note"
            placeholder="e.g. Weekly batch"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </div>
      </div>
      <div className="section-title">
        Bottles sent{" "}
        <span className="small muted" style={{ textTransform: "none", fontWeight: 400 }}>
          – fill in every bottle type going in this load; leave 0 for types not sent
        </span>
      </div>
      <table>
        <thead>
          <tr>
            <th>Bottle</th>
            <th className="num">Empty in store</th>
            <th className="num">Sent now</th>
            <th className="num">Empty after</th>
            <th className="num">At factory after</th>
            <th className="num">Charge each</th>
            <th className="num">Cost when filled</th>
          </tr>
        </thead>
        <tbody>
          {bottles.map((b) => {
            const q = Number(qty[b.bottleTypeCode]) || 0;
            const ch = chargeOf(b.bottleTypeCode);
            const left = b.empty - q;
            return (
              <tr key={b.bottleTypeCode}>
                <td>
                  <b>{b.name}</b>
                </td>
                <td className="num">{b.empty}</td>
                <td className="num">
                  <input
                    aria-label={`Sent now ${b.label}`}
                    type="number"
                    min={0}
                    style={{ width: 90 }}
                    value={qty[b.bottleTypeCode] ?? ""}
                    onChange={(e) => setQty({ ...qty, [b.bottleTypeCode]: e.target.value })}
                  />
                </td>
                <td className="num" style={left < 0 ? { color: "var(--bad)" } : undefined}>
                  {left}
                </td>
                <td className="num">{b.atFactory + q}</td>
                <td className="num">
                  {ch === null ? <span className="muted">no charge</span> : formatMoney(ch)}
                </td>
                <td className="num">{q > 0 && ch !== null ? formatMoney(q * Number(ch)) : ""}</td>
              </tr>
            );
          })}
        </tbody>
        <tfoot>
          <tr>
            <td colSpan={2}>Total</td>
            <td className="num" data-testid="send-total">
              {totalQty}
            </td>
            <td />
            <td />
            <td />
            <td className="num">{formatMoney(totalCost)}</td>
          </tr>
        </tfoot>
      </table>
      <div style={{ marginTop: 10 }}>
        {warnings.length ? (
          <div className="banner bad">
            {warnings.join(" ")} Stock cannot go below zero – record a stock adjustment if the count is
            different.
          </div>
        ) : (
          <div className="small muted">
            {totalQty} empties go out in one dispatch. Filling cost is charged only for bottles that come back
            filled.
          </div>
        )}
      </div>
    </FormDialog>
  );
}

export function DispatchSentDialog({ dispatch, onClose }: { dispatch: DispatchView; onClose: () => void }) {
  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Dispatch ${dispatch.dispatchNo} sent`}
      extraButtons={
        <button type="button" className="btn" onClick={() => openDispatchNote(dispatch.dispatchNo)}>
          Print dispatch note
        </button>
      }
    >
      <p>
        Sent to <b>{dispatch.factory.name}</b> on {formatDate(dispatch.date)}:
      </p>
      <ul>
        {dispatch.batches.map((b) => (
          <li key={b.id}>
            {b.qtySent} × {b.bottleLabel} – batch <span className="mono">{b.batchNo}</span>
          </li>
        ))}
      </ul>
      <p className="small muted">Each bottle type is received back against its own batch.</p>
    </FormDialog>
  );
}

// ------------------------------------------------------------------ receive filled (FR-24, FR-26)

export function batchLabel(b: BatchView): string {
  return `${b.batchNo} – ${b.factory.name} – sent ${formatDate(b.date)} – ${b.atFactory} × ${b.bottleLabel} at factory`;
}

export function ReceiveFilledDialog({
  open,
  batchId,
  today,
  onClose,
}: {
  open: BatchView[];
  batchId: number | null;
  today: string;
  onClose: () => void;
}) {
  const receive = useReceiveFilled();
  const [batch, setBatch] = useState(String(batchId ?? open[0]?.id ?? ""));
  const b = open.find((x) => String(x.id) === batch);
  const [date, setDate] = useState<string | null>(today);
  const [filled, setFilled] = useState(String(b?.atFactory ?? ""));
  const [rejected, setRejected] = useState("0");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const f = Number(filled) || 0;
  const r = Number(rejected) || 0;
  const left = (b?.atFactory ?? 0) - f - r;

  function submit() {
    if (!b) return;
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    receive.mutate(
      { batchId: b.id, body: { date, filled: f, rejected: r, rejectReason: reason } },
      {
        onSuccess: (res) => {
          toast(
            `Received – filling cost ${formatMoney(res.saved.cost)}` +
              (res.batch.atFactory ? `, ${res.batch.atFactory} still at the factory` : ""),
            "ok",
          );
          onClose();
        },
        onError: (err) => setError(errorText(err)),
      },
    );
  }

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Receive filled bottles"
      onSubmit={submit}
      submitting={receive.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      {open.length === 0 ? (
        <p className="muted">No bottles are at the factory.</p>
      ) : (
        <>
          <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
            <div className="field req full">
              <label htmlFor="fr-batch">Batch</label>
              <select
                id="fr-batch"
                value={batch}
                onChange={(e) => {
                  setBatch(e.target.value);
                  setFilled(String(open.find((x) => String(x.id) === e.target.value)?.atFactory ?? ""));
                }}
              >
                {open.map((x) => (
                  <option key={x.id} value={x.id}>
                    {batchLabel(x)}
                  </option>
                ))}
              </select>
            </div>
            <div className="field req">
              <label htmlFor="fr-date">Date</label>
              <DateField id="fr-date" value={date} onChange={setDate} today={today} />
            </div>
            <div className="field req">
              <label htmlFor="fr-filled">Filled bottles received</label>
              <input
                id="fr-filled"
                type="number"
                min={0}
                value={filled}
                onChange={(e) => setFilled(e.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="fr-rej">Rejected / damaged</label>
              <input
                id="fr-rej"
                type="number"
                min={0}
                value={rejected}
                onChange={(e) => setRejected(e.target.value)}
              />
            </div>
            <div className={r > 0 ? "field req" : "field"}>
              <label htmlFor="fr-reason">Reject reason</label>
              <input
                id="fr-reason"
                placeholder="e.g. Failed seal test"
                value={reason}
                onChange={(e) => setReason(e.target.value)}
              />
            </div>
          </div>
          {b && (
            <div className="summary" style={{ marginTop: 10 }}>
              <span>Still at factory after this</span>
              <b className="num" data-testid="left-after" style={left ? { color: "var(--warn)" } : undefined}>
                {left}
              </b>
              <span>Rejected → written off (company)</span>
              <b className="num">{r}</b>
              <span className="total">
                Filling cost ({f} × {formatMoney(b.charge)})
              </span>
              <b className="num total" data-testid="return-cost">
                {formatMoney(f * Number(b.charge))}
              </b>
            </div>
          )}
        </>
      )}
    </FormDialog>
  );
}

// ------------------------------------------------------------------ factory payment (FR-26)

const METHODS = [
  { value: "CASH", label: "Cash" },
  { value: "BANK_TRANSFER", label: "Bank transfer" },
  { value: "CHEQUE", label: "Cheque" },
] as const;

export function FactoryPaymentDialog({
  unpaid,
  today,
  onClose,
}: {
  unpaid: FactoryMonth[];
  today: string;
  onClose: () => void;
}) {
  const pay = usePayFactory();
  const [pick, setPick] = useState("0");
  const sel = unpaid[Number(pick)];
  const [date, setDate] = useState<string | null>(today);
  const [amount, setAmount] = useState(sel ? String(sel.due) : "");
  const [method, setMethod] = useState<(typeof METHODS)[number]["value"]>("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");

  function submit() {
    setError("");
    if (!sel) {
      setError("Nothing is owed to the factories.");
      return;
    }
    if (!date) {
      setError("Enter the date.");
      return;
    }
    pay.mutate(
      {
        factoryId: sel.factoryId,
        period: sel.period,
        date,
        amount: amount.trim() === "" ? undefined : Number(amount),
        method,
        reference,
        note,
      },
      {
        onSuccess: (p) => {
          toast(`Payment recorded (${p.fpNo})`, "ok");
          onClose();
        },
        onError: (err) => setError(errorText(err)),
      },
    );
  }

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Record factory payment"
      onSubmit={submit}
      submitLabel="Save"
      submitting={pay.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req full">
          <label htmlFor="fp-month">Factory and month</label>
          <select
            id="fp-month"
            value={pick}
            onChange={(e) => {
              setPick(e.target.value);
              const m = unpaid[Number(e.target.value)];
              setAmount(m ? String(m.due) : "");
            }}
          >
            {unpaid.map((m, i) => (
              <option key={`${m.factoryId}-${m.period}`} value={i}>
                {m.factoryName} – {m.periodLabel} – {formatMoney(m.due)} due
              </option>
            ))}
          </select>
        </div>
        <div className="field req">
          <label htmlFor="fp-date">Date</label>
          <DateField id="fp-date" value={date} onChange={setDate} today={today} />
        </div>
        <div className="field req">
          <label htmlFor="fp-amount">Amount (Rs.)</label>
          <input
            id="fp-amount"
            type="number"
            min={0}
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </div>
        <div className="field req">
          <label htmlFor="fp-method">Method</label>
          <select
            id="fp-method"
            value={method}
            onChange={(e) => setMethod(e.target.value as (typeof METHODS)[number]["value"])}
          >
            {METHODS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
        </div>
        <div className={method === "CASH" ? "field" : "field req"}>
          <label htmlFor="fp-ref">Cheque no. / ref.</label>
          <input id="fp-ref" value={reference} onChange={(e) => setReference(e.target.value)} />
        </div>
        <div className="field full">
          <label htmlFor="fp-note">Note</label>
          <input id="fp-note" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </div>
    </FormDialog>
  );
}
