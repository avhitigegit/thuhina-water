import { describe, expect, it } from "vitest";
import { addDaysIso } from "@/lib/format";
import { actionBadgeKind, actionLabel, roleBadgeKind } from "./labels";
import { companySchema, editUserSchema, logoProblem, MSG_USERNAME, newUserSchema } from "./schemas";

const valid = {
  fullName: " Saman Dissanayake ",
  username: " Saman.D ",
  role: "DELIVERY_STAFF",
  phone: "",
  temporaryPassword: "Start-123",
};

function errorsOf(result: {
  success: boolean;
  error?: { issues: { path: PropertyKey[]; message: string }[] };
}) {
  return Object.fromEntries((result.error?.issues ?? []).map((i) => [String(i.path[0]), i.message]));
}

describe("user form rules", () => {
  it("accepts a valid new user and lower-cases the username", () => {
    const r = newUserSchema.safeParse(valid);
    expect(r.success).toBe(true);
    expect(r.data).toMatchObject({ fullName: "Saman Dissanayake", username: "saman.d" });
  });

  it("uses the prototype messages", () => {
    expect(errorsOf(newUserSchema.safeParse({ ...valid, username: "ab" })).username).toBe(MSG_USERNAME);
    expect(errorsOf(newUserSchema.safeParse({ ...valid, username: "saman d" })).username).toBe(MSG_USERNAME);
    expect(
      errorsOf(newUserSchema.safeParse({ ...valid, temporaryPassword: "1234567" })).temporaryPassword,
    ).toBe("Temporary password must be at least 8 characters.");
    expect(errorsOf(newUserSchema.safeParse({ ...valid, fullName: "  " })).fullName).toBe(
      "Full name is required.",
    );
    expect(errorsOf(newUserSchema.safeParse({ ...valid, role: "" })).role).toBe("Choose a role.");
  });

  it("edit has no password", () => {
    expect(editUserSchema.safeParse({ ...valid, temporaryPassword: undefined }).success).toBe(true);
  });
});

describe("company form rules", () => {
  const company = { name: "Thuhina Water", address: "", phone: "", email: "", regNo: "" };
  it("name is required and the email must look right", () => {
    expect(companySchema.safeParse(company).success).toBe(true);
    expect(errorsOf(companySchema.safeParse({ ...company, name: " " })).name).toBe(
      "Company name is required.",
    );
    expect(errorsOf(companySchema.safeParse({ ...company, email: "accounts" })).email).toBe(
      "Enter a valid email address.",
    );
    expect(companySchema.safeParse({ ...company, email: "accounts@thuhinawater.lk" }).success).toBe(true);
  });

  it("logo must be PNG or JPG up to 1 MB", () => {
    const file = (type: string, size: number) => new File([new Uint8Array(size)], "logo", { type });
    expect(logoProblem(file("image/png", 1000))).toBeNull();
    expect(logoProblem(file("image/jpeg", 1024 * 1024))).toBeNull();
    expect(logoProblem(file("image/svg+xml", 10))).toBe("Logo must be a PNG or JPG image.");
    expect(logoProblem(file("image/png", 1024 * 1024 + 1))).toBe("Logo must be 1 MB or smaller.");
  });
});

describe("labels", () => {
  it("role and action badges as in the prototype", () => {
    expect(roleBadgeKind("ADMIN")).toBe("dark");
    expect(roleBadgeKind("ACCOUNTANT")).toBe("info");
    expect(roleBadgeKind("DELIVERY_STAFF")).toBe("");
    expect(actionLabel("REVERSE")).toBe("Reverse");
    expect(actionBadgeKind("REVERSE")).toBe("bad");
    expect(actionBadgeKind("LOGIN")).toBe("");
  });

  it("audit default range is the last 7 days", () => {
    expect(addDaysIso("2026-10-05", -7)).toBe("2026-09-28");
    expect(addDaysIso("2026-03-01", -1)).toBe("2026-02-28");
    expect(addDaysIso("2026-12-31", 1)).toBe("2027-01-01");
  });
});
