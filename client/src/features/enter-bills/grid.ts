/*
 * Enter Bills grid model (M07 C2 – C4): one row per paper bill. Rows come from the due list ("auto"), are typed by
 * hand ("typed"), and end "saved" (locked) or with the server's error. The live result of a row comes from the
 * TypeScript copy of ExchangeRules; the server checks again on save.
 */
import type { BottleType, CustomerDetail, DueCustomer, SaleResult } from "@/lib/api/types";
import { addDaysIso, formatDate, formatMoney, parseDmy } from "@/lib/format";
import { calculate, exceedsCredit, NOTE_REQUIRED, type Bottles, type ExchangeCalc } from "./exchangeRules";

export type BillKind = "EXCHANGE" | "LEFT_AT_DOOR";

/** A bottle type column of the grid: "20L". */
export interface BottleCol {
  code: string;
  label: string;
  name: string;
}

/** What the grid needs to know about a row's customer (from the due list or the customer details). */
export interface GridCustomer {
  id: number;
  code: string;
  name: string;
  phone: string;
  typeName: string;
  payType: string;
  payTypeLabel: string;
  creditLimit: number;
  balance: number;
  cycleWeeks: number;
  nextDeliveryDate: string;
  bottles: Record<string, Bottles>;
  usual: Record<string, number>;
  prices: Record<string, { price: number; agreed: boolean } | null>;
  /** Inactive customers cannot get new bills (FR-13); warned as soon as chosen (N-06). Due customers are active. */
  inactive?: boolean;
}

export interface Qty {
  filled: string;
  empties: string;
}

export interface RowError {
  message: string;
  code: string;
  /** The Admin may resend with approveOverLimit (CREDIT_LIMIT). */
  creditLimit: boolean;
}

export interface Row {
  key: number;
  /** auto = filled in from the due list (replaced when the date changes); typed = the user typed in it. */
  source: "auto" | "typed";
  paper: string;
  /** The paper number was suggested (not typed): if it turns out to be entered already, the next free one is taken. */
  paperSuggested: boolean;
  /** Row date as typed (DD/MM/YYYY). */
  date: string;
  /** Text in the customer cell, and the customer it means (null = none / not found). */
  customerText: string;
  customer: GridCustomer | null;
  kind: BillKind;
  qty: Record<string, Qty>;
  /** Cash paid as typed (used once paidTouched; before that the amount is filled in – see shownPaid). */
  paid: string;
  /** The user typed the cash paid – stop filling it in. */
  paidTouched: boolean;
  phone: boolean;
  note: string;
  status: "open" | "saving" | "saved" | "failed";
  result: SaleResult | null;
  error: RowError | null;
}

let lastKey = 0;
export const nextKey = () => ++lastKey;

export function bottleCols(types: BottleType[]): BottleCol[] {
  return types
    .filter((t) => t.active)
    .sort((a, b) => b.litres - a.litres || a.code.localeCompare(b.code))
    .map((t) => ({ code: t.code, label: `${Number(t.litres)}L`, name: t.name }));
}

export function fromDue(d: DueCustomer, typeName = ""): GridCustomer {
  const bottles: Record<string, Bottles> = {};
  const usual: Record<string, number> = {};
  const prices: GridCustomer["prices"] = {};
  for (const b of d.bottles) {
    bottles[b.bottleTypeCode] = { held: b.held, toCollect: b.toCollect, owed: b.owed };
    usual[b.bottleTypeCode] = b.usual;
    prices[b.bottleTypeCode] = b.price == null ? null : { price: Number(b.price), agreed: b.agreedPrice };
  }
  return {
    id: d.id,
    code: d.code,
    name: d.name,
    phone: d.phone,
    typeName,
    payType: d.payType,
    payTypeLabel: d.payTypeLabel,
    creditLimit: Number(d.creditLimit),
    balance: Number(d.balance),
    cycleWeeks: d.cycleWeeks,
    nextDeliveryDate: d.nextDeliveryDate,
    bottles,
    usual,
    prices,
  };
}

