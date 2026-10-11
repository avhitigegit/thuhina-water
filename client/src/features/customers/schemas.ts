/*
 * Form rules of the Customers pop-ups – the same rules and wording as the server (M05) and the prototype
 * (Ops.saveCustomer, setCustomerStatus, setCustPrice). The server checks again (and the duplicate phone).
 */
import { z } from "zod";
import { isoWeekday, dayName } from "./labels";
import { formatDate } from "@/lib/format";

export const MSG_REQUIRED = "Name, address, phone, customer type and area are required.";
export const MSG_PHONE = "Phone must be a Sri Lankan number, e.g. 077 123 4567 or 011 285 4471.";
export const PHONE_RE = /^0\d{2} ?\d{3} ?\d{4}$/;

/** Customer form. `isNew` adds the registration date and agreed prices; `today` = business date. */
export function customerSchema(isNew: boolean, today: string, bottleCodes: string[]) {
  return z
    .object({
      name: z.string().trim().min(1, MSG_REQUIRED).max(120, "Name: at most 120 characters."),
      phone: z.string().trim().min(1, MSG_REQUIRED).regex(PHONE_RE, MSG_PHONE),
      address: z.string().trim().min(1, MSG_REQUIRED).max(250, "Address: at most 250 characters."),
      areaId: z.string().min(1, MSG_REQUIRED),
      customerTypeId: z.string().min(1, MSG_REQUIRED),
      registeredOn: z.string().nullable(),
      deliveryDay: z.string(),
      cycleWeeks: z.string(),
      nextDeliveryDate: z.string().nullable(),
      usualQty: z.record(z.string(), z.string()),
      payType: z.enum(["CASH", "CREDIT", "MONTHLY_BILL"]),
      creditLimit: z.string().trim(),
      termsDays: z.string(),
      agreedPrices: z.record(z.string(), z.string()),
      agreedReason: z.string().trim().max(250),
      whatsappOptIn: z.boolean(),
      whatsappNumber: z.string().trim(),
      notes: z.string().trim().max(500, "Notes: at most 500 characters."),
    })
    .superRefine((v, ctx) => {
      if (isNew) {
        if (!v.registeredOn)
          ctx.addIssue({ code: "custom", path: ["registeredOn"], message: "Enter the registration date." });
        else if (v.registeredOn > today)
          ctx.addIssue({
            code: "custom",
            path: ["registeredOn"],
            message: "Registration date cannot be in the future.",
          });
      }
      if (!v.nextDeliveryDate) {
        ctx.addIssue({
          code: "custom",
          path: ["nextDeliveryDate"],
          message: "Enter the next delivery date.",
        });
      } else if (isoWeekday(v.nextDeliveryDate) !== Number(v.deliveryDay)) {
        ctx.addIssue({
          code: "custom",
          path: ["nextDeliveryDate"],
          message: `Next delivery date ${formatDate(v.nextDeliveryDate)} is a ${dayName(isoWeekday(v.nextDeliveryDate))}, but the delivery day is ${dayName(Number(v.deliveryDay))}.`,
        });
      }
      for (const code of bottleCodes) {
        const q = v.usualQty[code] ?? "";
        if (q !== "" && !/^\d+$/.test(q))
          ctx.addIssue({ code: "custom", path: ["usualQty", code], message: "A whole number, 0 or more." });
      }
      if (v.payType !== "CASH") {
        if (!(Number(v.creditLimit) > 0))
          ctx.addIssue({
            code: "custom",
            path: ["creditLimit"],
            message: "Credit and Monthly bill customers need a credit limit.",
          });
        if (!["7", "14", "30", "45", "60"].includes(v.termsDays))
          ctx.addIssue({
            code: "custom",
            path: ["termsDays"],
            message: "Choose the payment terms from the list.",
          });
      }
      if (isNew) {
        const anyPrice = bottleCodes.some((c) => (v.agreedPrices[c] ?? "").trim() !== "");
        for (const code of bottleCodes) {
          const p = (v.agreedPrices[code] ?? "").trim();
          if (p !== "" && !(Number(p) > 0))
            ctx.addIssue({
              code: "custom",
              path: ["agreedPrices", code],
              message: "Enter the agreed price per bottle.",
            });
        }
        if (anyPrice && !v.agreedReason)
          ctx.addIssue({
            code: "custom",
            path: ["agreedReason"],
            message: "Enter the reason for the agreed price (e.g. about 3,500 bottles a month).",
          });
      }
      if (v.whatsappNumber && !PHONE_RE.test(v.whatsappNumber))
        ctx.addIssue({ code: "custom", path: ["whatsappNumber"], message: MSG_PHONE });
    });
}
export type CustomerValues = z.input<ReturnType<typeof customerSchema>>;

/** Deactivate: date (not after today), reason from the list; "Other" needs details. */
export function deactivateSchema(today: string) {
  return z
    .object({
      date: z.string().nullable(),
      reason: z.string(),
      details: z.string().trim().max(200),
    })
    .superRefine((v, ctx) => {
      if (!v.date) ctx.addIssue({ code: "custom", path: ["date"], message: "Enter the inactive-from date." });
      else if (v.date > today)
        ctx.addIssue({ code: "custom", path: ["date"], message: "The status date cannot be in the future." });
      if (!v.reason)
        ctx.addIssue({ code: "custom", path: ["reason"], message: "Enter the reason for deactivating." });
      if (v.reason === "Other" && !v.details)
        ctx.addIssue({ code: "custom", path: ["details"], message: 'Enter the details for "Other".' });
    });
}
export type DeactivateValues = z.input<ReturnType<typeof deactivateSchema>>;

/** "Moved away – Moved to Kandy" */
export function statusReason(reason: string, details: string): string {
  return details ? `${reason} – ${details}` : reason;
}
