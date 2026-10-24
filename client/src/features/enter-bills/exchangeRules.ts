/*
 * TypeScript copy of the server's ExchangeRules (design 5.2, BR-03, BR-04, BR-07) for the live Result column of the
 * Enter Bills grid (M07 C4) – the grid would otherwise wait for one server call per row. The server checks every bill
 * again on save. Both sides run the shared cases in server/src/test/resources/rules/exchange-cases.json; change the
 * two together.
 */

/** A customer's bottles of one type. */
export interface Bottles {
  held: number;
  toCollect: number;
  owed: number;
}

export const NO_BOTTLES: Bottles = { held: 0, toCollect: 0, owed: 0 };

export interface RuleCustomer {
  name: string;
  typeName: string;
  bottles: Record<string, Bottles>;
}

export interface RuleLine {
  bottle: { code: string; label: string; name: string };
  filled: number;
  empties: number;
  /** Water price for the customer on the bill date; null = none set. */
  price: { price: number; agreed: boolean } | null;
}

export interface CalcLine {
  bottleTypeCode: string;
  label: string;
  filled: number;
  empties: number;
  unitPrice: number | null;
  agreedPrice: boolean;
  owedAdded: number;
  owedCleared: number;
  toCollectAdded: number;
  toCollectCleared: number;
  lineAmount: number;
}

export interface RuleProblem {
  field: string | null;
  code: string;
  message: string;
}

export interface ExchangeCalc {
  lines: CalcLine[];
  amount: number;
  /** The customer's bottles after the bill. */
  after: Record<string, Bottles>;
  /** Reasons the bill cannot be saved (empty = can be saved). */
  problems: RuleProblem[];
}

export const SALE_RULE = "SALE_RULE";
export const NOTE_REQUIRED = "NOTE_REQUIRED";

const field = (f: string, message: string): RuleProblem => ({ field: f, code: SALE_RULE, message });
const rule = (message: string): RuleProblem => ({ field: null, code: SALE_RULE, message });

/** Money with 2 decimals, half up (server Money.of). */
function money(v: number): number {
  return Math.round((v + Number.EPSILON) * 100) / 100;
}

/**
 * Per bottle type: diff = F − E; more filled than empties → owed += diff (a note is needed); more empties → clear
 * empties to collect first, then owed; anything left over is an error. Left at door: empties = 0 and to collect += F.
 * Amount = F × price.
 */
export function calculate(
  c: RuleCustomer,
  leftAtDoor: boolean,
  input: RuleLine[],
  note: string | null,
  phoneConfirmed: boolean,
): ExchangeCalc {
  const lines: CalcLine[] = [];
  const problems: RuleProblem[] = [];
  const seen = new Set<string>();
  let water = 0;
  let owedAdded = 0;
  const bottlesOf = (code: string) => c.bottles[code] ?? NO_BOTTLES;

  for (const inp of input) {
    const f = inp.filled;
    const e = leftAtDoor ? 0 : inp.empties;
    if (f < 0 || e < 0) {
      problems.push(field("lines", "Quantities cannot be negative."));
      continue;
    }
    if (f === 0 && e === 0) continue;
    const code = inp.bottle.code;
    if (seen.has(code)) {
      problems.push(field("lines", "Enter each bottle type once."));
      continue;
    }
    seen.add(code);
    const now = bottlesOf(code);
    const l: CalcLine = {
      bottleTypeCode: code,
      label: inp.bottle.label,
      filled: f,
      empties: e,
      unitPrice: inp.price ? inp.price.price : null,
      agreedPrice: inp.price ? inp.price.agreed : false,
      owedAdded: 0,
      owedCleared: 0,
      toCollectAdded: 0,
      toCollectCleared: 0,
      lineAmount: 0,
    };
    if (leftAtDoor) {
      l.toCollectAdded = f;
    } else if (f > e) {
      l.owedAdded = f - e;
    } else if (e > f) {
      let extra = e - f;
      const fromCollect = Math.min(extra, now.toCollect);
      extra -= fromCollect;
      const fromOwed = Math.min(extra, now.owed);
      extra -= fromOwed;
      l.toCollectCleared = fromCollect;
      l.owedCleared = fromOwed;
      if (extra > 0) {
        problems.push(
          rule(
            `${c.name} returned ${extra} more ${inp.bottle.label} empties than they hold, owe or have waiting for collection. Check the bill.`,
          ),
        );
      }
    }
    if (f > 0 && now.held === 0) {
      problems.push(
        rule(
          `${c.name} holds no ${inp.bottle.label} bottles. Use Extra order (deposit needed) for new bottles.`,
        ),
      );
    }
    if (inp.price === null && f > 0) {
      problems.push(rule(`No water price set for ${inp.bottle.name} / ${c.typeName}.`));
    }
    l.lineAmount = l.unitPrice === null ? 0 : money(l.unitPrice * f);
    water += l.lineAmount;
    owedAdded += l.owedAdded;
    lines.push(l);
  }

  if (lines.length === 0 && problems.length === 0) {
    problems.push(field("lines", "Enter the filled bottles given and/or empties collected."));
  }
  const totalHeld = Object.values(c.bottles).reduce((s, b) => s + b.held, 0);
  if (!leftAtDoor && totalHeld === 0) {
    problems.unshift(rule(`${c.name} has no bottles yet. Record a First purchase instead.`));
  }
  const blankNote = !note || note.trim() === "";
  if (leftAtDoor) {
    if (!phoneConfirmed) problems.push(field("phoneConfirmed", "Confirm that the customer agreed by phone."));
    if (blankNote) problems.push(field("note", 'Add a note for the "Left at door" delivery.'));
  } else if (owedAdded > 0 && blankNote) {
    problems.push({
      field: "note",
      code: NOTE_REQUIRED,
      message: `Fewer empties than filled bottles: ${owedAdded} bottle(s) will be recorded as owed. Add a note explaining the shortage.`,
    });
  }

  const after: Record<string, Bottles> = {};
  for (const [code, b] of Object.entries(c.bottles)) after[code] = { ...b };
  for (const l of lines) {
    const b = after[l.bottleTypeCode] ?? { ...NO_BOTTLES };
    after[l.bottleTypeCode] = {
      held: b.held,
      toCollect: b.toCollect + l.toCollectAdded - l.toCollectCleared,
      owed: b.owed + l.owedAdded - l.owedCleared,
    };
  }
  return { lines, amount: money(water), after, problems };
}

/** Credit check (BR-09, server CreditCheck): only Credit / Monthly bill with a limit, and only when the bill adds to the balance. */
export function exceedsCredit(
  payType: string,
  limit: number,
  balance: number,
  amount: number,
  paid: number,
): { after: number; exceeds: boolean } {
  const after = money(balance + amount - paid);
  return { after, exceeds: payType !== "CASH" && limit > 0 && after > limit && amount - paid > 0 };
}
