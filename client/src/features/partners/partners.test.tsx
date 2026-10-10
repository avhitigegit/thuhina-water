import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccessProvider } from "@/features/auth/access";
import type { MeResponse, Supplier } from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { factorySchema, MSG_ITEMS, supplierSchema, TERMS } from "./schemas";
import { SuppliersTab } from "./SuppliersTab";

const SUPPLIER: Supplier = {
  id: 1,
  code: "S01",
  name: "Lanka Polymer Containers (Pvt) Ltd",
  contact: "Mr. Asanka Silva",
  phone: "011 223 6614",
  email: "sales@lankapolymer.lk",
  address: "No. 18, Ekala Industrial Estate, Ja-Ela",
  termsDays: 30,
  termsLabel: "30 days",
  items: [{ code: "B20", type: "BOTTLE", name: "Empty 20L Bottle" }],
  owed: null,
  active: true,
  version: 0,
};

const API: Record<string, unknown> = {
  "/api/suppliers": [SUPPLIER],
  "/api/bottle-types": [
    {
      code: "B20",
      name: "20L Bottle",
      litres: 20,
      active: true,
      deposit: 1000,
      inCirculation: null,
      version: 0,
    },
  ],
  "/api/products": [
    {
      id: 4,
      code: "P04",
      name: "Manual Bottle Pump",
      sellingPrice: 1250,
      costPrice: 750,
      stockQty: 48,
      active: true,
      version: 0,
    },
  ],
};

function me(permissions: string[]): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role: "ADMIN", roleName: "Admin" },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions,
    menu: [],
  } as MeResponse;
}

function renderTab(permissions: string[]) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
    async (url) => new Response(JSON.stringify(API[url.split("?")[0]] ?? []), { status: 200 }),
  );
  vi.stubGlobal("fetch", fetch);
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrap = (children: ReactNode) => (
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(permissions), viewOnly: false }}>{children}</AccessProvider>
    </QueryClientProvider>
  );
  render(wrap(<SuppliersTab />));
  return fetch;
}

afterEach(() => vi.unstubAllGlobals());

describe("supplier form rules", () => {
  const ok = {
    name: "Kelani Plastics",
    contact: "",
    phone: "011 291 0457",
    email: "",
    address: "",
    termsDays: "14",
    items: ["B20"],
    active: true,
  };
  it("needs name, phone, an item and terms from the list", () => {
    expect(supplierSchema.safeParse(ok).success).toBe(true);
    const r = supplierSchema.safeParse({ ...ok, name: "", phone: " ", items: [], termsDays: "21" });
    const msgs = Object.fromEntries((r.error?.issues ?? []).map((i) => [String(i.path[0]), i.message]));
    expect(msgs).toEqual({
      name: "Supplier name and phone are required.",
      phone: "Supplier name and phone are required.",
      items: MSG_ITEMS,
      termsDays: "Choose the payment terms from the list.",
    });
    expect(TERMS.map((t) => t.days)).toEqual([0, 7, 14, 30, 45, 60]);
  });

  it("factory needs a charge for every active bottle type", () => {
    const schema = factorySchema([
      { code: "B20", label: "20L" },
      { code: "B10", label: "10L" },
    ]);
    const base = {
      name: "AquaSeal",
      address: "",
      licence: "",
      contact: "",
      phone: "",
      email: "",
      termsDays: "14",
      active: true,
    };
    expect(schema.safeParse({ ...base, charges: { B20: "60", B10: "35" } }).success).toBe(true);
    const r = schema.safeParse({ ...base, charges: { B20: "60", B10: "" } });
    expect(r.error?.issues.map((i) => [i.path.join("."), i.message])).toEqual([
      ["charges.B10", "Enter the charge per 10L bottle (more than 0)."],
    ]);
  });
});

describe("SuppliersTab", () => {
  it("Accountant sees the list without New / Edit", async () => {
    renderTab([P.PARTNERS_VIEW]);
    expect(await screen.findByText("Lanka Polymer Containers (Pvt) Ltd")).toBeInTheDocument();
    expect(screen.getByText("Empty 20L Bottle")).toBeInTheDocument();
    expect(screen.getByText("30 days")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ New supplier" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
  });

  it("Admin cannot save a supplier without an item", async () => {
    const user = userEvent.setup();
    const fetch = renderTab([P.PARTNERS_VIEW, P.PARTNERS_EDIT, P.MASTERDATA_VIEW]);
    await user.click(await screen.findByRole("button", { name: "+ New supplier" }));
    const dialog = screen.getByRole("dialog", { name: "New supplier" });
    expect(within(dialog).getByLabelText("Supplier code")).toHaveValue("Generated on save");
    expect(await within(dialog).findByLabelText("Manual Bottle Pump")).not.toBeChecked();
    expect(within(dialog).getByLabelText("Payment terms")).toHaveValue("30");

    await user.type(within(dialog).getByLabelText("Name"), "Kelani Plastics");
    await user.type(within(dialog).getByLabelText("Phone"), "011 291 0457");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(await within(dialog).findByText(MSG_ITEMS)).toBeInTheDocument();
    expect(fetch.mock.calls.some(([, init]) => (init as RequestInit | undefined)?.method === "POST")).toBe(
      false,
    );
  });
});
