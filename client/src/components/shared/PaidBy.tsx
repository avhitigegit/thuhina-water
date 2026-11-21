"use client";

/*
 * "Paid by" – the one way every payment and bill records how money was paid (client 21/11/2026): Cash, Card, Online
 * transfer, Cheque or Bill to Bill. A cheque needs its number, an online transfer its reference and Bill to Bill the
 * number of the earlier bill it pays; a card slip number is optional. Used by Receive payment, money paid on any bill,
 * supplier, factory and expense payments. The rule and its wording match the server (PayMethod.referenceProblem).
 * BANK_TRANSFER keeps its stored name and reads "Online transfer".
 */

export type PayMethod = "CASH" | "CARD" | "BANK_TRANSFER" | "CHEQUE" | "BILL_TO_BILL";

export const PAY_METHODS: { value: PayMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "CARD", label: "Card" },
  { value: "BANK_TRANSFER", label: "Online transfer" },
  { value: "CHEQUE", label: "Cheque" },
  { value: "BILL_TO_BILL", label: "Bill to Bill" },
];

const REFERENCE: Record<PayMethod, { label: string; missing: string | null }> = {
  CASH: { label: "", missing: null },
  CARD: { label: "Card slip number", missing: null },
  BANK_TRANSFER: { label: "Transfer reference", missing: "Enter the transfer reference." },
  CHEQUE: { label: "Cheque number", missing: "Enter the cheque number." },
  BILL_TO_BILL: { label: "Bill number it pays", missing: "Enter the number of the bill it pays." },
};

export function payMethodLabel(method: string | null | undefined): string {
  return PAY_METHODS.find((m) => m.value === method)?.label ?? method ?? "";
}

/** "Cheque number" / "Transfer reference" / "Card slip number" / "Bill number it pays" / "" for cash. */
export function referenceLabel(method: PayMethod): string {
  return REFERENCE[method]?.label ?? "";
}

/** Whether the reference box is shown (everything but cash). */
export function hasReference(method: PayMethod): boolean {
  return method !== "CASH";
}

/** Whether the reference must be filled in (online transfer, cheque, Bill to Bill). */
export function needsReference(method: PayMethod): boolean {
  return REFERENCE[method]?.missing != null;
}

/** The message when the reference is missing (same wording as the server), or null. */
export function referenceProblem(method: PayMethod, reference: string): string | null {
  const missing = REFERENCE[method]?.missing ?? null;
  if (!missing || reference.trim()) return null;
  return missing;
}

/** What to send: no reference for cash. */
export function payFields(method: PayMethod, reference: string) {
  return { method, reference: method === "CASH" ? undefined : reference.trim() || undefined };
}

/**
 * The "Paid by" drop-down and, for anything but cash, the reference box – two `.field`s that sit in the parent's form
 * grid. {@code id} prefixes the inputs' ids ("<id>-method", "<id>-ref"). {@code methods} limits the choice (default: all);
 * {@code referenceHint} replaces the reference placeholder (e.g. "Left empty: the bills ticked").
 */
export function PaidByFields({
  id,
  method,
  reference,
  onMethod,
  onReference,
  error,
  referenceHint,
  referenceOptional,
}: {
  id: string;
  method: PayMethod;
  reference: string;
  onMethod: (m: PayMethod) => void;
  onReference: (r: string) => void;
  error?: string;
  referenceHint?: string;
  /** The reference may be left empty (e.g. Bill to Bill on Pay factory: the bills ticked are the reference). */
  referenceOptional?: boolean;
}) {
  const required = needsReference(method) && !referenceOptional;
  return (
    <>
      <div className="field req">
        <label htmlFor={`${id}-method`}>Paid by</label>
        <select id={`${id}-method`} value={method} onChange={(e) => onMethod(e.target.value as PayMethod)}>
          {PAY_METHODS.map((m) => (
            <option key={m.value} value={m.value}>
              {m.label}
            </option>
          ))}
        </select>
      </div>
      {hasReference(method) && (
        <div className={required ? "field req" : "field"}>
          <label htmlFor={`${id}-ref`}>{referenceLabel(method)}</label>
          <input
            id={`${id}-ref`}
            className={error ? "invalid" : undefined}
            value={reference}
            maxLength={100}
            placeholder={referenceHint ?? (required ? undefined : "Optional")}
            onChange={(e) => onReference(e.target.value)}
          />
          {error && <span className="error">{error}</span>}
        </div>
      )}
    </>
  );
}
