/*
 * Form rules of the Bottles & Products pop-ups – the same rules and wording as the server (M02) and the prototype
 * (Ops.saveBottleType, saveProduct, setPrice, saveBrand). The server checks again.
 */
import { z } from "zod";
import { moneyText, optionalMoneyText } from "@/lib/forms";

export const MSG_BOTTLE_REQUIRED = "Name and size in litres are required.";
export const MSG_PRODUCT_REQUIRED = "Name and selling price are required.";

/** Bottle code from the size, as the server makes it: 19 → B19, 0.5 → B0_5. */
export function bottleCodeFor(litres: string): string | null {
  const n = Number(litres);
  if (!litres.trim() || !(n > 0)) return null;
  return "B" + String(n).replace(".", "_");
}

export const bottleTypeSchema = z.object({
  name: z.string().trim().min(1, MSG_BOTTLE_REQUIRED).max(60, "Name: at most 60 characters."),
  litres: z
    .string()
    .trim()
    .min(1, MSG_BOTTLE_REQUIRED)
    .refine(
      (v) => Number(v) > 0 && Number(v) <= 1000 && /^\d+(\.\d{1,2})?$/.test(v),
      "Size: more than 0 and at most 1000 litres, with up to 2 decimals.",
    ),
  active: z.boolean(),
});
export type BottleTypeValues = z.input<typeof bottleTypeSchema>;

export const productSchema = z.object({
  name: z.string().trim().min(1, MSG_PRODUCT_REQUIRED).max(100, "Name: at most 100 characters."),
  sellingPrice: z
    .string()
    .trim()
    .min(1, MSG_PRODUCT_REQUIRED)
    .pipe(moneyText("Enter a selling price greater than zero.")),
  costPrice: optionalMoneyText("Cost price cannot be negative."),
  openingStock: z
    .string()
    .trim()
    .refine((v) => v === "" || /^\d+$/.test(v), "Opening stock: a whole number, 0 or more."),
  active: z.boolean(),
});
export type ProductValues = z.input<typeof productSchema>;

/** Price change pop-up. `today` is the business date: the change may start today or later. */
export function priceChangeSchema(today: string) {
  return z.object({
    price: moneyText("Enter a price greater than zero."),
    effectiveFrom: z
      .string()
      .nullable()
      .refine((v) => !!v, "Enter the effective date.")
      .refine((v) => !v || v >= today, "The effective date cannot be before today."),
    reason: z
      .string()
      .trim()
      .min(1, "Enter the reason for the change.")
      .max(200, "Reason: at most 200 characters."),
  });
}
export type PriceChangeValues = z.input<ReturnType<typeof priceChangeSchema>>;

export const brandSchema = z.object({
  name: z.string().trim().min(1, "Enter the brand name.").max(60, "Brand: at most 60 characters."),
  bottleTypeCode: z.string().min(1, "Choose the bottle type."),
  note: z.string().trim().max(200, "Note: at most 200 characters."),
});
export type BrandValues = z.input<typeof brandSchema>;

/** Low stock warning on the products list (prototype: red when 3 or fewer). */
export const LOW_PRODUCT_STOCK = 3;
