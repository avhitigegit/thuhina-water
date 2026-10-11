/* Words of the Customers page (prototype master-data/customers.html, customer-panel.js, store.js). */
import type { PayType } from "@/lib/api/types";
import { formatDate } from "@/lib/format";

export const DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

/** 1 = Monday … 7 = Sunday (server, ISO). */
export function dayName(day: number): string {
  return DAYS[day - 1] ?? "";
}

/** ISO weekday of a date "2026-10-12" (1 = Monday). */
export function isoWeekday(iso: string): number {
  const [y, m, d] = iso.split("-").map(Number);
  const w = new Date(Date.UTC(y, m - 1, d)).getUTCDay();
  return w === 0 ? 7 : w;
}

/** The very next delivery day after `from` (BR-16: registration date → first delivery). */
export function nextDeliveryAfter(from: string, day: number): string {
  const [y, m, d] = from.split("-").map(Number);
  const date = new Date(Date.UTC(y, m - 1, d + 1));
  while ((date.getUTCDay() === 0 ? 7 : date.getUTCDay()) !== day) date.setUTCDate(date.getUTCDate() + 1);
  return date.toISOString().slice(0, 10);
}

export const CYCLES = [
  { weeks: 1, label: "Every week" },
  { weeks: 2, label: "Every 2 weeks" },
  { weeks: 3, label: "Every 3 weeks" },
  { weeks: 4, label: "Every 4 weeks" },
];

/** "Mon, weekly · next 05/10/2026" pieces. */
export function cycleShort(weeks: number): string {
  return weeks === 1 ? "weekly" : `${weeks} wks`;
}

export function deliveryText(day: number, weeks: number, next: string): string {
  return `${dayName(day)}, ${weeks === 1 ? "every week" : `every ${weeks} weeks`} · next ${formatDate(next)}`;
}

export const PAY_TYPES: { value: PayType; label: string }[] = [
  { value: "CASH", label: "Cash" },
  { value: "CREDIT", label: "Credit" },
  { value: "MONTHLY_BILL", label: "Monthly bill" },
];

export function payLabel(p: string): string {
  return PAY_TYPES.find((x) => x.value === p)?.label ?? p;
}

/** Payment terms for Credit and Monthly bill customers. */
export const CREDIT_TERMS = [7, 14, 30, 45, 60];

export const DEACT_REASONS = [
  "Stopped buying",
  "Moved away",
  "Business closed",
  "Went to another supplier",
  "Not paying",
  "Other",
];

export const STATE_BADGE: Record<string, { label: string; kind: "ok" | "info" | "" }> = {
  IN_FORCE: { label: "In force", kind: "ok" },
  SCHEDULED: { label: "Scheduled", kind: "info" },
  ENDED: { label: "Ended", kind: "" },
  REPLACED: { label: "Replaced", kind: "" },
  CANCELLED: { label: "Cancelled", kind: "" },
};
