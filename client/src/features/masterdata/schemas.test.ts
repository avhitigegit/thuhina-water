import { describe, expect, it } from "vitest";
import {
  bottleCodeFor,
  bottleTypeSchema,
  brandSchema,
  MSG_BOTTLE_REQUIRED,
  MSG_PRODUCT_REQUIRED,
  priceChangeSchema,
  productSchema,
} from "./schemas";

function errorsOf(result: {
  success: boolean;
  error?: { issues: { path: PropertyKey[]; message: string }[] };
}) {
  const out: Record<string, string> = {};
  for (const i of result.error?.issues ?? []) out[String(i.path[0])] ??= i.message;
  return out;
}

describe("bottle types", () => {
  it("code from the size, as the server makes it", () => {
    expect(bottleCodeFor("19")).toBe("B19");
    expect(bottleCodeFor("0.5")).toBe("B0_5");
    expect(bottleCodeFor("20.00")).toBe("B20");
    expect(bottleCodeFor("")).toBeNull();
    expect(bottleCodeFor("0")).toBeNull();
  });

  it("name and size are required; size > 0, ≤ 1000, 2 decimals", () => {
    expect(errorsOf(bottleTypeSchema.safeParse({ name: "", litres: "", active: true }))).toEqual({
      name: MSG_BOTTLE_REQUIRED,
      litres: MSG_BOTTLE_REQUIRED,
    });
    expect(errorsOf(bottleTypeSchema.safeParse({ name: "x", litres: "1.255", active: true })).litres).toBe(
      "Size: more than 0 and at most 1000 litres, with up to 2 decimals.",
    );
    expect(bottleTypeSchema.safeParse({ name: "19L Bottle", litres: "19", active: true }).success).toBe(true);
  });
});

describe("products", () => {
  const ok = { name: "Pump", sellingPrice: "1250", costPrice: "", openingStock: "0", active: true };
  it("name and selling price are required, price > 0", () => {
    expect(productSchema.safeParse(ok).success).toBe(true);
    expect(errorsOf(productSchema.safeParse({ ...ok, name: " ", sellingPrice: "" }))).toMatchObject({
      name: MSG_PRODUCT_REQUIRED,
      sellingPrice: MSG_PRODUCT_REQUIRED,
    });
    expect(errorsOf(productSchema.safeParse({ ...ok, sellingPrice: "0" })).sellingPrice).toBe(
      "Enter a selling price greater than zero.",
    );
    expect(errorsOf(productSchema.safeParse({ ...ok, costPrice: "-1" })).costPrice).toBe(
      "Cost price cannot be negative.",
    );
    expect(errorsOf(productSchema.safeParse({ ...ok, openingStock: "2.5" })).openingStock).toBe(
      "Opening stock: a whole number, 0 or more.",
    );
  });
});

describe("price change", () => {
  const schema = priceChangeSchema("2026-10-11");
  it("price > 0, date today or later, reason required", () => {
    expect(
      schema.safeParse({ price: "375", effectiveFrom: "2026-10-12", reason: "Factory charge" }).success,
    ).toBe(true);
    expect(schema.safeParse({ price: "375", effectiveFrom: "2026-10-11", reason: "Today" }).success).toBe(
      true,
    );
    expect(errorsOf(schema.safeParse({ price: "0", effectiveFrom: null, reason: " " }))).toEqual({
      price: "Enter a price greater than zero.",
      effectiveFrom: "Enter the effective date.",
      reason: "Enter the reason for the change.",
    });
    expect(
      errorsOf(schema.safeParse({ price: "375", effectiveFrom: "2026-10-10", reason: "x" })).effectiveFrom,
    ).toBe("The effective date cannot be before today.");
  });
});

describe("brands", () => {
  it("brand name and bottle type are required", () => {
    expect(errorsOf(brandSchema.safeParse({ name: "", bottleTypeCode: "", note: "" }))).toEqual({
      name: "Enter the brand name.",
      bottleTypeCode: "Choose the bottle type.",
    });
  });
});
