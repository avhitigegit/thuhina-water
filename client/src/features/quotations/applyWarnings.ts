/*
 * What "Apply prices" cancels besides the running price (N-14): the same rules as the server's CustomerQuotationService
 * .applyPrices and CustomerPriceService.set / end. A quoted water price from a date cancels the agreements starting on or
 * after it and replaces the running one, so a planned price or a planned "back to standard" date is lost. A line at the
 * price already running (with nothing planned after) is skipped by the server and changes nothing.
 */
import type { CqLineView, CustomerPriceRow } from "@/lib/api/types";
import { addDaysIso, formatDate, formatMoney } from "@/lib/format";

export function applyWarnings(lines: CqLineView[], prices: CustomerPriceRow[], from: string): string[] {
  const out: string[] = [];
  const water = lines.filter((l) => l.kind === "WATER" && l.bottleTypeCode && Number(l.unitPrice) > 0);
  for (const code of new Set(water.map((l) => l.bottleTypeCode!))) {
    const quoted = Number(water.find((l) => l.bottleTypeCode === code)!.unitPrice);
    const rows = prices.filter((p) => p.bottleTypeCode === code && p.state !== "CANCELLED");
    const later = rows.filter((p) => p.validFrom > from);
    const running = rows.find((p) => p.validFrom <= from && (p.validUntil == null || p.validUntil >= from));
    if (running && Number(running.price) === quoted && later.length === 0) continue;
    const backOn = running?.validUntil ? addDaysIso(running.validUntil, 1) : null;
    // Back to the standard price after the running one ends (not when another agreed price starts that day).
    if (running && backOn && running.validFrom < from && !later.some((p) => p.validFrom === backOn)) {
      out.push(
        `${running.label}: the plan to go back to the standard price from ${formatDate(backOn)} will be removed.`,
      );
    }
    for (const p of rows.filter((r) => r.validFrom >= from)) {
      out.push(
        `${p.label}: the planned price ${formatMoney(p.price)} from ${formatDate(p.validFrom)} will be cancelled.`,
      );
    }
  }
  return out;
}
