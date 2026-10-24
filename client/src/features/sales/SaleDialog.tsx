"use client";

/*
 * Sale pop-up framework (prototype sales-bills.html saleModal): two columns – the form (customer, bill no.
 * "Generated on save", date, the sale's own fields, cash paid) and the live calculation from the server's
 * …/preview endpoint (debounced) with the customer card "after this bill". Paid is filled with the amount for Cash
 * customers (0 for Credit / Monthly bill) until the user types in it. Saving: field messages go under their field,
 * other messages in the banner; CREDIT_LIMIT opens the credit approval pop-up (Admin: "Approve and save").
 */
import { useState, type ReactNode } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useCustomer } from "@/features/customers/api";
import { CustomerPicker } from "@/features/customers/CustomerPicker";
import { ApiError } from "@/lib/api/client";
import type { CustomerDetail, CustomerLookupRow, SalePreview, SaleResult } from "@/lib/api/types";
import { todayIso } from "@/lib/format";
import { toast } from "@/lib/toast";
import { useSalePreview, useSaveSale } from "./api";
import { CREDIT_LIMIT, CreditApprovalDialog } from "./CreditApprovalDialog";
import { CustomerCard } from "./CustomerCard";
import type { SaleKind } from "./labels";

/** What the sale's own fields get: the chosen customer and the save messages. */
export interface SaleContext {
  customer: CustomerDetail | null;
  /** The save message under a field, or nothing. */
  err: (field: string) => ReactNode;
  invalid: (field: string) => boolean;
}

