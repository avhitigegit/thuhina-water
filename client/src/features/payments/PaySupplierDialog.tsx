"use client";

/*
 * Pay supplier pop-up (FR-21), opened from the Payments page: supplier, date, amount (default = owed), Paid by with the
 * cheque number / bank reference, note. It clears the oldest receipt first – no purchase order to choose.
 */
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { PaidByFields, payFields, type PayMethod } from "@/components/shared/PaidBy";
import { useSuppliers } from "@/features/partners/api";
import { useRecordPayment, useSupplierBalances } from "@/features/purchasing/api";
import { errorText } from "@/features/purchasing/labels";
import type { SupplierBalance } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { toast } from "@/lib/toast";

/** Loads what is owed to each supplier, then shows the pop-up ({@code supplierId} null = the first one owed). */
export function PaySupplierDialog({
  supplierId,
  today,
  onClose,
}: {
  supplierId: number | null;
  today: string;
  onClose: () => void;
}) {
  const balances = useSupplierBalances();
  if (!balances.data) return null;
  return <PaymentDialog supplierId={supplierId} balances={balances.data} today={today} onClose={onClose} />;
}

function PaymentDialog({
  supplierId,
  balances,
  today,
  onClose,
}: {
  supplierId: number | null;
  balances: SupplierBalance[];
  today: string;
  onClose: () => void;
}) {
  const suppliers = useSuppliers();
  const pay = useRecordPayment();
  const owedOf = (id: number) => Number(balances.find((b) => b.supplierId === id)?.owed ?? 0);
  const firstOwed = balances.find((b) => Number(b.owed) > 0)?.supplierId;
  const start = supplierId ?? firstOwed ?? null;

  const [supplier, setSupplier] = useState<string>(start === null ? "" : String(start));
  const [date, setDate] = useState<string | null>(today);
  const [amount, setAmount] = useState(start === null ? "" : String(Math.max(0, owedOf(start))));
  const [method, setMethod] = useState<PayMethod>("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const list = (suppliers.data ?? []).filter((s) => s.active || owedOf(s.id) !== 0);
  const sid = supplier || (list[0] ? String(list[0].id) : "");

  function submit() {
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    pay.mutate(
      {
        supplierId: sid ? Number(sid) : undefined,
        date,
        amount: amount.trim() === "" ? undefined : Number(amount),
        ...payFields(method, reference),
        note,
      },
      {
        onSuccess: (p) => {
          toast(`Payment recorded (${p.spNo})`, "ok");
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
      title="Pay supplier"
      onSubmit={submit}
      submitLabel="Save payment"
      submitting={pay.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req full">
          <label htmlFor="sp-sup">Supplier</label>
          <select
            id="sp-sup"
            value={sid}
            onChange={(e) => {
              setSupplier(e.target.value);
              setAmount(String(Math.max(0, owedOf(Number(e.target.value)))));
            }}
          >
            {list.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} – owed {formatMoney(owedOf(s.id))}
              </option>
            ))}
          </select>
        </div>
        <div className="field req">
          <label htmlFor="sp-date">Date</label>
          <DateField id="sp-date" value={date} onChange={setDate} today={today} />
        </div>
        <div className="field req">
          <label htmlFor="sp-amount">Amount (Rs.)</label>
          <input
            id="sp-amount"
            type="number"
            min={0}
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </div>
        <PaidByFields
          id="sp"
          method={method}
          reference={reference}
          onMethod={setMethod}
          onReference={setReference}
        />
        <div className="field">
          <label htmlFor="sp-note">Note</label>
          <input id="sp-note" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </div>
    </FormDialog>
  );
}
