/* Purchasing words shared by the tabs and pop-ups (prototype store.js PO_STATUS, PAY_MODES). */
import { ApiError } from "@/lib/api/client";
import type { ItemQty } from "@/lib/api/types";

export const PO_STATUSES = [
  { value: "DRAFT", label: "Draft" },
  { value: "APPROVED", label: "Approved" },
  { value: "SENT", label: "Sent" },
  { value: "PARTLY_RECEIVED", label: "Partly received" },
  { value: "RECEIVED", label: "Received" },
  { value: "CANCELLED", label: "Cancelled" },
] as const;

export const PAY_METHODS = [
  { value: "CASH", label: "Cash" },
  { value: "BANK_TRANSFER", label: "Bank transfer" },
  { value: "CHEQUE", label: "Cheque" },
] as const;

export type PayMethod = (typeof PAY_METHODS)[number]["value"];

/** PO step bar (FR-19): 1. Draft 2. Approved 3. Sent 4. Received – "Partly received" while goods are still due. */
export function poSteps(status: string): { steps: string[]; current: string } {
  const last = status === "PARTLY_RECEIVED" ? "4. Partly received" : "4. Received";
  const steps = ["1. Draft", "2. Approved", "3. Sent", last];
  const at = { DRAFT: 0, APPROVED: 1, SENT: 2, PARTLY_RECEIVED: 3, RECEIVED: 3 }[status] ?? 0;
  return { steps, current: steps[at] };
}

/** "300 × Empty 20L Bottle" */
export function itemText(i: ItemQty): string {
  return `${i.qty.toLocaleString("en-US")} × ${i.name}`;
}

/** The message to show for a failed save: field messages of a 400, else the error message. */
export function errorText(err: unknown): string {
  if (err instanceof ApiError) {
    const fields = Object.values(err.fieldErrors);
    return fields.length ? fields.join(" ") : err.message;
  }
  return "Something went wrong. Please try again.";
}
