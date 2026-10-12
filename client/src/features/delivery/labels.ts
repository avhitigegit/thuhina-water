/* Words and small formats of the delivery pages (prototype delivery/delivery-schedule.html, delivery-planning.html). */
import type { BottleQty, DueBottle } from "@/lib/api/types";

/** "2×20L 1×10L" (prototype qty()); empty text when nothing. */
export function qtyText(list: readonly BottleQty[]): string {
  return list
    .filter((b) => b.qty > 0)
    .map((b) => `${b.qty}×${b.label}`)
    .join(" ");
}

/** Usual quantities or bottles held of a due customer as "2×20L". */
export function bottlesText(bottles: readonly DueBottle[], what: "usual" | "held"): string {
  return qtyText(bottles.map((b) => ({ bottleTypeCode: b.bottleTypeCode, label: b.label, qty: b[what] })));
}

export function sum<T>(rows: readonly T[], f: (row: T) => number): number {
  return rows.reduce((s, r) => s + f(r), 0);
}

/** "Mon" + day of month for the chart labels: "Mo 12". */
export function chartLabel(dayName: string, iso: string): string {
  return `${dayName.slice(0, 2)} ${iso.slice(8)}`;
}

/** "weekly" / "every 2 wks" (prototype planning row). */
export function cycleText(weeks: number): string {
  return weeks === 1 ? "weekly" : `every ${weeks} wks`;
}
