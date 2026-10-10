"use client";

/*
 * Prices & deposits tab (FR-04, FR-07, BR-08, prototype bottles-products.html): water price grid (rows = active bottle
 * types, columns = active customer types) and deposits. Click a price to change it from a date with a reason – a new
 * entry; a later date is "scheduled" and shown under the current price. Price history pop-up with
 * Current / Scheduled / Old. (The customer agreed prices card comes with Customers, M05.)
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm } from "react-hook-form";
import type { z } from "zod";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { MoneyText } from "@/components/shared/MoneyText";
import { Badge } from "@/components/shared/StatusBadge";
import { useCan } from "@/features/auth/access";
import type { PriceCell, PriceHistoryRow, PriceMatrix } from "@/lib/api/types";
import { addDaysIso, formatDate, todayIso } from "@/lib/format";
import { showServerErrors } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { usePriceHistory, usePriceMatrix, useSetPrice } from "./api";
import { priceChangeSchema, type PriceChangeValues } from "./schemas";

/** What the change pop-up is for: the cell plus words for its title. */
interface Target {
  cell: PriceCell;
  title: string;
}

function PriceButton({ cell, onOpen, canEdit }: { cell: PriceCell; onOpen: () => void; canEdit: boolean }) {
  const label = cell.current ? <MoneyText value={cell.current.price} /> : "Set price";
  return (
    <>
      {canEdit ? (
        <button className="btn link" type="button" style={{ fontWeight: 600, fontSize: 14 }} onClick={onOpen}>
          {label}
        </button>
      ) : (
        <b>{cell.current ? label : "–"}</b>
      )}
      {cell.current && (
        <> {cell.confirmed ? <Badge kind="ok">confirmed</Badge> : <Badge kind="warn">example</Badge>}</>
      )}
      {cell.next && (
        <div className="small" style={{ color: "var(--brand)" }}>
          → <MoneyText value={cell.next.price} /> from {formatDate(cell.next.effectiveFrom)}
        </div>
      )}
    </>
  );
}

function PriceChangeDialog({ target, onClose }: { target: Target; onClose: () => void }) {
  const today = todayIso();
  const schema = priceChangeSchema(today);
  const setPrice = useSetPrice();
  const [error, setError] = useState("");
  const {
    register,
    control,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<PriceChangeValues, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { price: "", effectiveFrom: addDaysIso(today, 1), reason: "" },
  });
  const { cell } = target;

  const submit = handleSubmit((v) => {
    setError("");
    setPrice.mutate(
      {
        kind: cell.kind as "WATER" | "DEPOSIT",
        bottleTypeCode: cell.bottleTypeCode,
        customerTypeId: cell.customerTypeId ?? undefined,
        price: Number(v.price),
        effectiveFrom: v.effectiveFrom as string,
        reason: v.reason,
      },
      {
        onSuccess: (row) => {
          toast(row.status === "SCHEDULED" ? "Price change scheduled" : "Price updated", "ok");
          onClose();
        },
        onError: (err) =>
          setError(showServerErrors(err, ["price", "effectiveFrom", "reason"] as const, setFieldError) ?? ""),
      },
    );
  });

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={target.title}
      onSubmit={() => void submit()}
      submitting={setPrice.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <p className="muted">
        Now:{" "}
        {cell.current ? (
          <>
            <MoneyText value={cell.current.price} /> (from {formatDate(cell.current.effectiveFrom)})
          </>
        ) : (
          "not set"
        )}
        {cell.next && (
          <>
            {" "}
            · scheduled <MoneyText value={cell.next.price} /> from {formatDate(cell.next.effectiveFrom)}
          </>
        )}
      </p>
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req">
          <label htmlFor="pc-price">New price (Rs.)</label>
          <input
            id="pc-price"
            type="number"
            min="0"
            step="0.01"
            autoFocus
            className={errors.price ? "invalid num" : "num"}
            {...register("price")}
          />
          {errors.price && <span className="error">{errors.price.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="pc-from">From date</label>
          <Controller
            control={control}
            name="effectiveFrom"
            render={({ field }) => (
              <DateField
                id="pc-from"
                value={field.value}
                onChange={field.onChange}
                onBlur={field.onBlur}
                invalid={!!errors.effectiveFrom}
              />
            )}
          />
          {errors.effectiveFrom ? (
            <span className="error">{errors.effectiveFrom.message}</span>
          ) : (
            <span className="help">Past bills keep the price of their date.</span>
          )}
        </div>
        <div className="field req full">
          <label htmlFor="pc-reason">Reason</label>
          <input
            id="pc-reason"
            placeholder="e.g. Factory charge increase"
            className={errors.reason ? "invalid" : undefined}
            {...register("reason")}
          />
          {errors.reason && <span className="error">{errors.reason.message}</span>}
        </div>
      </div>
    </FormDialog>
  );
}

const STATUS_BADGE: Record<string, { label: string; kind: "ok" | "info" | "" }> = {
  CURRENT: { label: "Current", kind: "ok" },
  SCHEDULED: { label: "Scheduled", kind: "info" },
  OLD: { label: "Old", kind: "" },
};

const historyColumns: Column<PriceHistoryRow>[] = [
  { key: "from", header: "From", cell: (r) => <span className="nowrap">{formatDate(r.effectiveFrom)}</span> },
  { key: "price", header: "Price", cell: (r) => r.label },
  { key: "amount", header: "Amount", align: "num", cell: (r) => <MoneyText value={r.price} /> },
  { key: "reason", header: "Reason", cell: (r) => r.reason },
  { key: "by", header: "By", cell: (r) => r.createdByName ?? r.createdBy },
  {
    key: "status",
    header: "",
    cell: (r) => {
      const s = STATUS_BADGE[r.status] ?? { label: r.status, kind: "" as const };
      return <Badge kind={s.kind}>{s.label}</Badge>;
    },
  },
];

function PriceHistoryDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const history = usePriceHistory(open);
  return (
    <FormDialog open={open} onOpenChange={(o) => !o && onClose()} title="Price history" wide>
      {history.isError ? (
        <div className="banner bad">{history.error.message}</div>
      ) : (
        <DataTable
          columns={historyColumns}
          rows={history.data}
          loading={history.isPending}
          rowKey={(r) => r.id}
          empty="No prices yet."
        />
      )}
    </FormDialog>
  );
}

