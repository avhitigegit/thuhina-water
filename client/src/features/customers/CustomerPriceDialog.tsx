"use client";

/*
 * Agreed prices for one customer (FR-05, prototype UI.custPriceDialog) – Admin. One row per bottle type: standard
 * price, price now (+ scheduled change), filling cost as a guide, new agreed price with the difference and a margin
 * hint, and "back to standard". One date range and reason for all rows.
 */
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { MoneyText } from "@/components/shared/MoneyText";
import { ApiError } from "@/lib/api/client";
import type { CustomerDetail, WaterPriceView } from "@/lib/api/types";
import { formatMoney, todayIso } from "@/lib/format";
import { toast } from "@/lib/toast";
import { useEndCustomerPrice, useSetCustomerPrice } from "./api";
import { PriceText } from "./PriceText";

function Hint({ row, value }: { row: WaterPriceView; value: string }) {
  const v = Number(value);
  if (!value || !(v > 0)) return null;
  const std = row.standard ?? 0;
  const parts: React.ReactNode[] = [];
  if (std) {
    if (v < std)
      parts.push(
        <span key="d" style={{ color: "var(--warn)" }}>
          {formatMoney(std - v)} ({Math.round(((std - v) / std) * 100)}%) below standard
        </span>,
      );
    else if (v > std) parts.push(<span key="d">{formatMoney(v - std)} above standard</span>);
    else parts.push(<span key="d">same as standard</span>);
  }
  if (row.fillingCharge != null) {
    parts.push(
      v <= row.fillingCharge ? (
        <b key="m" style={{ color: "var(--bad)" }}>
          {" "}
          · not above the filling cost
        </b>
      ) : (
        <span key="m">
          {" "}
          · {formatMoney((v - row.fillingCharge) * row.bottlesPerMonth)} a month after filling
        </span>
      ),
    );
  }
  return <div className="small">{parts}</div>;
}

export function CustomerPriceDialog({
  customer,
  onClose,
}: {
  customer: CustomerDetail;
  onClose: () => void;
}) {
  const today = todayIso();
  const setPrice = useSetCustomerPrice();
  const endPrice = useEndCustomerPrice();
  const [values, setValues] = useState<Record<string, string>>({});
  const [back, setBack] = useState<Record<string, boolean>>({});
  const [from, setFrom] = useState<string | null>(today);
  const [until, setUntil] = useState<string | null>(null);
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function save() {
    setError("");
    const todo = customer.prices.filter(
      (p) => back[p.bottleTypeCode] || (values[p.bottleTypeCode] ?? "").trim(),
    );
    if (!todo.length) return setError('Enter a new price or tick "back to standard".');
    if (!from) return setError("Enter the date the agreed price starts.");
    if (until && until < from) return setError("The end date cannot be before the start date.");
    if (!reason.trim())
      return setError("Enter the reason for the agreed price (e.g. about 3,500 bottles a month).");
    setBusy(true);
    try {
      for (const p of todo) {
        if (back[p.bottleTypeCode]) {
          await endPrice.mutateAsync({
            id: customer.id,
            body: { bottleTypeCode: p.bottleTypeCode, from, reason: reason.trim() },
          });
        } else {
          await setPrice.mutateAsync({
            id: customer.id,
            body: {
              bottleTypeCode: p.bottleTypeCode,
              price: Number(values[p.bottleTypeCode]),
              from,
              until: until ?? undefined,
              reason: reason.trim(),
            },
          });
        }
      }
      toast(`${todo.length} price${todo.length > 1 ? "s" : ""} saved for ${customer.name}`, "ok");
      onClose();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Something went wrong. Please try again.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title={`Agreed prices – ${customer.name}`}
      submitLabel="Save prices"
      onSubmit={() => void save()}
      submitting={busy}
    >
      {error && <div className="banner bad">{error}</div>}
      <p className="muted">
        {customer.typeName} customer. An agreed price replaces the standard {customer.typeName} price for this
        customer only. Bills already saved keep their price.
      </p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Bottle</th>
              <th className="num">Standard</th>
              <th className="num">Price now</th>
              <th
                className="num"
                title="Filling factory charge per bottle – a guide for the lowest sensible price"
              >
                Filling cost
              </th>
              <th>New agreed price (Rs.)</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {customer.prices.map((p) => (
              <tr key={p.bottleTypeCode}>
                <td>
                  <b>{p.label}</b>
                  <div className="small muted">about {p.bottlesPerMonth} a month</div>
                </td>
                <td className="num">{p.standard != null ? <MoneyText value={p.standard} /> : "–"}</td>
                <td className="num">
                  <PriceText price={{ ...p, agreed: false }} showNext />
                </td>
                <td className="num">
                  {p.fillingCharge != null ? <MoneyText value={p.fillingCharge} /> : "–"}
                </td>
                <td>
                  <input
                    type="number"
                    min="1"
                    step="0.5"
                    style={{ width: 110 }}
                    aria-label={`New agreed price ${p.label}`}
                    placeholder="no change"
                    disabled={back[p.bottleTypeCode]}
                    value={values[p.bottleTypeCode] ?? ""}
                    onChange={(e) => setValues({ ...values, [p.bottleTypeCode]: e.target.value })}
                  />
                  <Hint row={p} value={values[p.bottleTypeCode] ?? ""} />
                </td>
                <td>
                  {(p.agreed || p.next) && (
                    <label className="check small">
                      <input
                        type="checkbox"
                        checked={!!back[p.bottleTypeCode]}
                        onChange={(e) => setBack({ ...back, [p.bottleTypeCode]: e.target.checked })}
                      />{" "}
                      back to standard
                    </label>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="form-grid" style={{ marginTop: 12 }}>
        <div className="field req">
          <label htmlFor="cp-from">From</label>
          <DateField id="cp-from" value={from} onChange={setFrom} />
        </div>
        <div className="field">
          <label htmlFor="cp-until">Until (optional)</label>
          <DateField id="cp-until" value={until} onChange={setUntil} />
          <span className="help">e.g. end of a tender or trial</span>
        </div>
        <div className="field req span2">
          <label htmlFor="cp-reason">Reason</label>
          <input
            id="cp-reason"
            value={reason}
            placeholder="e.g. Volume price – about 3,500 bottles a month"
            onChange={(e) => setReason(e.target.value)}
          />
        </div>
      </div>
      <p className="small muted">
        Only the Admin can set agreed prices. Every change is kept in the customer&apos;s price history and
        the audit log.
      </p>
    </FormDialog>
  );
}
