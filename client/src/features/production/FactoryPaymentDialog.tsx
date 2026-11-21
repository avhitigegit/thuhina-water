"use client";

import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { PaidByFields, payFields, type PayMethod } from "@/components/shared/PaidBy";
import type { FactoryOwed } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { toast } from "@/lib/toast";
import { useFactoryBills, usePayFactory } from "./api";
import { errorText } from "./ProductionDialogs";

/**
 * Pay a factory – Bill to Bill (client 21/11/2026, FR-26): the unpaid filling bills of the factory are listed (one per
 * load received, FR-0001); tick the bills to pay and the amount fills in with what is due on them. Less than that is a
 * part payment of the ticked bills, oldest first. With "Bill to Bill" as Paid by, the bill numbers are the reference.
 */
export function FactoryPaymentDialog({
  factories,
  today,
  onClose,
}: {
  factories: FactoryOwed[];
  today: string;
  onClose: () => void;
}) {
  const pay = usePayFactory();
  const start = factories.find((f) => Number(f.owed) > 0) ?? factories[0];
  const [factoryId, setFactoryId] = useState(String(start?.id ?? ""));
  const bills = useFactoryBills(factoryId ? Number(factoryId) : null, true);
  const [ticked, setTicked] = useState<number[]>([]);
  const [date, setDate] = useState<string | null>(today);
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PayMethod>("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");

  // Oldest bill first, as the payment is spread.
  const open = [...(bills.data ?? [])].reverse();
  const dueOf = (ids: number[]) =>
    open.filter((b) => ids.includes(b.id)).reduce((sum, b) => sum + Number(b.due), 0);
  const tickedDue = dueOf(ticked);
  const all = open.length > 0 && ticked.length === open.length;

  function tick(ids: number[]) {
    setTicked(ids);
    const due = dueOf(ids);
    setAmount(due > 0 ? due.toFixed(2) : "");
  }

  function submit() {
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    if (ticked.length === 0) {
      setError("Tick the bills this payment pays.");
      return;
    }
    pay.mutate(
      {
        factoryId: factoryId ? Number(factoryId) : undefined,
        billIds: ticked,
        date,
        amount: amount.trim() === "" ? undefined : Number(amount),
        ...payFields(method, reference),
        note,
      },
      {
        onSuccess: (p) => {
          toast(`Payment recorded (${p.fpNo}) for ${p.bills ?? "the bills ticked"}`, "ok");
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
      title="Pay factory – Bill to Bill"
      onSubmit={submit}
      submitLabel="Save payment"
      submitting={pay.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req full">
          <label htmlFor="fp-factory">Factory</label>
          <select
            id="fp-factory"
            value={factoryId}
            onChange={(e) => {
              setFactoryId(e.target.value);
              setTicked([]);
              setAmount("");
            }}
          >
            {factories.map((f) => (
              <option key={f.id} value={f.id}>
                {f.name} – owed {formatMoney(f.owed)}
              </option>
            ))}
          </select>
        </div>
        <div className="field req full">
          <span className="label">Bills to pay</span>
          {bills.isPending ? (
            <p className="muted small">Loading bills…</p>
          ) : open.length === 0 ? (
            <p className="muted small" data-testid="fp-no-bills">
              No unpaid bills for this factory.
            </p>
          ) : (
            <div className="bill-pick" data-testid="fp-bills">
              <table>
                <thead>
                  <tr>
                    <th style={{ width: 36 }}>
                      <input
                        type="checkbox"
                        aria-label="Tick all bills"
                        checked={all}
                        onChange={(e) => tick(e.target.checked ? open.map((b) => b.id) : [])}
                      />
                    </th>
                    <th>Bill</th>
                    <th>Received</th>
                    <th>Bottles filled</th>
                    <th className="num">Cost</th>
                    <th className="num">Paid</th>
                    <th className="num">Due</th>
                  </tr>
                </thead>
                <tbody>
                  {open.map((b) => {
                    const on = ticked.includes(b.id);
                    return (
                      <tr key={b.id} className={on ? "picked" : undefined}>
                        <td>
                          <input
                            type="checkbox"
                            aria-label={`Pay ${b.billNo}`}
                            checked={on}
                            onChange={(e) =>
                              tick(e.target.checked ? [...ticked, b.id] : ticked.filter((x) => x !== b.id))
                            }
                          />
                        </td>
                        <td>
                          <b>{b.billNo}</b>
                          <div className="small muted">{b.batchNo}</div>
                        </td>
                        <td>{formatDate(b.date)}</td>
                        <td>
                          {b.filled} × {b.bottleLabel}
                        </td>
                        <td className="num">{formatMoney(b.cost)}</td>
                        <td className="num">{formatMoney(b.paid)}</td>
                        <td className="num">
                          <b>{formatMoney(b.due)}</b>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          )}
          {ticked.length > 0 && (
            <div className="small" data-testid="fp-ticked">
              {ticked.length} bill(s) ticked – due <b>{formatMoney(tickedDue)}</b>
            </div>
          )}
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
        <PaidByFields
          id="fp"
          method={method}
          reference={reference}
          onMethod={setMethod}
          onReference={setReference}
          referenceOptional={method === "BILL_TO_BILL"}
          referenceHint={method === "BILL_TO_BILL" ? "Left empty: the bills ticked" : undefined}
        />
        <div className="field full">
          <label htmlFor="fp-note">Note</label>
          <input id="fp-note" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </div>
      <p className="small muted">
        Each payment pays the bills ticked. Less than the amount due is a part payment of those bills, oldest
        first.
      </p>
    </FormDialog>
  );
}
