import { describe, expect, it } from "vitest";
import type { CqLineView, CustomerPriceRow } from "@/lib/api/types";
import { applyWarnings } from "./applyWarnings";

const line = (price: number): CqLineView => ({
  lineNo: 1,
  kind: "WATER",
  bottleTypeCode: "B20",
  productCode: null,
  description: "20L water",
  qty: 40,
  unit: null,
  unitPrice: price,
  amount: price * 40,
  currentPrice: null,
  currentAgreed: false,
  belowCurrent: false,
});

const row = (
  price: number,
  validFrom: string,
  validUntil: string | null,
  state = "IN_FORCE",
): CustomerPriceRow =>
  ({ bottleTypeCode: "B20", label: "20L", price, validFrom, validUntil, state }) as CustomerPriceRow;

describe("Apply quoted prices – planned prices it cancels (N-14)", () => {
  it("warns that the planned return to the standard price is removed (QT-005)", () => {
    expect(applyWarnings([line(300)], [row(290, "2026-09-01", "2026-11-30")], "2026-11-01")).toEqual([
      "20L: the plan to go back to the standard price from 01/12/2026 will be removed.",
    ]);
  });

  it("warns that a planned price is cancelled – not as a return to the standard price", () => {
    const prices = [row(290, "2026-09-01", "2026-11-30"), row(270, "2026-12-01", null, "SCHEDULED")];
    expect(applyWarnings([line(300)], prices, "2026-11-01")).toEqual([
      "20L: the planned price Rs. 270.00 from 01/12/2026 will be cancelled.",
    ]);
  });

  it("no warning when nothing is planned, for the same price, or for cancelled rows", () => {
    expect(applyWarnings([line(300)], [row(290, "2026-09-01", null)], "2026-11-01")).toEqual([]);
    expect(applyWarnings([line(290)], [row(290, "2026-09-01", "2026-11-30")], "2026-11-01")).toEqual([]);
    expect(applyWarnings([line(300)], [row(270, "2026-12-01", null, "CANCELLED")], "2026-11-01")).toEqual([]);
  });
});
