/*
 * Form rules of the Stock pop-ups – the same wording as the server (M04) and the prototype (Ops.companyDamage,
 * Ops.adjustStock). The server checks again, including "never below zero" (BR-12).
 */
import { z } from "zod";

const wholeAtLeast = (min: number, message: string) =>
  z
    .string()
    .trim()
    .refine((v) => /^\d+$/.test(v) && Number(v) >= min, message);

/** `today` = business date: dates may not be after today. */
function dateUpTo(today: string) {
  return z
    .string()
    .nullable()
    .refine((v) => !!v, "Enter the date.")
    .refine((v) => !v || v <= today, `Date cannot be after today.`);
}

export function damageSchema(today: string) {
  return z.object({
    date: dateUpTo(today),
    bottleTypeCode: z.string().min(1, "Choose the bottle type."),
    qty: wholeAtLeast(1, "Enter the number of damaged bottles."),
    location: z.string().min(1, "Select where the damage happened."),
    reason: z.string().trim().min(1, "Enter the reason.").max(200, "Reason: at most 200 characters."),
  });
}
export type DamageValues = z.input<ReturnType<typeof damageSchema>>;

/** Count: counted ≥ 0. Lost (bottles only): qty ≥ 1. */
export function adjustmentSchema(today: string) {
  return z
    .object({
      date: dateUpTo(today),
      itemCode: z.string().min(1, "Choose the item."),
      isBottle: z.boolean(),
      mode: z.enum(["COUNT", "LOST"]),
      bucket: z.string(),
      counted: z.string().trim(),
      qty: z.string().trim(),
      reason: z
        .string()
        .trim()
        .min(1, "Enter the reason for the adjustment.")
        .max(200, "Reason: at most 200 characters."),
    })
    .superRefine((v, ctx) => {
      if (v.isBottle && !["EMPTY", "FILLED", "FACTORY"].includes(v.bucket)) {
        ctx.addIssue({ code: "custom", path: ["bucket"], message: "Select the stock status to adjust." });
      }
      if (v.mode === "LOST" && v.isBottle) {
        if (!/^\d+$/.test(v.qty) || Number(v.qty) < 1) {
          ctx.addIssue({ code: "custom", path: ["qty"], message: "Enter the number of bottles lost." });
        }
      } else if (!/^\d+$/.test(v.counted)) {
        ctx.addIssue({ code: "custom", path: ["counted"], message: "Enter the counted quantity." });
      }
    });
}
export type AdjustmentValues = z.input<ReturnType<typeof adjustmentSchema>>;
