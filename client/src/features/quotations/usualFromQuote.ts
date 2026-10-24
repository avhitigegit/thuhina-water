/*
 * Usual quantity per delivery for a customer registered from a quotation (N-09): the quoted water bottles per bottle type.
 * A quantity "per month" is shared over the 4 weekly deliveries of the new customer's default cycle (rounded up); any
 * other unit is taken as the quantity per delivery. The Admin can change it on the form.
 */
import type { CqLineView } from "@/lib/api/types";

export function usualFromQuote(lines: CqLineView[]): Record<string, number> {
  const out: Record<string, number> = {};
  for (const l of lines) {
    if (l.kind !== "WATER" || !l.bottleTypeCode || !(l.qty > 0)) continue;
    const perDelivery = /month/i.test(l.unit ?? "") ? Math.ceil(l.qty / 4) : l.qty;
    out[l.bottleTypeCode] = (out[l.bottleTypeCode] ?? 0) + perDelivery;
  }
  return out;
}
