"use client";

/*
 * Billing pop-ups (prototype billing/billing.html):
 *   Receive payment (FR-50) – customer, date, amount (default = balance), Paid by with the bank reference / cheque number
 *     (and the cheque's bank and date), note; beside it balance now, paying and balance after, with "Part payment" or the
 *     advance. It clears the oldest amounts first – there is no invoice to choose. The server checks everything again.
 *   Payment saved – the server's message and Print receipt.
 *   Statement (FR-52) – from (start of last month) / to (today) → the statement PDF.
 */
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { PaidByFields, payFields, referenceProblem, type PayMethod } from "@/components/shared/PaidBy";
import { CustomerPicker } from "@/features/customers/CustomerPicker";
import type { CustomerLookupRow, PaymentSaved } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { openPdf, receiptUrl, statementUrl, useAccount, useReceivePayment } from "./api";

/** The message when a payment cannot be saved yet (same wording as the server), or null. */
export function paymentProblem(p: {
  customer: boolean;
  date: string | null;
  amount: number;
  method: PayMethod;
  reference: string;
}): string | null {
  if (!p.customer) return "Select a customer.";
  if (!p.date) return "Enter the date.";
  if (!(p.amount > 0)) return "Enter the amount received.";
  return referenceProblem(p.method, p.reference);
}

export function ReceivePaymentDialog({
  initialCustomer,
  today,
  onClose,
  onSaved,
}: {
  initialCustomer: CustomerLookupRow | null;
  today: string;
  onClose: () => void;
  onSaved: (saved: PaymentSaved) => void;
}) {
  const receive = useReceivePayment();
  const [customer, setCustomer] = useState<CustomerLookupRow | null>(initialCustomer);
  const account = useAccount(customer?.id ?? null);
  const [date, setDate] = useState<string | null>(today);
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<PayMethod>("CASH");
  const [reference, setReference] = useState("");
  const [bank, setBank] = useState("");
  const [chequeDate, setChequeDate] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [error, setError] = useState("");

  // A newly chosen customer's account fills in the amount (their balance).
  const loaded = customer && account.data?.account.customerId === customer.id ? account.data : null;
  const [filledFor, setFilledFor] = useState<number | null>(null);
  if (loaded && filledFor !== loaded.account.customerId) {
    setFilledFor(loaded.account.customerId);
    const bal = Number(loaded.account.balance);
    setAmount(bal > 0 ? bal.toFixed(2) : "");
  }

  const balance = Number(loaded?.account.balance ?? 0);
  const paying = Number(amount) || 0;
  const after = balance - paying;

  function submit() {
    const problem = paymentProblem({ customer: !!customer, date, amount: paying, method, reference });
    if (problem) {
      setError(problem);
      return;
    }
    setError("");
    receive.mutate(
      {
        customerId: customer!.id,
        date: date!,
        amount: paying,
        ...payFields(method, reference),
        bank: method === "CHEQUE" && bank.trim() ? bank.trim() : undefined,
        chequeDate: method === "CHEQUE" && chequeDate ? chequeDate : undefined,
        note: note.trim() || undefined,
      },
      { onSuccess: onSaved, onError: (err) => setError(errorText(err)) },
    );
  }

  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title="Receive payment"
      onSubmit={submit}
      submitLabel="Save payment"
      submitting={receive.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div style={{ display: "grid", gap: 16, gridTemplateColumns: "minmax(0,3fr) minmax(240px,2fr)" }}>
        <div className="form-grid">
          <div className="field req span2">
            <label htmlFor="pay-cust">Customer</label>
            <CustomerPicker
              id="pay-cust"
              value={customer}
              onChange={setCustomer}
              autoFocus={!initialCustomer}
            />
          </div>
          <div className="field req">
            <label htmlFor="pay-date">Date</label>
            <DateField id="pay-date" value={date} onChange={setDate} today={today} />
          </div>
          <div className="field req">
            <label htmlFor="pay-amount">Amount (Rs.)</label>
            <input
              id="pay-amount"
              type="number"
              min={0}
              step="0.01"
              value={amount}
              autoFocus={!!initialCustomer}
              onChange={(e) => setAmount(e.target.value)}
            />
          </div>
          <PaidByFields
            id="pay"
            method={method}
            reference={reference}
            onMethod={setMethod}
            onReference={setReference}
          />
          {method === "CHEQUE" && (
            <>
              <div className="field">
                <label htmlFor="pay-bank">Bank</label>
                <input
                  id="pay-bank"
                  placeholder="e.g. People's Bank"
                  value={bank}
                  onChange={(e) => setBank(e.target.value)}
                />
              </div>
              <div className="field">
                <label htmlFor="pay-cdate">Cheque date</label>
                <DateField id="pay-cdate" value={chequeDate} onChange={setChequeDate} today={today} />
              </div>
            </>
          )}
          <div className="field full">
            <label htmlFor="pay-note">Note</label>
            <input id="pay-note" value={note} onChange={(e) => setNote(e.target.value)} />
          </div>
        </div>
        <div>
          {loaded && (
            <>
              <div className="summary">
                <span>Balance now</span>
                <b className="num" data-testid="pay-balance">
                  {formatMoney(balance)}
                </b>
                <span>Paying</span>
                <b className="num">{formatMoney(paying)}</b>
                <span className="total">Balance after</span>
                <b className="num total" data-testid="pay-after">
                  {formatMoney(after)}
                </b>
              </div>
              {paying > 0 && paying < balance && (
                <div className="banner warn" style={{ marginTop: 8 }}>
                  Part payment
                </div>
              )}
              {paying > 0 && after < 0 && (
                <div className="banner info" style={{ marginTop: 8 }}>
                  Paid in advance – {formatMoney(-after)} credit balance.
                </div>
              )}
              <p className="small muted">Payments clear the oldest amounts first.</p>
            </>
          )}
        </div>
      </div>
    </FormDialog>
  );
}

