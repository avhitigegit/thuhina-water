"use client";

/*
 * "Paid by" – the one way every payment and bill records how money was paid: Cash, Bank transfer or Cheque, with the
 * cheque number / bank reference (required for those two). Used by Receive payment, money paid on any bill, supplier,
 * factory and expense payments. The rule and its wording match the server (PayMethod.referenceProblem).
 */

export type PayMethod = "CASH" | "BANK_TRANSFER" | "CHEQUE";

export const PAY_METHODS: { value: PayMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "BANK_TRANSFER", label: "Bank transfer" },
  { value: "CHEQUE", label: "Cheque" },
];

export function payMethodLabel(method: string | null | undefined): string {
  return PAY_METHODS.find((m) => m.value === method)?.label ?? method ?? "";
}

/** "Cheque number" / "Bank reference" / "" for cash. */
export function referenceLabel(method: PayMethod): string {
  return method === "CHEQUE" ? "Cheque number" : method === "BANK_TRANSFER" ? "Bank reference" : "";
}

/** The message when the reference is missing (same wording as the server), or null. */
export function referenceProblem(method: PayMethod, reference: string): string | null {
  if (method === "CASH" || reference.trim()) return null;
  return method === "CHEQUE" ? "Enter the cheque number." : "Enter the bank reference.";
}

/** What to send: the reference only for a cheque or bank transfer. */
export function payFields(method: PayMethod, reference: string) {
  return { method, reference: method === "CASH" ? undefined : reference.trim() || undefined };
}

/**
 * The "Paid by" drop-down and, for a cheque or bank transfer, the reference box – two `.field`s that sit in the
 * parent's form grid. {@code id} prefixes the inputs' ids ("<id>-method", "<id>-ref").
 */
export function PaidByFields({
  id,
  method,
  reference,
  onMethod,
  onReference,
  error,
}: {
  id: string;
  method: PayMethod;
  reference: string;
  onMethod: (m: PayMethod) => void;
  onReference: (r: string) => void;
  error?: string;
}) {
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
      {method !== "CASH" && (
        <div className="field req">
          <label htmlFor={`${id}-ref`}>{referenceLabel(method)}</label>
          <input
            id={`${id}-ref`}
            className={error ? "invalid" : undefined}
            value={reference}
            maxLength={100}
            onChange={(e) => onReference(e.target.value)}
          />
          {error && <span className="error">{error}</span>}
        </div>
      )}
    </>
  );
}
