"use client";

/*
 * Expense pop-ups (prototype expenses/expenses.html):
 *   Add / edit expense – expense no., date paid, for month ("September electricity paid on 02/10 → September"), type (active
 *     ones, plus the expense's own), description, paid to (earlier payees suggested), bill / account no., amount, paid by and
 *     the cheque no. / bank ref. (not for Cash). "Save & add another" keeps the month for fast entry. The server checks
 *     everything with the prototype's messages.
 *   Expense types – the list with this month's total, Activate / Deactivate, and a new type.
 */
import { useId, useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { StatusBadge } from "@/components/shared/StatusBadge";
import type { ExpenseRow, ExpenseSaved, ExpenseType } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { toast } from "@/lib/toast";
import { useSaveExpense, useSaveExpenseType, type ExpenseBody } from "./api";

export const PAY_METHODS = [
  { value: "CASH", label: "Cash" },
  { value: "BANK_TRANSFER", label: "Bank transfer" },
  { value: "CHEQUE", label: "Cheque" },
] as const;
type Method = ExpenseBody["method"];

interface MonthChoice {
  month: string;
  label: string;
}

export function ExpenseDialog({
  expense,
  month,
  months,
  types,
  payees,
  today,
  onClose,
  onSaved,
}: {
  /** null = add a new expense. */
  expense: ExpenseRow | null;
  /** The month shown on the page – the default "for month" of a new expense. */
  month: string;
  months: MonthChoice[];
  types: ExpenseType[];
  payees: string[];
  today: string;
  onClose: () => void;
  onSaved: (saved: ExpenseSaved) => void;
}) {
  const save = useSaveExpense();
  const listId = useId();
  const [paidDate, setPaidDate] = useState<string | null>(expense?.paidDate ?? today);
  const [forMonth, setForMonth] = useState(expense?.forMonth ?? month);
  const [typeId, setTypeId] = useState(expense ? String(expense.typeId) : "");
  const [description, setDescription] = useState(expense?.description ?? "");
  const [paidTo, setPaidTo] = useState(expense?.paidTo ?? "");
  const [billNo, setBillNo] = useState(expense?.billNo ?? "");
  const [amount, setAmount] = useState(expense ? String(expense.amount) : "");
  const [method, setMethod] = useState<Method>((expense?.method as Method) ?? "CASH");
  const [reference, setReference] = useState(expense?.reference ?? "");
  const [error, setError] = useState("");
  const choices = types.filter((t) => t.active || t.id === expense?.typeId);
  // An old expense may be for a month no longer in the list.
  const monthChoices =
    expense && !months.some((m) => m.month === expense.forMonth)
      ? [...months, { month: expense.forMonth, label: expense.forMonthLabel }]
      : months;

  function submit(again: boolean) {
    setError("");
    save.mutate(
      {
        id: expense?.id ?? null,
        body: {
          paidDate,
          forMonth,
          typeId: typeId ? Number(typeId) : null,
          description,
          paidTo,
          billNo,
          amount: amount.trim() === "" ? null : Number(amount),
          method,
          reference: method === "CASH" ? "" : reference,
          ...(expense ? { version: expense.version } : {}),
        },
      },
      {
        onSuccess: (saved) => {
          toast(saved.message, "ok");
          onSaved(saved);
          if (again) {
            // Fast entry: same month and date, the rest cleared.
            setTypeId("");
            setDescription("");
            setPaidTo("");
            setBillNo("");
            setAmount("");
            setMethod("CASH");
            setReference("");
          } else {
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
      title={expense ? `Edit expense ${expense.expenseNo}` : "Add expense"}
      onSubmit={() => submit(false)}
      submitting={save.isPending}
      extraButtons={
        !expense && (
          <button type="button" className="btn" disabled={save.isPending} onClick={() => submit(true)}>
            Save &amp; add another
          </button>
        )
      }
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="ex-no">Expense no.</label>
          <input id="ex-no" value={expense?.expenseNo ?? "Generated on save"} readOnly />
        </div>
        <div className="field req">
          <label htmlFor="ex-date">Date paid</label>
          <DateField id="ex-date" value={paidDate} onChange={setPaidDate} today={today} />
        </div>
        <div className="field req">
          <label htmlFor="ex-month">For month</label>
          <select id="ex-month" value={forMonth} onChange={(e) => setForMonth(e.target.value)}>
            {monthChoices.map((m) => (
              <option key={m.month} value={m.month}>
                {m.label}
              </option>
            ))}
          </select>
          <span className="help">e.g. September electricity paid on 02/10 → September</span>
        </div>
        <div className="field req">
          <label htmlFor="ex-type">Expense type</label>
          <select id="ex-type" value={typeId} onChange={(e) => setTypeId(e.target.value)}>
            <option value="">Select</option>
            {choices.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field span2">
          <label htmlFor="ex-desc">Description</label>
          <input
            id="ex-desc"
            placeholder="e.g. LH-4521 – 2 rear tyres"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="ex-to">Paid to</label>
          <input
            id="ex-to"
            list={listId}
            placeholder="e.g. CEB, Dinesh Auto Service"
            value={paidTo}
            onChange={(e) => setPaidTo(e.target.value)}
          />
          <datalist id={listId}>
            {payees.map((p) => (
              <option key={p} value={p} />
            ))}
          </datalist>
        </div>
        <div className="field">
          <label htmlFor="ex-bill">Bill / account no.</label>
          <input id="ex-bill" value={billNo} onChange={(e) => setBillNo(e.target.value)} />
        </div>
        <div className="field req">
          <label htmlFor="ex-amount">Amount (Rs.)</label>
          <input
            id="ex-amount"
            type="number"
            min={0}
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </div>
        <div className="field req">
          <label htmlFor="ex-method">Paid by</label>
          <select id="ex-method" value={method} onChange={(e) => setMethod(e.target.value as Method)}>
            {PAY_METHODS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
        </div>
        {method !== "CASH" && (
          <div className="field req">
            <label htmlFor="ex-ref">Cheque no. / bank ref.</label>
            <input id="ex-ref" value={reference} onChange={(e) => setReference(e.target.value)} />
          </div>
        )}
      </div>
    </FormDialog>
  );
}

export function ExpenseTypesDialog({
  types,
  monthLabel,
  monthTotals,
  onClose,
}: {
  types: ExpenseType[];
  monthLabel: string;
  /** This month's total by type id. */
  monthTotals: Map<number, number | string>;
  onClose: () => void;
}) {
  const save = useSaveExpenseType();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState("");

  function toggle(t: ExpenseType) {
    setError("");
    save.mutate(
      { id: t.id, body: { active: !t.active, version: t.version } },
      { onError: (err) => setError(errorText(err)) },
    );
  }

  function add() {
    setError("");
    save.mutate(
      { id: null, body: { name, description } },
      {
        onSuccess: () => {
          toast("Expense type added", "ok");
          setName("");
          setDescription("");
        },
        onError: (err) => setError(errorText(err)),
      },
    );
  }

  return (
    <FormDialog open wide onOpenChange={(o) => !o && onClose()} title="Expense types">
      {error && <div className="banner bad">{error}</div>}
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Description</th>
              <th className="num">{monthLabel}</th>
              <th>Status</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {types.map((t) => (
              <tr key={t.id}>
                <td>
                  <b>{t.name}</b>
                </td>
                <td>{t.description}</td>
                <td className="num">{formatMoney(monthTotals.get(t.id) ?? 0)}</td>
                <td>
                  <StatusBadge status={t.active ? "Active" : "Inactive"} />
                </td>
                <td>
                  <button
                    className="btn sm"
                    type="button"
                    disabled={save.isPending}
                    aria-label={`${t.active ? "Deactivate" : "Activate"} ${t.name}`}
                    onClick={() => toggle(t)}
                  >
                    {t.active ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="form-grid" style={{ marginTop: 12, gridTemplateColumns: "1fr 2fr auto" }}>
        <div className="field">
          <label htmlFor="et-name">New type</label>
          <input
            id="et-name"
            placeholder="e.g. Insurance"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="et-desc">Description</label>
          <input id="et-desc" value={description} onChange={(e) => setDescription(e.target.value)} />
        </div>
        <button
          className="btn"
          type="button"
          style={{ alignSelf: "end" }}
          disabled={save.isPending}
          onClick={add}
        >
          Add
        </button>
      </div>
      <p className="small muted">
        An inactive type is hidden when adding expenses; its past expenses are kept.
      </p>
    </FormDialog>
  );
}