export function PaymentSavedDialog({ saved, onClose }: { saved: PaymentSaved; onClose: () => void }) {
  const p = saved.payment;
  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Payment saved"
      extraButtons={
        <button type="button" className="btn primary" onClick={() => openPdf(receiptUrl(p.id))}>
          Print receipt
        </button>
      }
    >
      <p>{saved.message}</p>
      <p className="small muted">
        Receipt <span className="mono">{p.receiptNo}</span>
        {saved.partPayment && " · Part payment"}
      </p>
    </FormDialog>
  );
}

/** "2026-10-14" → "2026-09-01" (statement default "from"). */
export function startOfLastMonth(today: string): string {
  const [y, m] = today.split("-").map(Number);
  const py = m === 1 ? y - 1 : y;
  const pm = m === 1 ? 12 : m - 1;
  return `${py}-${String(pm).padStart(2, "0")}-01`;
}

export function StatementDialog({
  customer,
  today,
  onClose,
}: {
  customer: { id: number; name: string };
  today: string;
  onClose: () => void;
}) {
  const [from, setFrom] = useState<string | null>(startOfLastMonth(today));
  const [to, setTo] = useState<string | null>(today);
  const [error, setError] = useState("");

  function submit() {
    if (!from || !to) {
      setError("Enter both dates.");
      return;
    }
    if (from > to) {
      setError("The From date must be on or before the To date.");
      return;
    }
    openPdf(statementUrl(customer.id, from, to));
    onClose();
  }

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Statement – ${customer.name}`}
      onSubmit={submit}
      submitLabel="Print / PDF"
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field">
          <label htmlFor="st-from">From</label>
          <DateField id="st-from" value={from} onChange={setFrom} today={today} />
        </div>
        <div className="field">
          <label htmlFor="st-to">To</label>
          <DateField id="st-to" value={to} onChange={setTo} today={today} />
        </div>
      </div>
      <p className="small muted">
        Brought forward, every bill and payment with the running balance, and the age of the balance.
      </p>
    </FormDialog>
  );
}