const PAY_LABELS: Record<string, string> = { CASH: "Cash", CREDIT: "Credit", MONTHLY_BILL: "Monthly bill" };

export function fromDetail(c: CustomerDetail): GridCustomer {
  const bottles: Record<string, Bottles> = {};
  for (const b of c.bottles)
    bottles[b.bottleTypeCode] = { held: b.held, toCollect: b.toCollect, owed: b.owed };
  const prices: GridCustomer["prices"] = {};
  for (const p of c.prices)
    prices[p.bottleTypeCode] = p.price == null ? null : { price: Number(p.price), agreed: p.agreed };
  return {
    id: c.id,
    code: c.code,
    name: c.name,
    phone: c.phone,
    typeName: c.typeName,
    payType: c.payType,
    payTypeLabel: PAY_LABELS[c.payType] ?? c.payType,
    creditLimit: Number(c.creditLimit),
    balance: Number(c.outstanding),
    cycleWeeks: c.cycleWeeks,
    nextDeliveryDate: c.nextDeliveryDate,
    bottles,
    usual: { ...c.usualQty },
    prices,
    inactive: c.status !== "ACTIVE",
  };
}

/** Usual quantities: filled = usual; empties = usual + empties left at the door last trip (Exchange only). */
export function usualQty(c: GridCustomer, cols: BottleCol[], kind: BillKind): Record<string, Qty> {
  const out: Record<string, Qty> = {};
  for (const b of cols) {
    const u = c.usual[b.code] ?? 0;
    const empties = u ? u + (c.bottles[b.code]?.toCollect ?? 0) : 0;
    out[b.code] = {
      filled: u ? String(u) : "",
      empties: kind === "EXCHANGE" && empties ? String(empties) : "",
    };
  }
  return out;
}

export function emptyRow(date: string, paper = ""): Row {
  return {
    key: nextKey(),
    source: "typed",
    paper,
    paperSuggested: paper !== "",
    date: formatDate(date),
    customerText: "",
    customer: null,
    kind: "EXCHANGE",
    qty: {},
    paid: "",
    paidTouched: false,
    phone: false,
    note: "",
    status: "open",
    result: null,
    error: null,
  };
}

export function customerText(c: Pick<GridCustomer, "code" | "name" | "phone">): string {
  return `${c.code} – ${c.name} – ${c.phone}`;
}

export function rowFor(c: GridCustomer, cols: BottleCol[], date: string, source: Row["source"]): Row {
  return {
    ...emptyRow(date),
    source,
    customerText: customerText(c),
    customer: c,
    qty: usualQty(c, cols, "EXCHANGE"),
  };
}

const num = (v: string | undefined) => {
  const n = Number((v ?? "").trim() || 0);
  return Number.isFinite(n) ? Math.trunc(n) : 0;
};

/** The rule calculation of a row (null without a customer). */
export function rowCalc(r: Row, cols: BottleCol[]): ExchangeCalc | null {
  const c = r.customer;
  if (!c) return null;
  return calculate(
    { name: c.name, typeName: c.typeName || "this customer type", bottles: c.bottles },
    r.kind === "LEFT_AT_DOOR",
    cols.map((b) => ({
      bottle: b,
      filled: num(r.qty[b.code]?.filled),
      empties: num(r.qty[b.code]?.empties),
      price: c.prices[b.code] ?? null,
    })),
    r.note,
    r.phone,
  );
}

/** Cash paid filled in: the amount for Cash customers on an Exchange; empty for Credit / Monthly bill and Left at door. */
export function autoPaid(r: Row, calc: ExchangeCalc | null): string {
  if (!r.customer || !calc || r.kind !== "EXCHANGE" || r.customer.payType !== "CASH" || !calc.amount)
    return "";
  return String(calc.amount);
}