function bottleName(matrix: PriceMatrix, code: string): string {
  return matrix.bottleTypes.find((b) => b.code === code)?.name ?? code;
}

export function PricesTab() {
  const matrix = usePriceMatrix();
  const canEdit = useCan(P.MASTERDATA_EDIT);
  const [target, setTarget] = useState<Target | null>(null);
  const [historyOpen, setHistoryOpen] = useState(false);

  if (matrix.isError) return <div className="banner bad">{matrix.error.message}</div>;
  const m = matrix.data;

  return (
    <>
      <div className="banner info">
        Click a price to change it. Prices marked <Badge kind="warn">example</Badge> are placeholders until
        the client confirms them. Past bills keep the price that applied on their date.
      </div>

      <div className="card">
        <div className="card-head">
          <h2>Water price per bottle</h2>
          <button className="btn" type="button" onClick={() => setHistoryOpen(true)}>
            Price history
          </button>
        </div>
        {!m ? (
          <p className="muted">Loading…</p>
        ) : m.bottleTypes.length === 0 || m.customerTypes.length === 0 ? (
          <p className="muted">Add an active bottle type and customer type first.</p>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Bottle</th>
                  {m.customerTypes.map((t) => (
                    <th key={t.id} className="num">
                      {t.name}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {m.bottleTypes.map((b) => (
                  <tr key={b.code}>
                    <td>
                      <b>{b.name}</b>
                    </td>
                    {m.customerTypes.map((t) => {
                      const cell = m.water.find(
                        (c) => c.bottleTypeCode === b.code && c.customerTypeId === t.id,
                      );
                      return (
                        <td key={t.id} className="num">
                          {cell && (
                            <PriceButton
                              cell={cell}
                              canEdit={canEdit}
                              onOpen={() => setTarget({ cell, title: `Water price – ${b.name} / ${t.name}` })}
                            />
                          )}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <div className="card">
        <h2>Bottle deposit (per new bottle, not refundable)</h2>
        {m && (
          <div className="table-wrap">
            <table>
              <tbody>
                {m.deposits.map((cell) => (
                  <tr key={cell.key}>
                    <td>
                      <b>{bottleName(m, cell.bottleTypeCode)}</b>
                    </td>
                    <td className="num">
                      <PriceButton
                        cell={cell}
                        canEdit={canEdit}
                        onOpen={() =>
                          setTarget({ cell, title: `Deposit – ${bottleName(m, cell.bottleTypeCode)}` })
                        }
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {target && <PriceChangeDialog target={target} onClose={() => setTarget(null)} />}
      <PriceHistoryDialog open={historyOpen} onClose={() => setHistoryOpen(false)} />
    </>
  );
}
