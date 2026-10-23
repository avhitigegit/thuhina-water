import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { calculate, exceedsCredit, type Bottles } from "./exchangeRules";
import {
  billRequest,
  emptyRow,
  freePaper,
  nextAfter,
  rowFor,
  rowView,
  suggestPaper,
  totals,
  usualQty,
  type GridCustomer,
} from "./grid";

/* The same cases as the server's ExchangeRulesSharedCasesTest (M07 C4). */
type Triple = [number, number, number];
interface SharedLine {
  bottle: string;
  filled: number;
  empties: number;
  price: number | null;
  agreed: boolean;
}
interface SharedCase {
  name: string;
  bottles: Record<string, Triple>;
  leftAtDoor: boolean;
  lines: SharedLine[];
  note: string | null;
  phoneConfirmed: boolean;
  expect: {
    amount: number;
    after: Record<string, Triple>;
    lines: (Record<string, number | string> & { bottle: string })[];
    problems: { field: string | null; code: string; message: string }[];
  };
}
interface SharedFile {
  bottles: Record<string, { label: string; name: string }>;
  customer: { name: string; typeName: string };
  cases: SharedCase[];
}

const shared: SharedFile = JSON.parse(
  readFileSync(resolve(process.cwd(), "../server/src/test/resources/rules/exchange-cases.json"), "utf8"),
);
const toBottles = (t: Triple): Bottles => ({ held: t[0], toCollect: t[1], owed: t[2] });
const map = (o: Record<string, Triple>) =>
  Object.fromEntries(Object.entries(o).map(([k, v]) => [k, toBottles(v)]));

describe("ExchangeRules – TypeScript copy, shared cases with the server", () => {
  it("has the shared cases", () => expect(shared.cases.length).toBeGreaterThanOrEqual(10));

  it.each(shared.cases.map((c) => [c.name, c] as const))("%s", (_name, c) => {
    const calc = calculate(
      { ...shared.customer, bottles: map(c.bottles) },
      c.leftAtDoor,
      c.lines.map((l) => ({
        bottle: { code: l.bottle, ...shared.bottles[l.bottle] },
        filled: l.filled,
        empties: l.empties,
        price: l.price === null ? null : { price: l.price, agreed: l.agreed },
      })),
      c.note,
      c.phoneConfirmed,
    );
    expect(calc.amount).toBe(c.expect.amount);
    expect(calc.after).toEqual(map(c.expect.after));
    expect(
      calc.lines.map((l) => ({
        bottle: l.bottleTypeCode,
        filled: l.filled,
        empties: l.empties,
        owedAdded: l.owedAdded,
        owedCleared: l.owedCleared,
        toCollectAdded: l.toCollectAdded,
        toCollectCleared: l.toCollectCleared,
        lineAmount: l.lineAmount,
      })),
    ).toEqual(c.expect.lines);
    expect(calc.problems).toEqual(c.expect.problems);
  });

  it("credit check: only Credit / Monthly bill over the limit, and only when the bill adds to the balance", () => {
    expect(exceedsCredit("CREDIT", 3000, 2800, 700, 0)).toEqual({ after: 3500, exceeds: true });
    expect(exceedsCredit("CREDIT", 3000, 2800, 700, 700).exceeds).toBe(false);
    expect(exceedsCredit("CASH", 0, 5000, 700, 0).exceeds).toBe(false);
  });
});

const COLS = [{ code: "B20", label: "20L", name: "20L Bottle" }];
const CUST: GridCustomer = {
  id: 1,
  code: "C0001",
  name: "W.M. Sunil Perera",
  phone: "077 123 4567",
  typeName: "Household",
  payType: "CASH",
  payTypeLabel: "Cash",
  creditLimit: 0,
  balance: 0,
  cycleWeeks: 1,
  nextDeliveryDate: "2026-10-12",
  bottles: { B20: { held: 2, toCollect: 1, owed: 0 } },
  usual: { B20: 2 },
  prices: { B20: { price: 350, agreed: false } },
};

