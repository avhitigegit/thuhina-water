"use client";

/*
 * Purchase orders tab (FR-18 – FR-20, prototype purchasing/purchasing.html): status filter; PO, supplier, items, total,
 * received / ordered, status and the next step. PO pop-up: step bar, summary, lines (editable while Draft), history,
 * receipts and only the next action – Next: Approve → Next: Mark as sent to supplier → Next: Receive goods; Cancel PO
 * (Draft / Approved, reason), Save changes (Draft), Print / PDF. Receive goods pop-up: received now, damaged on arrival,
 * "added to stock" live. Admin full; Accountant view only.
 */
import { useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { ReasonDialog } from "@/components/shared/ReasonDialog";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { StepBar } from "@/components/shared/StepBar";
import { useCan } from "@/features/auth/access";
import type { PoDetail, PoRow } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import {
  poPdfUrl,
  useCancelPo,
  usePoStep,
  usePurchaseOrder,
  usePurchaseOrders,
  useReceiveGoods,
  useUpdatePo,
} from "./api";
import { errorText, itemText, PO_STATUSES, poSteps } from "./labels";

export function PurchaseOrdersTab({ onOpen }: { onOpen: (id: number) => void }) {
  const [status, setStatus] = useState("");
  const orders = usePurchaseOrders(status);

  const columns: Column<PoRow>[] = [
    {
      key: "po",
      header: "PO",
      cell: (p) => (
        <>
          <b className="mono">{p.poNo}</b>
          <div className="small muted">{formatDate(p.date)}</div>
        </>
      ),
    },
    { key: "supplier", header: "Supplier", cell: (p) => p.supplier.name },
    {
      key: "items",
      header: "Items",
      cell: (p) =>
        p.items.map((i) => (
          <div key={i.itemCode} className="nowrap">
            {itemText(i)}
          </div>
        )),
    },
    { key: "total", header: "Total", align: "num", cell: (p) => formatMoney(p.total) },
    { key: "received", header: "Received", align: "num", cell: (p) => `${p.received} / ${p.ordered}` },
    { key: "status", header: "Status", cell: (p) => <StatusBadge status={p.statusLabel} /> },
    { key: "next", header: "Next step", cell: (p) => <span className="small">{p.nextStep ?? "–"}</span> },
    {
      key: "open",
      header: "",
      className: "actions",
      cell: (p) => (
        <button className="btn sm primary" type="button" onClick={() => onOpen(p.id)}>
          Open
        </button>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Purchase orders</h2>
        <select
          aria-label="Status"
          style={{ width: "auto" }}
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="">All statuses</option>
          {PO_STATUSES.map((s) => (
            <option key={s.value} value={s.value}>
              {s.label}
            </option>
          ))}
        </select>
      </div>
      {orders.isError ? (
        <div className="banner bad">{orders.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={orders.data}
          loading={orders.isPending}
          rowKey={(p) => p.id}
          empty="No purchase orders."
        />
      )}
    </div>
  );
}

export function openPoPdf(id: number) {
  window.open(poPdfUrl(id), "_blank", "noopener");
}

// ------------------------------------------------------------------ PO pop-up (FR-19)

interface DraftLine {
  qty: string;
  price: string;
}

export function PoDialog({
  id,
  today = todayIso(),
  onClose,
}: {
  id: number;
  today?: string;
  onClose: () => void;
}) {
  const order = usePurchaseOrder(id);
  const po = order.data;
  const [mode, setMode] = useState<"view" | "cancel" | "receive">("view");
  if (mode === "receive" && po) {
    return <ReceiveDialog po={po} today={today} onClose={() => setMode("view")} />;
  }
  return (
    <PoView
      key={po ? `${po.id}-${po.version}` : "loading"}
      po={po}
      error={order.isError ? order.error.message : null}
      cancelling={mode === "cancel"}
      onMode={setMode}
      onClose={onClose}
    />
  );
}

function PoView({
  po,
  error: loadError,
  cancelling,
  onMode,
  onClose,
}: {
  po: PoDetail | undefined;
  error: string | null;
  cancelling: boolean;
  onMode: (m: "view" | "cancel" | "receive") => void;
  onClose: () => void;
}) {
  const canEdit = useCan(P.PURCHASING_EDIT);
  const update = useUpdatePo();
  const step = usePoStep();
  const cancel = useCancelPo();
  const draft = !!po && po.editable && canEdit;
  const [lines, setLines] = useState<Record<string, DraftLine>>(() =>
    Object.fromEntries(
      (po?.lines ?? []).map((l) => [l.itemCode, { qty: String(l.qty), price: String(l.unitPrice) }]),
    ),
  );
  const [expected, setExpected] = useState<string | null>(po?.expectedDate ?? null);
  const [notes, setNotes] = useState(po?.notes ?? "");
  const [error, setError] = useState("");
  const busy = update.isPending || step.isPending || cancel.isPending;

  const total = po
    ? po.lines.reduce((t, l) => {
        const d = lines[l.itemCode];
        return t + (draft && d ? (Number(d.qty) || 0) * (Number(d.price) || 0) : Number(l.amount));
      }, 0)
    : 0;
  const changed =
    !!po &&
    (expected !== po.expectedDate ||
      notes !== (po.notes ?? "") ||
      po.lines.some((l) => {
        const d = lines[l.itemCode];
        return d && (Number(d.qty) !== l.qty || Number(d.price) !== Number(l.unitPrice));
      }));

  function save(then?: () => void) {
    if (!po) return;
    setError("");
    if (!expected) {
      setError("Enter the expected delivery date.");
      return;
    }
    update.mutate(
      {
        id: po.id,
        body: {
          expectedDate: expected,
          notes,
          version: po.version,
          lines: po.lines.map((l) => ({
            itemCode: l.itemCode,
            qty: Number(lines[l.itemCode]?.qty) || 0,
            unitPrice: Number(lines[l.itemCode]?.price) || null,
          })),
        },
      },
      {
        onSuccess: () => (then ? then() : toast("Saved", "ok")),
        onError: (err) => setError(errorText(err)),
      },
    );
  }

  function next(which: "approve" | "send") {
    if (!po) return;
    const run = () =>
      step.mutate(
        { id: po.id, step: which },
        {
          onSuccess: () => toast(which === "approve" ? "PO approved" : "Marked as sent", "ok"),
          onError: (err) => setError(errorText(err)),
        },
      );
    if (which === "approve" && changed) save(run);
    else run();
  }

  function doCancel(reason: string) {
    if (!po) return;
    cancel.mutate(
      { id: po.id, reason },
      {
        onSuccess: () => {
          toast("PO cancelled", "ok");
          onMode("view");
        },
        onError: (err) => {
          onMode("view");
          toast(errorText(err), "bad");
        },
      },
    );
  }

  const { steps, current } = poSteps(po?.status ?? "DRAFT");
  return (
    <>
      <FormDialog
        open={!cancelling}
        wide
        onOpenChange={(o) => !o && onClose()}
        title={po ? `Purchase order ${po.poNo}` : "Purchase order"}
        cancelLabel="Close"
        extraButtons={
          po && (
            <>
              <button type="button" className="btn" onClick={() => openPoPdf(po.id)}>
                Print / PDF
              </button>
              {canEdit && po.canCancel && (
                <button type="button" className="btn danger" disabled={busy} onClick={() => onMode("cancel")}>
                  Cancel PO
                </button>
              )}
              {draft && (
                <button type="button" className="btn" disabled={busy} onClick={() => save()}>
                  Save changes
                </button>
              )}
              {canEdit && po.status === "DRAFT" && (
                <button type="button" className="btn primary" disabled={busy} onClick={() => next("approve")}>
                  Next: Approve
                </button>
              )}
              {canEdit && po.status === "APPROVED" && (
                <button type="button" className="btn primary" disabled={busy} onClick={() => next("send")}>
                  Next: Mark as sent to supplier
                </button>
              )}
              {canEdit && po.canReceive && (
                <button
                  type="button"
                  className="btn primary"
                  disabled={busy}
                  onClick={() => onMode("receive")}
                >
                  Next: Receive goods
                </button>
              )}
            </>
          )
        }
      >
        {loadError ? (
          <div className="banner bad">{loadError}</div>
        ) : !po ? (
          <p className="muted">Loading…</p>
        ) : (
          <>
            {error && <div className="banner bad">{error}</div>}
            {po.status !== "CANCELLED" && <StepBar steps={steps} current={current} />}
            <div className="summary" style={{ marginBottom: 10 }}>
              <span className="muted">Supplier</span>
              <b>{po.supplier.name}</b>
              <span className="muted">From quotation</span>
              <b>
                {po.supplierRef ?? po.quoteNo} ({po.qrNo})
              </b>
              <span className="muted">Status</span>
              <b>
                <StatusBadge status={po.statusLabel} />
              </b>
              <span className="muted">Expected delivery</span>
              <b>
                {draft ? (
                  <DateField
                    id="po-expected"
                    aria-label="Expected delivery"
                    value={expected}
                    onChange={setExpected}
                  />
                ) : (
                  formatDate(po.expectedDate)
                )}
              </b>
              {(draft || po.notes) && (
                <>
                  <span className="muted">Notes</span>
                  <b>
                    {draft ? (
                      <input aria-label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />
                    ) : (
                      po.notes
                    )}
                  </b>
                </>
              )}
            </div>
            <table>
              <thead>
                <tr>
                  <th>Item</th>
                  <th className="num">Qty</th>
                  <th className="num">Unit price</th>
                  <th className="num">Amount</th>
                  <th className="num">Received</th>
                  <th className="num">Damaged</th>
                </tr>
              </thead>
              <tbody>
                {po.lines.map((l) => {
                  const d = lines[l.itemCode];
                  const amount =
                    draft && d ? (Number(d.qty) || 0) * (Number(d.price) || 0) : Number(l.amount);
                  return (
                    <tr key={l.id}>
                      <td>{l.name}</td>
                      <td className="num">
                        {draft && d ? (
                          <input
                            aria-label={`Quantity ${l.name}`}
                            type="number"
                            min={0}
                            style={{ width: 90 }}
                            value={d.qty}
                            onChange={(e) =>
                              setLines({ ...lines, [l.itemCode]: { ...d, qty: e.target.value } })
                            }
                          />
                        ) : (
                          l.qty
                        )}
                      </td>
                      <td className="num">
                        {draft && d ? (
                          <input
                            aria-label={`Unit price ${l.name}`}
                            type="number"
                            min={0}
                            step="0.01"
                            style={{ width: 110 }}
                            value={d.price}
                            onChange={(e) =>
                              setLines({ ...lines, [l.itemCode]: { ...d, price: e.target.value } })
                            }
                          />
                        ) : (
                          formatMoney(l.unitPrice)
                        )}
                      </td>
                      <td className="num">{formatMoney(amount)}</td>
                      <td className="num">{l.receivedQty}</td>
                      <td className="num">{l.damagedQty || ""}</td>
                    </tr>
                  );
                })}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={3}>Total</td>
                  <td className="num">{formatMoney(total)}</td>
                  <td />
                  <td />
                </tr>
              </tfoot>
            </table>
            {draft && (
              <p className="small muted">
                Items and prices were copied from the chosen quotation. You can change them until the PO is
                approved (quantity 0 removes the line).
              </p>
            )}
            {po.cancelReason && <div className="banner bad">Cancelled: {po.cancelReason}</div>}
            {po.receipts.length > 0 && (
              <>
                <div className="section-title">Goods received</div>
                <div className="small">
                  {po.receipts.map((g) => (
                    <div key={g.id}>
                      <b className="mono">{g.grnNo}</b> {formatDate(g.date)}
                      {g.deliveryNote && ` · delivery note ${g.deliveryNote}`} ·{" "}
                      {g.lines
                        .map(
                          (l) => `${l.received} × ${l.name}` + (l.damaged ? ` (${l.damaged} damaged)` : ""),
                        )
                        .join(", ")}{" "}
                      · {formatMoney(g.value)}
                      {g.note && <span className="muted"> – {g.note}</span>}
                    </div>
                  ))}
                </div>
              </>
            )}
            <div className="section-title">History</div>
            <div className="small">
              {po.history.map((h, i) => (
                <div key={i}>
                  {formatDateTime(h.ts)} – {h.statusLabel} by {h.userName}
                  {h.note && ` (${h.note})`}
                </div>
              ))}
            </div>
          </>
        )}
      </FormDialog>
      {cancelling && po && (
        <ReasonDialog
          open
          onOpenChange={(o) => !o && onMode("view")}
          title={`Cancel ${po.poNo}?`}
          confirmLabel="Cancel PO"
          danger
          busy={cancel.isPending}
          onConfirm={doCancel}
        />
      )}
    </>
  );
}

// ------------------------------------------------------------------ receive goods (FR-20)

function ReceiveDialog({ po, today, onClose }: { po: PoDetail; today: string; onClose: () => void }) {
  const receive = useReceiveGoods();
  const [date, setDate] = useState<string | null>(today);
  const [deliveryNote, setDeliveryNote] = useState("");
  const [note, setNote] = useState("");
  const [qty, setQty] = useState<Record<number, { received: string; damaged: string }>>(() =>
    Object.fromEntries(po.lines.map((l) => [l.id, { received: String(l.stillDue), damaged: "0" }])),
  );
  const [error, setError] = useState("");

  function submit() {
    setError("");
    if (!date) {
      setError("Enter the date received.");
      return;
    }
    receive.mutate(
      {
        id: po.id,
        body: {
          date,
          deliveryNote,
          note,
          lines: po.lines
            .filter((l) => l.stillDue > 0)
            .map((l) => ({
              poLineId: l.id,
              received: Number(qty[l.id]?.received) || 0,
              damaged: Number(qty[l.id]?.damaged) || 0,
            })),
        },
      },
      {
        onSuccess: (r) => {
          toast(`${r.receipt.grnNo} saved – stock updated, PO is ${r.po.statusLabel}`, "ok");
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
      title={`Receive goods – ${po.poNo}`}
      onSubmit={submit}
      submitLabel="Save receipt"
      submitting={receive.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid">
        <div className="field req">
          <label htmlFor="grn-date">Date received</label>
          <DateField id="grn-date" value={date} onChange={setDate} today={today} />
        </div>
        <div className="field">
          <label htmlFor="grn-dn">Delivery note no.</label>
          <input id="grn-dn" value={deliveryNote} onChange={(e) => setDeliveryNote(e.target.value)} />
        </div>
        <div className="field span2">
          <label htmlFor="grn-note">Note</label>
          <input
            id="grn-note"
            placeholder="e.g. 2 bottles cracked"
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </div>
      </div>
      <table style={{ marginTop: 10 }}>
        <thead>
          <tr>
            <th>Item</th>
            <th className="num">Still due</th>
            <th className="num">Received now</th>
            <th className="num">Damaged on arrival</th>
            <th className="num">Added to stock</th>
          </tr>
        </thead>
        <tbody>
          {po.lines.map((l) => {
            const v = qty[l.id];
            const off = l.stillDue === 0;
            const good = off ? 0 : (Number(v?.received) || 0) - (Number(v?.damaged) || 0);
            return (
              <tr key={l.id}>
                <td>{l.name}</td>
                <td className="num">{l.stillDue}</td>
                <td className="num">
                  <input
                    aria-label={`Received ${l.name}`}
                    type="number"
                    min={0}
                    style={{ width: 90 }}
                    disabled={off}
                    value={off ? "0" : v.received}
                    onChange={(e) => setQty({ ...qty, [l.id]: { ...v, received: e.target.value } })}
                  />
                </td>
                <td className="num">
                  <input
                    aria-label={`Damaged ${l.name}`}
                    type="number"
                    min={0}
                    style={{ width: 90 }}
                    disabled={off}
                    value={off ? "0" : v.damaged}
                    onChange={(e) => setQty({ ...qty, [l.id]: { ...v, damaged: e.target.value } })}
                  />
                </td>
                <td className="num" data-testid={`good-${l.itemCode}`}>
                  {good}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      <p className="small muted">
        Good bottles go to &quot;Empty in store&quot;; products go to product stock. Damaged items are not
        added or paid for.
      </p>
    </FormDialog>
  );
}
