"use client";

/*
 * Filling Factory pop-ups (prototype production/production.html):
 *   Send empties – one entry for every bottle type (factory, date, vehicle, note; a row per bottle type with empty in store,
 *     sent now, empty after, at factory after, charge, cost), with the "below zero" and "no charge" warnings (BR-12);
 *   Dispatch sent – the batches made and Print dispatch note;
 *   Receive filled – factory, bottle, date, filled (default all still at the factory), rejected + reason; the load is
 *     split over the open batches oldest first (bottles kept back come with a later load), shown before saving;
 *   Pay factory – factory, amount (default = owed), Paid by + reference; it clears the oldest filling cost first.
 */
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useFactories } from "@/features/partners/api";
import { ApiError } from "@/lib/api/client";
import type { BatchView, DispatchView, FactoryBottleStock } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { toast } from "@/lib/toast";
import { allocate, dispatchNoteUrl, useReceiveFilled, useSendEmpties } from "./api";

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
  // Typed quantities; until typed, all empties in store – or 0 for a size the factory does not fill (N-05).
  const [typed, setQty] = useState<Record<string, string>>({});
  const [error, setError] = useState("");
  const factoryId = factory || (active[0] ? String(active[0].id) : "");
  const chosen = active.find((f) => String(f.id) === factoryId);
  const chargeOf = (code: string) => chosen?.charges.find((c) => c.bottleTypeCode === code)?.charge ?? null;
  const qty: Record<string, string> = Object.fromEntries(
    bottles.map((b) => [
      b.bottleTypeCode,
      typed[b.bottleTypeCode] ?? (chosen && chargeOf(b.bottleTypeCode) === null ? "0" : String(b.empty)),
    ]),
  );

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
                    onChange={(e) => setQty({ ...typed, [b.bottleTypeCode]: e.target.value })}
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

/**
 * Receive one load back from a factory for one bottle type. The load is split over that factory's open batches, oldest
 * first, so bottles the factory kept back last time (not damaged – just not ready) are received with the next load.
 */
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
  const start = open.find((x) => x.id === batchId) ?? open[0];
  const fromRow = batchId != null && open.some((x) => x.id === batchId);
  const [factoryId, setFactoryId] = useState(String(start?.factory.id ?? ""));
  const [bottle, setBottle] = useState(start?.bottleTypeCode ?? "");
  const [date, setDate] = useState<string | null>(today);
  const factories = [...new Map(open.map((x) => [String(x.factory.id), x.factory.name])).entries()];
  const bottlesAt = (fid: string) => [
    ...new Map(
      open.filter((x) => String(x.factory.id) === fid).map((x) => [x.bottleTypeCode, x.bottleLabel]),
    ).entries(),
  ];
  // Open batches of this factory and bottle type sent on or before the date, oldest first.
  const batches = open
    .filter(
      (x) => String(x.factory.id) === factoryId && x.bottleTypeCode === bottle && (!date || x.date <= date),
    )
    .sort((p, q) => p.date.localeCompare(q.date) || p.id - q.id);
  const total = batches.reduce((n, x) => n + x.atFactory, 0);
  const [filled, setFilled] = useState(String(total || ""));
  const [rejected, setRejected] = useState("0");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const f = Number(filled) || 0;
  const r = Number(rejected) || 0;
  const left = total - f - r;
  const split = allocate(
    batches.map((x) => x.atFactory),
    f,
    r,
  );
  const cost = batches.reduce((sum, x, i) => sum + split[i][0] * Number(x.charge), 0);
  const label = batches[0]?.bottleLabel ?? "";

  function pick(fid: string, code: string) {
    setFactoryId(fid);
    setBottle(code);
    const n = open
      .filter((x) => String(x.factory.id) === fid && x.bottleTypeCode === code && (!date || x.date <= date))
      .reduce((sum, x) => sum + x.atFactory, 0);
    setFilled(String(n || ""));
  }

  function submit() {
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    receive.mutate(
      {
        factoryId: Number(factoryId),
        bottleTypeCode: bottle,
        date,
        filled: f,
        rejected: r,
        rejectReason: reason,
      },
      {
        onSuccess: (res) => {
          toast(
            `Received – filling cost ${formatMoney(res.cost)}` +
              (res.stillAtFactory ? `, ${res.stillAtFactory} still at the factory` : ""),
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
      wide
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
          <p className="small muted" style={{ marginTop: 0 }}>
            Enter what the factory brought back. It is matched to the oldest batches first. Bottles not
            brought back stay at the factory and can be received with the next load.
          </p>
          <div className="form-grid">
            <div className="field req">
              <label htmlFor="fr-factory">Factory</label>
              {fromRow ? (
                <input
                  id="fr-factory"
                  readOnly
                  value={factories.find(([id]) => id === factoryId)?.[1] ?? ""}
                />
              ) : (
                <select
                  id="fr-factory"
                  value={factoryId}
                  onChange={(e) => pick(e.target.value, bottlesAt(e.target.value)[0]?.[0] ?? "")}
                >
                  {factories.map(([id, name]) => (
                    <option key={id} value={id}>
                      {name}
                    </option>
                  ))}
                </select>
              )}
            </div>
            <div className="field req">
              <label htmlFor="fr-bottle">Bottle</label>
              {/* UAT finding 13: Receive on a batch row fixes its factory and bottle; the main button lets you choose. */}
              {fromRow ? (
                <input
                  id="fr-bottle"
                  readOnly
                  value={bottlesAt(factoryId).find(([code]) => code === bottle)?.[1] ?? ""}
                />
              ) : (
                <select id="fr-bottle" value={bottle} onChange={(e) => pick(factoryId, e.target.value)}>
                  {bottlesAt(factoryId).map(([code, l]) => (
                    <option key={code} value={code}>
                      {l}
                    </option>
                  ))}
                </select>
              )}
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
          {batches.length === 0 ? (
            <p className="muted">No bottles sent on or before this date are at this factory.</p>
          ) : (
            <table style={{ marginTop: 10 }} data-testid="receive-split">
              <thead>
                <tr>
                  <th>Batch</th>
                  <th>Sent</th>
                  <th className="num">At factory</th>
                  <th className="num">Filled now</th>
                  <th className="num">Rejected now</th>
                  <th className="num">Still at factory after</th>
                </tr>
              </thead>
              <tbody>
                {batches.map((x, i) => (
                  <tr key={x.id}>
                    <td className="mono">{x.batchNo}</td>
                    <td>{formatDate(x.date)}</td>
                    <td className="num">{x.atFactory}</td>
                    <td className="num">{split[i][0] || ""}</td>
                    <td className="num">{split[i][1] || ""}</td>
                    <td className="num">{x.atFactory - split[i][0] - split[i][1]}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          {batches.length > 0 && (
            <div className="summary" style={{ marginTop: 10 }}>
              <span>Still at factory after this {label && `(${label})`}</span>
              <b className="num" data-testid="left-after" style={left ? { color: "var(--warn)" } : undefined}>
                {left}
              </b>
              {left > 0 && (
                <span className="small muted" style={{ gridColumn: "1 / -1" }}>
                  Not damaged – they stay at the factory and can be received with the next load.
                </span>
              )}
              <span>Rejected → written off (company)</span>
              <b className="num">{r}</b>
              <span className="total">Filling cost ({f} filled × the batch charge)</span>
              <b className="num total" data-testid="return-cost">
                {formatMoney(cost)}
              </b>
            </div>
          )}
        </>
      )}
    </FormDialog>
  );
}