/** Cash paid shown in the row: what the user typed, else the automatic amount. */
export function shownPaid(r: Row, calc: ExchangeCalc | null): string {
  return r.paidTouched ? r.paid : autoPaid(r, calc);
}

export function paidNumber(r: Row, calc: ExchangeCalc | null): number {
  const n = Number(shownPaid(r, calc).trim() || 0);
  return Number.isFinite(n) ? n : 0;
}

/** Next delivery after a bill on {@code date} (design 5.4 advance; a bill before the next delivery does not move it). */
export function nextAfter(next: string, cycleWeeks: number, date: string): string {
  let n = next;
  while (n <= date) n = addDaysIso(n, 7 * cycleWeeks);
  return n;
}

/**
 * Suggested paper bill number: previous numeric row + 1, else the highest entered + 1 (server). Numbers already in
 * another row are skipped.
 */
export function suggestPaper(rows: Row[], beforeKey: number, serverNext: string | null): string {
  const i = rows.findIndex((r) => r.key === beforeKey);
  for (let j = (i < 0 ? rows.length : i) - 1; j >= 0; j--) {
    const v = rows[j].paper.trim();
    if (/^\d{1,15}$/.test(v)) return freePaper(rows, beforeKey, Number(v) + 1);
  }
  return serverNext && /^\d{1,15}$/.test(serverNext)
    ? freePaper(rows, beforeKey, Number(serverNext))
    : (serverNext ?? "");
}

/** The first number from {@code from} on that no other row of the grid uses. */
export function freePaper(rows: Row[], key: number, from: number): string {
  let n = from;
  while (rows.some((r) => r.key !== key && r.paper.trim() === String(n))) n++;
  return String(n);
}

export type MsgKind = "ok" | "warn" | "err" | "muted" | "amount";
export interface Msg {
  kind: MsgKind;
  text: string;
}

export interface RowView {
  calc: ExchangeCalc | null;
  msgs: Msg[];
  /** Errors that stop saving (shown before saving; the server checks again). */
  hasError: boolean;
  dupInGrid: boolean;
  badDate: boolean;
  customerMissing: boolean;
  overLimit: boolean;
}

/** Live result of an unsaved row (prototype recalc): amount, bottles, rule problems, duplicates, credit. */
export function rowView(
  r: Row,
  rows: Row[],
  cols: BottleCol[],
  today: string,
  dbDuplicate: string | null,
): RowView {
  const msgs: Msg[] = [];
  let hasError = false;
  const err = (text: string) => {
    hasError = true;
    msgs.push({ kind: "err", text });
  };
  const paper = r.paper.trim().toUpperCase();
  const dupInGrid =
    paper !== "" && rows.some((o) => o.key !== r.key && o.paper.trim().toUpperCase() === paper);
  if (dbDuplicate) err(dbDuplicate);
  else if (dupInGrid) err("Same bill no. twice");
  const iso = parseDmy(r.date);
  const badDate = !iso;
  if (r.date.trim() && !iso) err("Enter the date as DD/MM/YYYY");
  else if (iso && iso > today) err("Bills cannot be entered for a future date");
  const customerMissing = r.customerText.trim() !== "" && !r.customer;
  if (customerMissing) err("Customer not found");
  // The server's wording (SalesService CUSTOMER_INACTIVE).
  if (r.customer?.inactive)
    err(`${r.customer.name} is inactive. Reactivate the customer before entering sales.`);

  const calc = rowCalc(r, cols);
  let overLimit = false;
  if (calc && r.customer) {
    const c = r.customer;
    if (calc.amount)
      msgs.push({
        kind: "amount",
        text: formatMoney(calc.amount) + (c.payType !== "CASH" ? ` → ${c.payTypeLabel}` : ""),
      });
    for (const l of calc.lines) {
      if (l.filled && l.agreedPrice && l.unitPrice !== null)
        msgs.push({ kind: "muted", text: `${l.label} @ ${formatMoney(l.unitPrice)} agreed` });
    }
    for (const l of calc.lines) {
      if (l.owedAdded) msgs.push({ kind: "warn", text: `+${l.owedAdded} ${l.label} owed` });
      if (l.toCollectAdded) msgs.push({ kind: "warn", text: `${l.toCollectAdded} ${l.label} to collect` });
      if (l.toCollectCleared)
        msgs.push({ kind: "ok", text: `collects ${l.toCollectCleared} left last trip` });
      if (l.owedCleared) msgs.push({ kind: "ok", text: `settles ${l.owedCleared} owed` });
    }
    for (const p of calc.problems) {
      if (p.code === NOTE_REQUIRED) err("Note needed for the shortage");
      else if (p.field === "phoneConfirmed") err("Tick phone agreement");
      else if (p.field === "note") err("Note needed");
      else err(p.message);
    }
    const credit = exceedsCredit(c.payType, c.creditLimit, c.balance, calc.amount, paidNumber(r, calc));
    overLimit = credit.exceeds;
    if (overLimit) msgs.push({ kind: "warn", text: "Over credit limit" });
    if (iso && !hasError)
      msgs.push({
        kind: "muted",
        text: `next: ${formatDate(nextAfter(c.nextDeliveryDate, c.cycleWeeks, iso))}`,
      });
  }
  return { calc, msgs, hasError, dupInGrid, badDate, customerMissing, overLimit };
}

