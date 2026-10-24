/*
 * Form rules of the Suppliers & Factories pop-ups – the same rules and wording as the server (M03) and the prototype
 * (Ops.saveSupplier, Ops.saveFactory). The server checks again.
 */
import { z } from "zod";

/** The fixed payment terms list (prototype TERMS, server PaymentTerms). */
export const TERMS: { days: number; label: string }[] = [
  { days: 0, label: "Cash on delivery" },
  { days: 7, label: "7 days" },
  { days: 14, label: "14 days" },
  { days: 30, label: "30 days" },
  { days: 45, label: "45 days" },
  { days: 60, label: "60 days" },
];

export const MSG_SUPPLIER_REQUIRED = "Supplier name and phone are required.";
export const MSG_ITEMS = "Select at least one item the supplier supplies.";
const MSG_TERMS = "Choose the payment terms from the list.";

const email = z
  .string()
  .trim()
  .max(100, "Email: at most 100 characters.")
  .refine((v) => v === "" || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v), "Enter a valid email address.");

const terms = z.string().refine((v) => TERMS.some((t) => String(t.days) === v), MSG_TERMS);

export const supplierSchema = z.object({
  name: z.string().trim().min(1, MSG_SUPPLIER_REQUIRED).max(100, "Name: at most 100 characters."),
  contact: z.string().trim().max(100, "Contact: at most 100 characters."),
  phone: z.string().trim().min(1, MSG_SUPPLIER_REQUIRED).max(20, "Phone: at most 20 characters."),
  email,
  address: z.string().trim().max(200, "Address: at most 200 characters."),
  termsDays: terms,
  items: z.array(z.string()).min(1, MSG_ITEMS),
  active: z.boolean(),
});
export type SupplierValues = z.input<typeof supplierSchema>;

/** A blank charge = the factory does not fill that size (N-05); a typed one must be more than 0. */
function optionalCharge(message: string) {
  return z
    .string()
    .trim()
    .refine((v) => v === "" || Number(v) > 0, message)
    .refine((v) => v === "" || /^\d+(\.\d{1,2})?$/.test(v), "Use rupees and cents, e.g. 1350 or 1350.50.");
}

export const MSG_CHARGE_NONE = "Enter the charge for at least one bottle type.";

/**
 * Factory form: a charge for each bottle type the factory fills, at least one ({code → "Enter the charge per 20L bottle
 * (more than 0)."}). The server also refuses blanking a charge that is already set.
 */
export function factorySchema(bottles: { code: string; label: string }[]) {
  return z.object({
    name: z.string().trim().min(1, "Enter the factory name.").max(100, "Name: at most 100 characters."),
    address: z.string().trim().max(200, "Address: at most 200 characters."),
    licence: z.string().trim().max(100, "Licence: at most 100 characters."),
    contact: z.string().trim().max(100, "Contact: at most 100 characters."),
    phone: z.string().trim().max(20, "Phone: at most 20 characters."),
    email,
    termsDays: terms,
    charges: z
      .object(
        Object.fromEntries(
          bottles.map((b) => [
            b.code,
            optionalCharge(`Enter the charge per ${b.label} bottle (more than 0).`),
          ]),
        ),
      )
      .refine((c) => bottles.length === 0 || Object.values(c).some((v) => v !== ""), {
        message: MSG_CHARGE_NONE,
        path: [bottles[0]?.code ?? ""],
      }),
    active: z.boolean(),
  });
}
export type FactoryValues = {
  name: string;
  address: string;
  licence: string;
  contact: string;
  phone: string;
  email: string;
  termsDays: string;
  charges: Record<string, string>;
  active: boolean;
};
