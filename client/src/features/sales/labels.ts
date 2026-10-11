/* Words of the Sales & Bills page (prototype sales/sales-bills.html, store.js KINDS, ui.js kindBadge). */
import type { BadgeKind } from "@/components/shared/StatusBadge";
import type { PayMethod, SaleLine } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";

/** Kinds of bill (server SalesKind) with their badge colour, in the prototype's order. */
export const KINDS: { value: string; label: string; badge: BadgeKind }[] = [
  { value: "FIRST_PURCHASE", label: "First purchase", badge: "info" },
  { value: "EXCHANGE", label: "Exchange", badge: "" },
  { value: "LEFT_AT_DOOR", label: "Left at door", badge: "warn" },
  { value: "COLLECT_EMPTIES", label: "Empties collected", badge: "ok" },
  { value: "EXTRA_ORDER", label: "Extra order", badge: "info" },
  { value: "PRODUCT_SALE", label: "Product sale", badge: "" },
  { value: "CUSTOMER_DAMAGE", label: "Customer damage", badge: "bad" },
  { value: "OWED_SETTLEMENT", label: "Bottles owed settled", badge: "ok" },
  { value: "OPENING_BALANCE", label: "Opening balance", badge: "dark" },
  { value: "REVERSAL", label: "Reversal", badge: "bad" },
];

export function kindBadge(kind: string): BadgeKind {
  return KINDS.find((k) => k.value === kind)?.badge ?? "";
}

/** The entries of "+ New ▾" – also the `?new=` values of the page (customer panel links use them). */
export type SaleKind = "first" | "extra" | "product" | "collect" | "damage" | "owed";

export const NEW_ENTRIES: { kind: SaleKind; label: string; hint: string }[] = [
  { kind: "first", label: "First purchase", hint: "New customer: deposit + water" },
  { kind: "extra", label: "Extra order", hint: "More bottles on any day" },
  { kind: "product", label: "Product sale", hint: "Dispensers, stands, pumps" },
  { kind: "collect", label: "Collect empties", hint: "Left at the door last trip" },
  { kind: "damage", label: "Customer-damaged bottle", hint: "Customer pays for a new bottle" },
  { kind: "owed", label: "Settle bottles owed", hint: "Bottles returned or deposit paid" },
];

export function isSaleKind(v: string | null): v is SaleKind {
  return NEW_ENTRIES.some((e) => e.kind === v);
}

export const PAY_METHODS: { value: PayMethod; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "BANK_TRANSFER", label: "Bank transfer" },
  { value: "CHEQUE", label: "Cheque" },
];

export function payMethodLabel(v: string | null | undefined): string {
  return PAY_METHODS.find((m) => m.value === v)?.label ?? v ?? "";
}

/**
 * One bill line in words (bill details, prototype showBill):
 * "20L: 4 filled @ Rs. 330.00 (agreed price) 3 empties back 1 owed" or "1 × Water dispenser @ Rs. 8,500.00".
 */
export function lineText(l: SaleLine): string {
  if (l.productCode) return `${l.qty} × ${l.label} @ ${formatMoney(l.unitPrice)}`;
  const s = [l.label + ":"];
  if (l.filled) s.push(`${l.filled} filled${l.unitPrice != null ? " @ " + formatMoney(l.unitPrice) : ""}`);
  if (l.agreedPrice) s.push("(agreed price)");
  if (l.empties) s.push(`${l.empties} empties back`);
  if (l.owedAdded) s.push(`${l.owedAdded} owed`);
  if (l.owedCleared) s.push(`${l.owedCleared} owed settled`);
  if (l.toCollectAdded) s.push(`${l.toCollectAdded} to collect`);
  if (l.toCollectCleared) s.push(`${l.toCollectCleared} collected`);
  if (l.depositCount) s.push(`${l.depositCount} deposit(s)`);
  if (l.oldBottles) s.push(`${l.oldBottles} old bottle(s) in`);
  if (l.damaged) s.push(`${l.damaged} damaged`);
  if (!l.filled && !l.empties && !l.damaged && l.qty && !l.owedCleared && !l.toCollectCleared)
    s.push(`${l.qty} bottle(s)`);
  return s.join(" ");
}
