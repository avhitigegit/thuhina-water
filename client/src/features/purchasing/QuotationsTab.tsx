"use client";

/*
 * Quotations tab (FR-15 – FR-18, prototype purchasing/purchasing.html): quotation requests with "n of m suppliers", the
 * chosen supplier and status. New request pop-up (items + suppliers – suppliers of the chosen items are ticked for you);
 * request pop-up with the side-by-side comparison (lowest price shown only as information; the owner chooses with a
 * reason), + Add quotation and Create purchase order. Admin full; Accountant view only.
 */
import { Fragment, useMemo, useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { ReasonDialog } from "@/components/shared/ReasonDialog";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Can, useCan } from "@/features/auth/access";
import { useBottleTypes, useProducts } from "@/features/masterdata/api";
import { useSuppliers } from "@/features/partners/api";
import type { QrDetail, QrRow, QuoteColumn } from "@/lib/api/types";
import { addDaysIso, formatDate, formatMoney, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import {
  attachmentUrl,
  useAddQuotation,
  useAttachQuotation,
  useChooseQuotation,
  useCreatePo,
  useCreateRequest,
  useQuotationRequest,
  useQuotationRequests,
} from "./api";
import { errorText, itemText } from "./labels";

type View = { kind: "new" } | { kind: "request"; id: number } | { kind: "add"; id: number } | null;

export function QuotationsTab({
  today = todayIso(),
  onOpenPo,
}: {
  today?: string;
  onOpenPo: (id: number) => void;
}) {
  const requests = useQuotationRequests();
  const [view, setView] = useState<View>(null);

  const columns: Column<QrRow>[] = [
    {
      key: "request",
      header: "Request",
      cell: (q) => (
        <>
          <b className="mono">{q.qrNo}</b>
          <div className="small muted">{formatDate(q.date)}</div>
        </>
      ),
    },
    {
      key: "items",
      header: "Items",
      cell: (q) =>
        q.items.map((i) => (
          <div key={i.itemCode} className="nowrap">
            {itemText(i)}
          </div>
        )),
    },
    {
      key: "quotations",
      header: "Quotations",
      cell: (q) => `${q.quotations} of ${q.suppliersAsked} suppliers`,
    },
    { key: "chosen", header: "Chosen", cell: (q) => q.chosen?.name ?? "" },
    {
      key: "status",
      header: "Status",
      cell: (q) => (
        <>
          <StatusBadge status={q.statusLabel} />
          {q.po && <div className="small muted mono">{q.po.poNo}</div>}
        </>
      ),
    },
    {
      key: "open",
      header: "",
      className: "actions",
      cell: (q) => (
        <button
          className="btn sm primary"
          type="button"
          onClick={() => setView({ kind: "request", id: q.id })}
        >
          Open
        </button>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Quotation requests</h2>
        <Can permission={P.PURCHASING_EDIT}>
          <button className="btn primary" type="button" onClick={() => setView({ kind: "new" })}>
            + New request
          </button>
        </Can>
      </div>
      <p className="small muted">
        Ask suppliers for prices, record their quotations, compare them side by side and choose one. The
        chosen quotation becomes a purchase order.
      </p>
      {requests.isError ? (
        <div className="banner bad">{requests.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={requests.data}
          loading={requests.isPending}
          rowKey={(q) => q.id}
          empty="No quotation requests yet."
        />
      )}
      {view?.kind === "new" && <NewRequestDialog today={today} onClose={() => setView(null)} />}
      {view?.kind === "request" && (
        <RequestDialog
          id={view.id}
          onClose={() => setView(null)}
          onAdd={() => setView({ kind: "add", id: view.id })}
          onPoCreated={(poId) => {
            setView(null);
            onOpenPo(poId);
          }}
        />
      )}
      {view?.kind === "add" && (
        <AddQuotationDialog
          id={view.id}
          today={today}
          onClose={() => setView({ kind: "request", id: view.id })}
        />
      )}
    </div>
  );
}

// ------------------------------------------------------------------ new request (FR-15)

interface Line {
  key: number;
  item: string;
  qty: string;
}

function NewRequestDialog({ today, onClose }: { today: string; onClose: () => void }) {
  const bottles = useBottleTypes();
  const products = useProducts();
  const suppliers = useSuppliers();
  const create = useCreateRequest();
  const items = useMemo(
    () => [
      ...(bottles.data ?? []).filter((b) => b.active).map((b) => ({ code: b.code, name: `Empty ${b.name}` })),
      ...(products.data ?? []).filter((p) => p.active).map((p) => ({ code: p.code, name: p.name })),
    ],
    [bottles.data, products.data],
  );
  const active = (suppliers.data ?? []).filter((s) => s.active);

  const [date, setDate] = useState<string | null>(today);
  const [requiredBy, setRequiredBy] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [lines, setLines] = useState<Line[]>([{ key: 1, item: "", qty: "" }]);
  const [ticked, setTicked] = useState<number[] | null>(null);
  const [edited, setEdited] = useState(false);
  const [error, setError] = useState("");

  // The first line starts with the largest bottle (prototype: 20L) once the items are loaded.
  const shown =
    edited || !items[0] ? lines : lines.map((l, i) => (i === 0 ? { ...l, item: items[0].code } : l));
  const chosen = shown.map((l) => l.item).filter(Boolean);
  // Suppliers of the chosen items are ticked for you; a change of items ticks them again (the user can still change).
  const autoTicked = active.filter((s) => s.items.some((i) => chosen.includes(i.code))).map((s) => s.id);
  const selected = ticked ?? autoTicked;

  function changeLines(next: Line[]) {
    setLines(next);
    setEdited(true);
  }

  function setLine(key: number, patch: Partial<Line>) {
    changeLines(shown.map((l) => (l.key === key ? { ...l, ...patch } : l)));
    if (patch.item !== undefined) setTicked(null);
  }

  function submit() {
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    create.mutate(
      {
        date,
        requiredBy: requiredBy ?? undefined,
        note,
        items: shown.filter((l) => l.item).map((l) => ({ itemCode: l.item, qty: Number(l.qty) || 0 })),
        supplierIds: selected,
      },
      {
        onSuccess: (r) => {
          toast(`${r.qrNo} created`, "ok");
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
      title="New quotation request"
      onSubmit={submit}
      submitLabel="Create request"
      submitting={create.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="qr-no">Request no.</label>
          <input id="qr-no" value="Generated on save" readOnly />
        </div>
        <div className="field req">
          <label htmlFor="qr-date">Date</label>
          <DateField id="qr-date" value={date} onChange={setDate} today={today} />
        </div>
        <div className="field">
          <label htmlFor="qr-req">Needed by</label>
          <DateField id="qr-req" value={requiredBy} onChange={setRequiredBy} today={today} />
        </div>
        <div className="field span2">
          <label htmlFor="qr-note">Note</label>
          <input id="qr-note" placeholder="Purpose" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </div>
      <div className="section-title">Items</div>
      <table>
        <tbody>
          {shown.map((l, i) => (
            <tr key={l.key}>
              <td>
                <select
                  aria-label={`Item ${i + 1}`}
                  value={l.item}
                  onChange={(e) => setLine(l.key, { item: e.target.value })}
                >
                  <option value="">Select item</option>
                  {items.map((it) => (
                    <option key={it.code} value={it.code}>
                      {it.name}
                    </option>
                  ))}
                </select>
              </td>
              <td>
                <input
                  aria-label={`Quantity ${i + 1}`}
                  type="number"
                  min={1}
                  style={{ width: 110 }}
                  value={l.qty}
                  onChange={(e) => setLine(l.key, { qty: e.target.value })}
                />
              </td>
              <td>
                <button
                  type="button"
                  className="btn sm danger"
                  aria-label={`Remove item ${i + 1}`}
                  onClick={() => {
                    changeLines(shown.filter((x) => x.key !== l.key));
                    setTicked(null);
                  }}
                >
                  ×
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      <button
        type="button"
        className="btn sm"
        style={{ marginTop: 6 }}
        onClick={() =>
          changeLines([...shown, { key: Math.max(0, ...shown.map((l) => l.key)) + 1, item: "", qty: "" }])
        }
      >
        + Add item
      </button>
      <div className="section-title">
        Suppliers asked to quote{" "}
        <span className="small muted" style={{ textTransform: "none", fontWeight: 400 }}>
          – suppliers of the chosen items are ticked for you
        </span>
      </div>
      <div className="stack">
        {active.map((s) => (
          <label key={s.id} className="check">
            <input
              type="checkbox"
              checked={selected.includes(s.id)}
              onChange={(e) =>
                setTicked(e.target.checked ? [...selected, s.id] : selected.filter((x) => x !== s.id))
              }
            />{" "}
            <b>{s.name}</b> <span className="small muted">{s.items.map((i) => i.name).join(", ")}</span>
          </label>
        ))}
      </div>
    </FormDialog>
  );
}

// ------------------------------------------------------------------ request with the comparison (FR-17)

function RequestDialog({
  id,
  onClose,
  onAdd,
  onPoCreated,
}: {
  id: number;
  onClose: () => void;
  onAdd: () => void;
  onPoCreated: (poId: number) => void;
}) {
  const request = useQuotationRequest(id);
  const canEdit = useCan(P.PURCHASING_EDIT);
  const choose = useChooseQuotation();
  const createPo = useCreatePo();
  const [choosing, setChoosing] = useState<QuoteColumn | null>(null);
  const qr = request.data;
  const chosen = qr?.quotations.find((q) => q.selected);

  function doChoose(reason: string) {
    if (!choosing) return;
    choose.mutate(
      { quotationId: choosing.id, reason },
      {
        onSuccess: () => {
          setChoosing(null);
          toast("Quotation chosen", "ok");
        },
        onError: (err) => {
          setChoosing(null);
          toast(errorText(err), "bad");
        },
      },
    );
  }

  function doCreatePo() {
    if (!chosen) return;
    createPo.mutate(chosen.id, {
      onSuccess: (po) => {
        toast(`Purchase order ${po.poNo} created as Draft`, "ok");
        onPoCreated(po.id);
      },
      onError: (err) => toast(errorText(err), "bad"),
    });
  }

  const title = qr ? `${qr.qrNo} – ${qr.items.map(itemText).join(", ")}` : "Quotation request";
  return (
    <>
      <FormDialog
        open={!choosing}
        wide
        onOpenChange={(o) => !o && onClose()}
        title={title}
        extraButtons={
          qr &&
          canEdit && (
            <>
              {qr.canAddQuotation && (
                <button type="button" className="btn" onClick={onAdd}>
                  + Add quotation
                </button>
              )}
              {qr.canCreatePo && (
                <button
                  type="button"
                  className="btn primary"
                  disabled={createPo.isPending}
                  onClick={doCreatePo}
                >
                  Create purchase order
                </button>
              )}
            </>
          )
        }
      >
        {request.isError ? (
          <div className="banner bad">{request.error.message}</div>
        ) : !qr ? (
          <p className="muted">Loading…</p>
        ) : (
          <Comparison qr={qr} canChoose={canEdit && qr.canChoose} onChoose={setChoosing} />
        )}
      </FormDialog>
      {choosing && (
        <ReasonDialog
          open
          onOpenChange={(o) => !o && setChoosing(null)}
          title={`Choose ${choosing.supplier.name}?`}
          label="Reason for choosing"
          confirmLabel="Choose"
          busy={choose.isPending}
          onConfirm={doChoose}
        >
          <p>Total {formatMoney(choosing.total)}.</p>
        </ReasonDialog>
      )}
    </>
  );
}

function Comparison({
  qr,
  canChoose,
  onChoose,
}: {
  qr: QrDetail;
  canChoose: boolean;
  onChoose: (q: QuoteColumn) => void;
}) {
  const quotes = qr.quotations;
  const chosen = quotes.find((q) => q.selected);
  const lowestStyle = { background: "#f2faf5" };
  return (
    <>
      <p className="muted">
        {formatDate(qr.date)}
        {qr.requiredBy && ` · needed by ${formatDate(qr.requiredBy)}`}
        {qr.note && ` · ${qr.note}`} · <StatusBadge status={qr.statusLabel} />
      </p>
      {chosen ? (
        <div className="banner ok">
          Chosen: <b>{chosen.supplier.name}</b> by {chosen.selectedByName} – “{chosen.selectionReason}”
          {qr.po && (
            <>
              {" "}
              · Purchase order <b className="mono">{qr.po.poNo}</b> ({qr.po.statusLabel})
            </>
          )}
        </div>
      ) : (
        quotes.length > 0 && (
          <div className="banner info">
            Compare and choose one. Lowest prices are highlighted only as information – the system does not
            choose.
          </div>
        )
      )}
      {quotes.length === 0 ? (
        <p className="empty-state">No quotations recorded yet.</p>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th />
                {quotes.map((q) => (
                  <th
                    key={q.id}
                    style={{ minWidth: 170, ...(q.selected ? { background: "var(--ok-soft)" } : {}) }}
                  >
                    {q.supplier.name}
                    <div className="small muted" style={{ fontWeight: 400 }}>
                      {[q.supplierRef ?? q.quoteNo, formatDate(q.date)].join(" · ")}
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {qr.items.map((it, row) => (
                <tr key={it.itemCode}>
                  <td>
                    <b>{it.name}</b>
                    <div className="small muted">Qty {it.qty.toLocaleString("en-US")}</div>
                  </td>
                  {quotes.map((q) => {
                    const p = q.prices[row];
                    return p?.unitPrice == null ? (
                      <td key={q.id} className="muted">
                        Not quoted
                      </td>
                    ) : (
                      <td key={q.id} style={p.lowest ? lowestStyle : undefined}>
                        <b>{formatMoney(p.unitPrice)}</b> each {p.lowest && <Badge kind="ok">lowest</Badge>}
                      </td>
                    );
                  })}
                </tr>
              ))}
              <tr>
                <td>
                  <b>Total</b>
                </td>
                {quotes.map((q) => (
                  <td key={q.id}>
                    <b>{formatMoney(q.total)}</b>
                    {!q.complete && (
                      <div>
                        <Badge kind="warn">Does not cover all items</Badge>
                      </div>
                    )}
                  </td>
                ))}
              </tr>
              <tr>
                <td>Delivery</td>
                {quotes.map((q) => (
                  <td key={q.id}>{q.deliveryDays} days</td>
                ))}
              </tr>
              <tr>
                <td>Valid until</td>
                {quotes.map((q) => (
                  <td key={q.id}>
                    {formatDate(q.validUntil)} {q.expired && <Badge kind="bad">expired</Badge>}
                  </td>
                ))}
              </tr>
              <tr>
                <td>Notes</td>
                {quotes.map((q) => (
                  <td key={q.id} className="small">
                    {q.notes ?? "–"}
                    {q.attachmentName && (
                      <div>
                        📎{" "}
                        {q.attachmentFile ? (
                          <a href={attachmentUrl(q.id)} target="_blank" rel="noopener noreferrer">
                            {q.attachmentName}
                          </a>
                        ) : (
                          q.attachmentName
                        )}
                      </div>
                    )}
                  </td>
                ))}
              </tr>
              {canChoose && (
                <tr>
                  <td />
                  {quotes.map((q) => (
                    <td key={q.id}>
                      <button type="button" className="btn primary sm" onClick={() => onChoose(q)}>
                        Choose
                      </button>
                    </td>
                  ))}
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      <div className="section-title">Suppliers asked</div>
      <div className="small">
        {qr.suppliers.map((s, i) => (
          <Fragment key={s.id}>
            {i > 0 && " · "}
            {s.name} {s.quoted ? <Badge kind="ok">quoted</Badge> : <Badge>waiting</Badge>}
          </Fragment>
        ))}
      </div>
    </>
  );
}

// ------------------------------------------------------------------ add quotation (FR-16)

function AddQuotationDialog({ id, today, onClose }: { id: number; today: string; onClose: () => void }) {
  const request = useQuotationRequest(id);
  const add = useAddQuotation();
  const attach = useAttachQuotation();
  const qr = request.data;
  const pending = qr?.suppliers.filter((s) => !s.quoted) ?? [];

  const [supplier, setSupplier] = useState<string>("");
  const [ref, setRef] = useState("");
  const [date, setDate] = useState<string | null>(today);
  const [validUntil, setValidUntil] = useState<string | null>(addDaysIso(today, 30));
  const [days, setDays] = useState("7");
  const [prices, setPrices] = useState<Record<string, string>>({});
  const [notes, setNotes] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState("");
  const supplierId = supplier || (pending[0] ? String(pending[0].id) : "");

  function submit() {
    if (!qr) return;
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    add.mutate(
      {
        qrId: qr.id,
        body: {
          supplierId: supplierId ? Number(supplierId) : undefined,
          supplierRef: ref,
          date,
          validUntil: validUntil ?? undefined,
          deliveryDays: days.trim() === "" ? undefined : Number(days),
          prices: qr.items.map((i) => ({
            itemCode: i.itemCode,
            unitPrice: prices[i.itemCode]?.trim() ? Number(prices[i.itemCode]) : null,
          })),
          notes,
        },
      },
      {
        onSuccess: (detail) => {
          const saved = detail.quotations.find((q) => q.supplier.id === Number(supplierId));
          if (file && saved) {
            attach.mutate(
              { quotationId: saved.id, file },
              {
                onSuccess: () => toast("Quotation saved", "ok"),
                onError: (err) =>
                  toast(`Quotation saved, but the file was not attached: ${errorText(err)}`, "warn"),
                onSettled: onClose,
              },
            );
          } else {
            toast("Quotation saved", "ok");
            onClose();
          }
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
      title={qr ? `Add quotation – ${qr.qrNo}` : "Add quotation"}
      onSubmit={submit}
      submitLabel="Save quotation"
      submitting={add.isPending || attach.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      {!qr ? (
        <p className="muted">Loading…</p>
      ) : (
        <>
          <div className="form-grid">
            <div className="field req span2">
              <label htmlFor="q-sup">Supplier</label>
              <select id="q-sup" value={supplierId} onChange={(e) => setSupplier(e.target.value)}>
                {pending.map((s) => (
                  <option key={s.id} value={s.id}>
                    {s.name}
                  </option>
                ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="q-ref">Supplier reference (on their quotation)</label>
              <input id="q-ref" placeholder="optional" value={ref} onChange={(e) => setRef(e.target.value)} />
            </div>
            <div className="field req">
              <label htmlFor="q-date">Date</label>
              <DateField id="q-date" value={date} onChange={setDate} today={today} />
            </div>
            <div className="field req">
              <label htmlFor="q-valid">Valid until</label>
              <DateField id="q-valid" value={validUntil} onChange={setValidUntil} today={today} />
            </div>
            <div className="field req">
              <label htmlFor="q-days">Delivery (days)</label>
              <input
                id="q-days"
                type="number"
                min={0}
                value={days}
                onChange={(e) => setDays(e.target.value)}
              />
            </div>
          </div>
          <div className="section-title">Unit prices (leave blank if not quoted)</div>
          <table>
            <tbody>
              {qr.items.map((i) => {
                const p = Number(prices[i.itemCode]);
                return (
                  <tr key={i.itemCode}>
                    <td>{i.name}</td>
                    <td className="num">× {i.qty.toLocaleString("en-US")}</td>
                    <td>
                      <input
                        aria-label={`Unit price ${i.name}`}
                        type="number"
                        min={0}
                        step="0.01"
                        style={{ width: 130 }}
                        placeholder="Rs. each"
                        value={prices[i.itemCode] ?? ""}
                        onChange={(e) => setPrices({ ...prices, [i.itemCode]: e.target.value })}
                      />
                    </td>
                    <td className="num">{p > 0 ? formatMoney(p * i.qty) : ""}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="form-grid" style={{ marginTop: 10 }}>
            <div className="field span2">
              <label htmlFor="q-notes">Notes</label>
              <input
                id="q-notes"
                placeholder="Transport, warranty, discounts…"
                value={notes}
                onChange={(e) => setNotes(e.target.value)}
              />
            </div>
            <div className="field">
              <label htmlFor="q-file">Scanned quotation</label>
              <input
                id="q-file"
                type="file"
                accept=".pdf,.jpg,.jpeg,.png"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
              />
            </div>
          </div>
        </>
      )}
    </FormDialog>
  );
}
