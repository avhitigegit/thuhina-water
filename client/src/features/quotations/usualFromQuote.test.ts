import { describe, expect, it } from "vitest";
import type { CqLineView } from "@/lib/api/types";
import { usualFromQuote } from "./usualFromQuote";

const line = (o: Partial<CqLineView>): CqLineView => ({
  lineNo: 1,
  kind: "WATER",
  bottleTypeCode: "B20",
  productCode: null,
  description: "20L water",
  qty: 40,
  unit: "per month",
  unitPrice: 300,
  amount: 12000,
  currentPrice: 320,
  currentAgreed: false,
  belowCurrent: true,
  ...o,
});

describe("usualFromQuote (N-09)", () => {
  it("shares a monthly quantity over 4 weekly deliveries, rounded up", () => {
    expect(usualFromQuote([line({ qty: 40 }), line({ bottleTypeCode: "B10", qty: 10 })])).toEqual({
      B20: 10,
      B10: 3,
    });
  });

  it("takes other units as the quantity per delivery and skips deposits, products and free text", () => {
    expect(
      usualFromQuote([
        line({ qty: 6, unit: "bottles" }),
        line({ kind: "DEPOSIT", qty: 10 }),
        line({ kind: "PRODUCT", bottleTypeCode: null, productCode: "P01", qty: 2 }),
        line({ kind: "OTHER", bottleTypeCode: null, qty: 0 }),
      ]),
    ).toEqual({ B20: 6 });
  });
});
