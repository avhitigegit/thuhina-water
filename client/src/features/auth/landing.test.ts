import { describe, expect, it } from "vitest";
import type { MeResponse } from "@/lib/api/types";
import { afterLoginPath, findMenuItem } from "./landing";

function me(overrides: Partial<MeResponse> = {}): MeResponse {
  return {
    user: {
      id: 1,
      username: "shanika.acc",
      fullName: "Shanika Perera",
      role: "ACCOUNTANT",
      roleName: "Accountant",
    },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions: ["dashboard.view", "customers.view", "billing.view", "billing.edit"],
    menu: [
      {
        module: "Dashboard",
        icon: "home",
        hidden: false,
        group: false,
        items: [{ key: "dashboard", title: "Dashboard", path: "/dashboard", view: false }],
      },
      {
        module: "Master Data",
        icon: "db",
        hidden: false,
        group: true,
        items: [{ key: "customers", title: "Customers", path: "/master-data/customers", view: true }],
      },
    ],
    ...overrides,
  };
}

describe("afterLoginPath", () => {
  it("goes to the role's landing page", () => {
    expect(afterLoginPath(me(), null)).toBe("/dashboard");
    expect(afterLoginPath(me({ landingPage: "/delivery/daily-list" }), null)).toBe("/delivery/daily-list");
  });

  it("returns to the requested page when the role may open it", () => {
    expect(afterLoginPath(me(), "/master-data/customers?q=perera")).toBe("/master-data/customers?q=perera");
  });

  it("ignores pages the role may not open and other sites", () => {
    expect(afterLoginPath(me(), "/admin")).toBe("/dashboard");
    expect(afterLoginPath(me(), "//evil.example.com/x")).toBe("/dashboard");
    expect(afterLoginPath(me(), "https://evil.example.com")).toBe("/dashboard");
  });

  it("forces the password change first", () => {
    expect(afterLoginPath(me({ mustChangePassword: true }), "/master-data/customers")).toBe(
      "/change-password",
    );
  });
});

describe("findMenuItem", () => {
  it("matches the page and its sub-pages", () => {
    expect(findMenuItem(me(), "/master-data/customers")?.view).toBe(true);
    expect(findMenuItem(me(), "/master-data/customers/12")?.key).toBe("customers");
    expect(findMenuItem(me(), "/master-data/customersX")).toBeUndefined();
    expect(findMenuItem(me(), "/inventory")).toBeUndefined();
  });
});
