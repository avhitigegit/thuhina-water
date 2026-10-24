/*
 * Form rules of the Administration pop-ups – the same rules and wording as the server (UserRules,
 * prototype Ops.saveUser). The server checks again; its field messages are shown the same way.
 */
import { z } from "zod";

export const MSG_USERNAME = "Username: at least 3 lowercase letters, numbers, dots or underscores.";
export const MSG_TEMP_PASSWORD = "Temporary password must be at least 8 characters.";

const roleSchema = z.enum(["ADMIN", "ACCOUNTANT", "DELIVERY_STAFF"], { message: "Choose a role." });

const base = {
  fullName: z.string().trim().min(1, "Full name is required.").max(100, "Full name: at most 100 characters."),
  // Typed in any case; saved in lower case (as the prototype does).
  username: z
    .string()
    .trim()
    .transform((v) => v.toLowerCase())
    .pipe(
      z
        .string()
        .max(50, MSG_USERNAME)
        .regex(/^[a-z0-9._]{3,}$/, MSG_USERNAME),
    ),
  role: roleSchema,
  phone: z.string().trim().max(20, "Phone: at most 20 characters."),
};

export const newUserSchema = z.object({
  ...base,
  temporaryPassword: z
    .string()
    .min(8, MSG_TEMP_PASSWORD)
    .max(72, "Temporary password: at most 72 characters."),
});

export const editUserSchema = z.object(base);

export type NewUserValues = z.input<typeof newUserSchema>;
export type EditUserValues = z.input<typeof editUserSchema>;

export const companySchema = z.object({
  name: z.string().trim().min(1, "Company name is required.").max(300),
  address: z.string().trim().max(300),
  phone: z.string().trim().max(300),
  email: z
    .string()
    .trim()
    .max(300)
    .refine((v) => v === "" || /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(v), "Enter a valid email address."),
  regNo: z.string().trim().max(300),
});
export type CompanyValues = z.input<typeof companySchema>;

export const LOGO_MAX_BYTES = 1024 * 1024;

/** Client-side check before uploading (the server checks the file content as well). */
export function logoProblem(file: File): string | null {
  if (!["image/png", "image/jpeg"].includes(file.type)) return "Logo must be a PNG or JPG image.";
  if (file.size > LOGO_MAX_BYTES) return "Logo must be 1 MB or smaller.";
  return null;
}
