/*
 * Display formats (NFR-07, design 14): dates DD/MM/YYYY, money "Rs. 1,350.00".
 * The API always uses ISO dates (2026-10-05); only the screen shows DD/MM/YYYY.
 */

/** Business time zone – "today" is the date in Sri Lanka, whatever the computer's own zone. */
export const BUSINESS_TIME_ZONE = "Asia/Colombo";

export const MONTHS = [
  "January",
  "February",
  "March",
  "April",
  "May",
  "June",
  "July",
  "August",
  "September",
  "October",
  "November",
  "December",
];
export const DAY_NAMES = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

const pad = (n: number) => (n < 10 ? "0" : "") + n;

/** Today's business date as ISO (YYYY-MM-DD) in Asia/Colombo. */
export function todayIso(now: Date = new Date()): string {
  // en-CA formats as YYYY-MM-DD.
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

/** ISO date plus (or minus) whole days: addDaysIso("2026-10-05", -7) → "2026-09-28". */
export function addDaysIso(iso: string, days: number): string {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d + days)).toISOString().slice(0, 10);
}

/** "2026-10-05" (or a timestamp starting with it) → "05/10/2026"; empty for no value. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "";
  const [y, m, d] = String(iso).slice(0, 10).split("-");
  return `${d}/${m}/${y}`;
}

/** Timestamp → "05/10/2026 14:32" in the business time zone. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return "";
  const parts = new Intl.DateTimeFormat("en-GB", {
    timeZone: BUSINESS_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23",
  }).formatToParts(new Date(value));
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? "";
  return `${get("day")}/${get("month")}/${get("year")} ${get("hour")}:${get("minute")}`;
}

/** True when the ISO date exists in the calendar (rejects 31/02/2026). */
export function isValidIso(iso: string): boolean {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!m) return false;
  const y = +m[1],
    mo = +m[2],
    d = +m[3];
  if (mo < 1 || mo > 12 || d < 1) return false;
  return d <= daysInMonth(y, mo);
}

/** "5/10/2026" or "05/10/2026" → "2026-10-05"; null when it is not a real date. */
export function parseDmy(text: string | null | undefined): string | null {
  const m = /^\s*(\d{1,2})\/(\d{1,2})\/(\d{4})\s*$/.exec(text ?? "");
  if (!m) return null;
  const iso = `${m[3]}-${pad(+m[2])}-${pad(+m[1])}`;
  return isValidIso(iso) ? iso : null;
}

/** Inserts the slashes while a date is typed: "05102026" → "05/10/2026". */
export function autoSlashDate(text: string): string {
  const v = text.replace(/[^\d]/g, "").slice(0, 8);
  if (v.length >= 5) return `${v.slice(0, 2)}/${v.slice(2, 4)}/${v.slice(4)}`;
  if (v.length >= 3) return `${v.slice(0, 2)}/${v.slice(2)}`;
  return v;
}

export function daysInMonth(year: number, month: number): number {
  return new Date(Date.UTC(year, month, 0)).getUTCDate();
}

/** Day of week for an ISO date: 0 = Sunday … 6 = Saturday (independent of the computer's time zone). */
export function dayOfWeek(iso: string): number {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(Date.UTC(y, m - 1, d)).getUTCDay();
}

/** "Fri 09/10/2026" – used in the top bar. */
export function formatDayDate(iso: string): string {
  return `${DAY_NAMES[dayOfWeek(iso)].slice(0, 3)} ${formatDate(iso)}`;
}

const moneyFormat = new Intl.NumberFormat("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** 1350 → "Rs. 1,350.00"; negative → "-Rs. 1,350.00". Accepts numbers or API decimal strings. */
export function formatMoney(value: number | string | null | undefined): string {
  const n = Number(value ?? 0);
  if (!Number.isFinite(n)) return "";
  const text = "Rs. " + moneyFormat.format(Math.abs(n));
  return n < 0 && Math.abs(n) >= 0.005 ? "-" + text : text;
}

/** 12500 → "12,500" */
export function formatNumber(value: number | string | null | undefined): string {
  return Number(value ?? 0).toLocaleString("en-US");
}
