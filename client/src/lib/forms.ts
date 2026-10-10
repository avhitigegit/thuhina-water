/*
 * Form helpers shared by the pop-ups (React Hook Form + Zod).
 *   showServerErrors(err, ["name", "price"], setError) puts a 400 VALIDATION field message under its field and
 *   returns the text to show in the pop-up's banner when the error is not about one of those fields.
 */
import { z } from "zod";
import { ApiError } from "@/lib/api/client";

export function showServerErrors<F extends string>(
  err: unknown,
  fields: readonly F[],
  setError: (field: F, error: { message: string }) => void,
): string | null {
  if (err instanceof ApiError) {
    const messages = err.fieldErrors;
    let shown = false;
    for (const f of fields) {
      if (messages[f]) {
        setError(f, { message: messages[f] });
        shown = true;
      }
    }
    return shown ? null : err.message;
  }
  return "Something went wrong. Please try again.";
}

/** Number typed in a text/number field: "" → null, otherwise the number (NaN when not a number). */
export function toNumber(v: string): number | null {
  const t = v.trim();
  return t === "" ? null : Number(t);
}

/** A required amount > 0 with at most 2 decimals, typed as text. */
export function moneyText(message: string) {
  return z
    .string()
    .trim()
    .refine((v) => v !== "" && Number(v) > 0, message)
    .refine((v) => v === "" || /^\d+(\.\d{1,2})?$/.test(v), "Use rupees and cents, e.g. 1350 or 1350.50.");
}

/** An optional amount ≥ 0 with at most 2 decimals, typed as text. */
export function optionalMoneyText(negativeMessage: string) {
  return z
    .string()
    .trim()
    .refine((v) => v === "" || Number(v) >= 0, negativeMessage)
    .refine((v) => v === "" || /^\d+(\.\d{1,2})?$/.test(v), "Use rupees and cents, e.g. 1350 or 1350.50.");
}