/** "✓ Saved as B000278 · Rs. 700.00, paid Rs. 700.00 · next delivery 09/10/2026" */
export function savedText(res: SaleResult): string {
  const t = res.txn;
  let s = `✓ Saved as ${t.billNo} · ${formatMoney(t.amount)}`;
  if (Number(t.paid)) s += `, paid ${formatMoney(t.paid)}`;
  if (res.schedule && res.schedule.after !== res.schedule.before)
    s += ` · next delivery ${formatDate(res.schedule.after)}`;
  return s;
}

/** The request for POST /sales/bills. */
export function billRequest(r: Row, cols: BottleCol[], approveOverLimit: boolean) {
  const left = r.kind === "LEFT_AT_DOOR";
  return {
    customerId: r.customer?.id ?? null,
    date: parseDmy(r.date),
    paperBillNo: r.paper.trim(),
    kind: r.kind,
    lines: cols
      .map((b) => ({
        bottleTypeCode: b.code,
        filled: num(r.qty[b.code]?.filled),
        empties: left ? 0 : num(r.qty[b.code]?.empties),
      }))
      .filter((l) => l.filled || l.empties),
    paid: paidNumber(r, rowCalc(r, cols)),
    note: r.note.trim() || null,
    phoneConfirmed: left && r.phone,
    approveOverLimit,
  };
}

/** Footer: bills (rows with a customer), saved, filled / empties per bottle type, cash, total charged. */
export function totals(rows: Row[], cols: BottleCol[]) {
  const filled: Record<string, number> = {};
  const empties: Record<string, number> = {};
  let bills = 0;
  let saved = 0;
  let cash = 0;
  let charged = 0;
  for (const r of rows) {
    if (r.status === "saved") saved++;
    if (!r.customer) continue;
    bills++;
    const calc = r.status === "saved" ? null : rowCalc(r, cols);
    cash += r.status === "saved" && r.result ? Number(r.result.txn.paid) : paidNumber(r, calc);
    charged += r.status === "saved" && r.result ? Number(r.result.txn.amount) : (calc?.amount ?? 0);
    for (const b of cols) {
      filled[b.code] = (filled[b.code] ?? 0) + num(r.qty[b.code]?.filled);
      empties[b.code] =
        (empties[b.code] ?? 0) + (r.kind === "LEFT_AT_DOOR" ? 0 : num(r.qty[b.code]?.empties));
    }
  }
  return { bills, saved, filled, empties, cash, charged };
}