export function SaleDialog({
  kind,
  title,
  okMessage,
  initialCustomer,
  customerOptional,
  afterCustomer,
  fields,
  fieldNames,
  build,
  calc,
  showPaid = true,
  afterPaid,
  onClose,
  onSaved,
}: {
  kind: SaleKind;
  title: string;
  /** Toast after saving, e.g. "First purchase saved" (the bill number is added). */
  okMessage: string;
  initialCustomer?: CustomerLookupRow | null;
  /** Product sale: a walk-in name instead of a customer. */
  customerOptional?: boolean;
  afterCustomer?: (ctx: SaleContext) => ReactNode;
  fields: (ctx: SaleContext) => ReactNode;
  /** Fields shown by `fields` / `afterCustomer` – save messages for other fields go in the banner. */
  fieldNames: string[];
  /** The sale's own part of the request, or null when the live calculation cannot run yet. */
  build: (ctx: SaleContext) => object | null;
  calc: (preview: SalePreview, ctx: SaleContext) => ReactNode;
  showPaid?: boolean;
  afterPaid?: ReactNode;
  onClose: () => void;
  onSaved?: (result: SaleResult) => void;
}) {
  const [picked, setPicked] = useState<CustomerLookupRow | null>(initialCustomer ?? null);
  const [date, setDate] = useState<string | null>(todayIso());
  const [paidText, setPaidText] = useState<string | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [banner, setBanner] = useState("");
  const [creditError, setCreditError] = useState<ApiError | null>(null);
  const detail = useCustomer(picked?.id ?? null);
  const customer = picked && detail.data?.id === picked.id ? detail.data : null;
  const save = useSaveSale(kind);

  const known = new Set(["customerId", "date", "paid", ...fieldNames]);
  const ctx: SaleContext = {
    customer,
    err: (f) => (errors[f] ? <span className="error">{errors[f]}</span> : null),
    invalid: (f) => !!errors[f],
  };

  const own = (customer || (customerOptional && !picked)) && date ? build(ctx) : null;
  const base = own ? { ...own, customerId: customer?.id, date } : null;
  // Paid follows the amount until typed in: full amount for Cash (and walk-in), 0 for Credit / Monthly bill.
  // The preview gets the paid shown (the automatic one comes from the previous calculation).
  const [autoPaid, setAutoPaid] = useState<number | undefined>(undefined);
  const paidValue = paidText ?? (autoPaid != null ? String(autoPaid) : "");
  const preview = useSalePreview(
    kind,
    base ? { ...base, paid: showPaid && paidValue !== "" ? Number(paidValue) || 0 : undefined } : null,
  );
  const p = preview.data;
  const nextAuto = p && base ? (!customer || customer.payType === "CASH" ? Number(p.amount) : 0) : undefined;
  if (nextAuto !== autoPaid) setAutoPaid(nextAuto);
  // Until the calculation with the new paid amount is back, the balance after uses the paid shown here.
  const credit =
    p?.credit && showPaid
      ? { ...p.credit, after: Number(p.credit.balance) + Number(p.amount) - (Number(paidValue) || 0) }
      : p?.credit;

  function send(approveOverLimit: boolean) {
    if (!base) {
      if (!picked && !customerOptional) setErrors({ customerId: "Select a customer." });
      return;
    }
    setErrors({});
    setBanner("");
    const body = { ...base, paid: showPaid ? Number(paidValue) || 0 : undefined, approveOverLimit };
    save.mutate(body, {
      onSuccess: (r) => {
        setCreditError(null);
        toast(`${okMessage} – bill ${r.txn.billNo}`, "ok");
        onSaved?.(r);
        onClose();
      },
      onError: (err) => {
        if (!(err instanceof ApiError)) {
          setBanner("Something went wrong. Please try again.");
          return;
        }
        if (err.code === CREDIT_LIMIT) {
          setCreditError(err);
          return;
        }
        setCreditError(null);
        const fields = { ...err.fieldErrors };
        const f = err.details.field;
        if (typeof f === "string" && !fields[f]) fields[f] = err.message;
        const shown = Object.fromEntries(Object.entries(fields).filter(([k]) => known.has(k)));
        setErrors(shown);
        if (Object.keys(shown).length === 0) setBanner(err.message);
      },
    });
  }

  return (
    <>
      <FormDialog
        open
        wide
        onOpenChange={(o) => !o && onClose()}
        title={title}
        onSubmit={() => send(false)}
        submitting={save.isPending && !creditError}
      >
        {banner && <div className="banner bad">{banner}</div>}
        <div
          style={{
            display: "grid",
            gap: 16,
            gridTemplateColumns: "minmax(0, 3fr) minmax(240px, 2fr)",
          }}
        >
          <div className="form-grid" style={{ alignContent: "start" }}>
            <div className={customerOptional ? "field span2" : "field req span2"}>
              <label htmlFor="sale-customer">Customer</label>
              <CustomerPicker
                id="sale-customer"
                value={picked}
                autoFocus={!picked}
                invalid={!!errors.customerId}
                onChange={(c) => {
                  setPicked(c);
                  setPaidText(null);
                  setErrors({});
                }}
              />
              {ctx.err("customerId")}
            </div>
            {afterCustomer?.(ctx)}
            <div className="field">
              <label htmlFor="sale-billno">Bill no.</label>
              <input id="sale-billno" value="Generated on save" readOnly />
            </div>
            <div className="field req">
              <label htmlFor="sale-date">Date</label>
              <DateField id="sale-date" value={date} onChange={setDate} invalid={!!errors.date} />
              {ctx.err("date")}
            </div>
            {fields(ctx)}
            {showPaid && (
              <div className="field">
                <label htmlFor="sale-paid">Cash paid (Rs.)</label>
                <input
                  id="sale-paid"
                  type="number"
                  min="0"
                  className={errors.paid ? "invalid num" : "num"}
                  value={paidValue}
                  onChange={(e) => setPaidText(e.target.value)}
                />
                {ctx.err("paid")}
              </div>
            )}
            {afterPaid}
          </div>
          <div>
            {!customer && !(customerOptional && !picked) ? (
              <p className="muted">Select a customer to see bottle balances.</p>
            ) : p ? (
              <>
                {p.problems.length > 0 && (
                  <div className="banner bad">
                    {p.problems.map((x) => (
                      <div key={x.message}>{x.message}</div>
                    ))}
                  </div>
                )}
                {p.warnings.map((w) => (
                  <div key={w} className="banner warn">
                    {w}
                  </div>
                ))}
                {calc(p, ctx)}
              </>
            ) : preview.isError ? (
              <div className="banner bad">{preview.error.message}</div>
            ) : null}
            {customer && (
              <div style={{ marginTop: 10 }}>
                <CustomerCard customer={customer} after={p?.after} credit={credit} />
              </div>
            )}
          </div>
        </div>
      </FormDialog>
      {creditError && (
        <CreditApprovalDialog
          error={creditError}
          busy={save.isPending}
          onApprove={() => send(true)}
          onClose={() => setCreditError(null)}
        />
      )}
    </>
  );
}

/** Calculation box (prototype `sum`): label / amount rows, the last one bold as the total. */
export function CalcRows({ rows }: { rows: [ReactNode, ReactNode, boolean?][] }) {
  return (
    <div className="summary">
      {rows.map(([label, value, total], i) => (
        <span key={i} style={{ display: "contents" }}>
          <span className={total ? "total" : undefined}>{label}</span>
          <b className={total ? "num total" : "num"}>{value}</b>
        </span>
      ))}
    </div>
  );
}