describe("Enter Bills grid model", () => {
  it("pre-fills usual quantities; empties include the empties left at the door last trip", () => {
    expect(usualQty(CUST, COLS, "EXCHANGE")).toEqual({ B20: { filled: "2", empties: "3" } });
    expect(usualQty(CUST, COLS, "LEFT_AT_DOOR")).toEqual({ B20: { filled: "2", empties: "" } });
  });

  it("suggests the previous numeric paper bill + 1, else the server's next number", () => {
    const a = { ...emptyRow("2026-10-12"), paper: "41275" };
    const b = emptyRow("2026-10-12");
    expect(suggestPaper([a, b], b.key, "900")).toBe("41276");
    expect(suggestPaper([b], b.key, "900")).toBe("900");
    expect(suggestPaper([{ ...a, paper: "A-1" }, b], b.key, null)).toBe("");
  });

  it("skips paper numbers already used in another row (re-used 5008 after 5009, 5010)", () => {
    const r1 = { ...emptyRow("2026-10-12"), paper: "5009" };
    const r2 = { ...emptyRow("2026-10-12"), paper: "5010" };
    const r3 = { ...emptyRow("2026-10-12"), paper: "5008" };
    const b = emptyRow("2026-10-12");
    expect(suggestPaper([r1, r2, r3, b], b.key, null)).toBe("5011");
    expect(freePaper([r1, r2, b], b.key, 5009)).toBe("5011");
  });

  it("live result: amount, collects, next delivery; Cash paid follows the amount", () => {
    const r = rowFor(CUST, COLS, "2026-10-12", "auto");
    const v = rowView(r, [r], COLS, "2026-10-12", null);
    expect(v.hasError).toBe(false);
    expect(v.msgs.map((m) => m.text)).toEqual([
      "Rs. 700.00",
      "collects 1 left last trip",
      "next: 19/10/2026",
    ]);
    expect(billRequest(r, COLS, false)).toMatchObject({
      customerId: 1,
      date: "2026-10-12",
      kind: "EXCHANGE",
      lines: [{ bottleTypeCode: "B20", filled: 2, empties: 3 }],
      paid: 700,
      approveOverLimit: false,
    });
    expect(totals([r], COLS)).toMatchObject({
      bills: 1,
      cash: 700,
      charged: 700,
      filled: { B20: 2 },
      empties: { B20: 3 },
    });
  });

  it("an inactive customer is an error as soon as chosen (N-06)", () => {
    const r = rowFor({ ...CUST, inactive: true }, COLS, "2026-10-12", "typed");
    const v = rowView(r, [r], COLS, "2026-10-12", null);
    expect(v.hasError).toBe(true);
    expect(v.msgs[0]).toEqual({
      kind: "err",
      text: `${CUST.name} is inactive. Reactivate the customer before entering sales.`,
    });
  });

  it("live result: duplicates, unknown customer, future date, shortage note, left at door", () => {
    const a = { ...rowFor(CUST, COLS, "2026-10-12", "typed"), paper: "500" };
    const b = { ...emptyRow("2026-10-13"), paper: "500", customerText: "Nobody" };
    const texts = (r: typeof a, db: string | null = null) =>
      rowView(r, [a, b], COLS, "2026-10-12", db).msgs.map((m) => m.text);
    expect(texts(b)).toEqual([
      "Same bill no. twice",
      "Bills cannot be entered for a future date",
      "Customer not found",
    ]);
    expect(texts(a, "Paper bill 500 already entered (02/10/2026, Dilshan Fernando)")[0]).toBe(
      "Paper bill 500 already entered (02/10/2026, Dilshan Fernando)",
    );
    const short = { ...a, paper: "501", qty: { B20: { filled: "3", empties: "1" } } };
    expect(texts(short)).toContain("Note needed for the shortage");
    expect(texts(short)).toContain("+2 20L owed");
    const left = { ...a, paper: "502", kind: "LEFT_AT_DOOR" as const };
    expect(texts(left)).toEqual(["Rs. 700.00", "2 20L to collect", "Tick phone agreement", "Note needed"]);
    expect(billRequest({ ...left, phone: true, note: "At the gate" }, COLS, false)).toMatchObject({
      kind: "LEFT_AT_DOOR",
      lines: [{ bottleTypeCode: "B20", filled: 2, empties: 0 }],
      paid: 0,
      phoneConfirmed: true,
    });
  });

  it("next delivery moves by whole cycles past the bill date", () => {
    expect(nextAfter("2026-10-12", 2, "2026-10-12")).toBe("2026-10-26");
    expect(nextAfter("2026-09-28", 1, "2026-10-12")).toBe("2026-10-19");
    expect(nextAfter("2026-10-19", 1, "2026-10-12")).toBe("2026-10-19");
  });
});
