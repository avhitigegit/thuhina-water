import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccessProvider } from "@/features/auth/access";
import type { CustomerDetail, MeResponse } from "@/lib/api/types";
import { todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { CustomerDialog } from "./CustomerDialog";
import { CustomerPanel } from "./CustomerPanel";
import { isoWeekday, nextDeliveryAfter } from "./labels";
import { customerSchema, MSG_PHONE, MSG_REQUIRED } from "./schemas";

const C0008: CustomerDetail = {
  id: 8,
  code: "C0008",
  name: "Ceyknit Garments (Pvt) Ltd",
  address: "Lot 12, BOI Zone, Horana Road",
  areaId: 10,
  areaName: "Piliyandala",
  phone: "038 224 1180",
  customerTypeId: 4,
  typeName: "Factory",
  deliveryDay: 2,
  cycleWeeks: 1,
  nextDeliveryDate: "2099-10-13",
  usualQty: { B20: 10 },
  payType: "MONTHLY_BILL",
  creditLimit: 120000,
  termsDays: 30,
  registeredOn: "2023-06-12",
  status: "ACTIVE",
  statusDate: null,
  statusReason: null,
  notes: null,
  whatsappOptIn: false,
  whatsappNumber: null,
  bottles: [{ bottleTypeCode: "B20", label: "20L", held: 10, toCollect: 2, owed: 0 }],
  outstanding: 12760,
  overLimit: false,
  overdue: false,
  prices: [
    {
      bottleTypeCode: "B20",
      label: "20L",
      price: 270,
      agreed: true,
      standard: 300,
      next: null,
      bottlesPerMonth: 43,
      fillingCharge: 60,
    },
  ],
  version: 3,
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

type Handler = (url: string, init?: RequestInit) => { status: number; body: unknown };

function setup(permissions: string[], handler: Handler) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(async (url, init) => {
    const r = handler(url.split("?")[0], init);
    return new Response(JSON.stringify(r.body), { status: r.status });
  });
  vi.stubGlobal("fetch", fetch);
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrap = (children: ReactNode) => (
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(permissions), viewOnly: false }}>{children}</AccessProvider>
    </QueryClientProvider>
  );
  return { fetch, wrap };
}

afterEach(() => vi.unstubAllGlobals());

describe("delivery dates", () => {
  it("first delivery is the next delivery day after the registration date", () => {
    expect(isoWeekday("2026-10-09")).toBe(5); // Friday
    expect(nextDeliveryAfter("2026-10-09", 1)).toBe("2026-10-12"); // Friday → next Monday
    expect(nextDeliveryAfter("2026-10-12", 1)).toBe("2026-10-19"); // on a Monday → the following Monday
    expect(nextDeliveryAfter("2026-12-31", 4)).toBe("2027-01-07");
  });
});

describe("customer form rules", () => {
  const base = {
    name: "Saman",
    phone: "077 123 4567",
    address: "12, Road",
    areaId: "1",
    customerTypeId: "1",
    registeredOn: "2026-10-09",
    deliveryDay: "1",
    cycleWeeks: "1",
    nextDeliveryDate: "2026-10-12",
    usualQty: { B20: "2" },
    payType: "CASH" as const,
    creditLimit: "",
    termsDays: "30",
    agreedPrices: {},
    agreedReason: "",
    bottlesGiven: { B20: "2" },
    oldBottles: "0",
    whatsappOptIn: false,
    whatsappNumber: "",
    notes: "",
  };
  const schema = customerSchema(true, "2026-10-11", ["B20"]);
  const msgs = (v: object) =>
    Object.fromEntries((schema.safeParse(v).error?.issues ?? []).map((i) => [i.path.join("."), i.message]));

  it("accepts a valid customer", () => {
    expect(schema.safeParse(base).success).toBe(true);
  });

  it("uses the prototype messages", () => {
    expect(msgs({ ...base, name: " ", areaId: "" })).toMatchObject({
      name: MSG_REQUIRED,
      areaId: MSG_REQUIRED,
    });
    expect(msgs({ ...base, phone: "77 123 4567" })).toMatchObject({ phone: MSG_PHONE });
    expect(msgs({ ...base, nextDeliveryDate: "2026-10-13" })).toMatchObject({
      nextDeliveryDate: "Next delivery date 13/10/2026 is a Tuesday, but the delivery day is Monday.",
    });
    expect(msgs({ ...base, payType: "CREDIT", termsDays: "21" })).toMatchObject({
      creditLimit: "Credit and Monthly bill customers need a credit limit.",
      termsDays: "Choose the payment terms from the list.",
    });
    expect(msgs({ ...base, registeredOn: "2026-10-12" })).toMatchObject({
      registeredOn: "Registration date cannot be in the future.",
    });
    expect(msgs({ ...base, agreedPrices: { B20: "270" } })).toMatchObject({
      agreedReason: "Enter the reason for the agreed price (e.g. about 3,500 bottles a month).",
    });
  });
});

describe("CustomerPanel", () => {
  it("shows balances, delivery and the agreed price; the Accountant gets no edit actions", async () => {
    const { wrap } = setup(
      [P.CUSTOMERS_VIEW, P.BILLING_VIEW, P.BILLING_EDIT, P.CUSTOMER_PRICES_VIEW],
      (url) => (url === "/api/customers/8" ? { status: 200, body: C0008 } : { status: 200, body: [] }),
    );
    render(wrap(<CustomerPanel customerId={8} onClose={vi.fn()} onEdit={vi.fn()} />));
    const panel = await screen.findByRole("dialog", { name: "Ceyknit Garments (Pvt) Ltd" });
    expect(await within(panel).findByText("Tuesday, every week · next 13/10/2099")).toBeInTheDocument();
    expect(within(panel).getByText("Empties to collect (20L)").parentElement).toHaveClass("bal", "hl");
    expect(within(panel).getAllByText("agreed").length).toBeGreaterThan(0);
    expect(within(panel).getByText("Agreed price")).toBeInTheDocument();
    // Accountant: billing actions yes, customer edits no.
    expect(within(panel).getByRole("link", { name: "Receive payment" })).toBeInTheDocument();
    for (const name of ["Edit", "Deactivate", "Prices", "Enter bill"]) {
      expect(within(panel).queryByRole("button", { name })).not.toBeInTheDocument();
      expect(within(panel).queryByRole("link", { name })).not.toBeInTheDocument();
    }
  });
});

describe("CustomerDialog", () => {
  it("fills the next delivery from the day and saves anyway after a duplicate phone warning", async () => {
    const user = userEvent.setup();
    const today = todayIso();
    let posts = 0;
    const { fetch, wrap } = setup(
      [P.CUSTOMERS_VIEW, P.CUSTOMERS_EDIT, P.CUSTOMER_PRICES_EDIT, P.MASTERDATA_VIEW],
      (url, init) => {
        if (url === "/api/areas")
          return {
            status: 200,
            body: [{ id: 1, name: "Nugegoda", active: true, activeCustomers: 3, version: 0 }],
          };
        if (url === "/api/customer-types")
          return {
            status: 200,
            body: [
              { id: 1, name: "Household", description: null, active: true, activeCustomers: 3, version: 0 },
            ],
          };
        if (url === "/api/bottle-types")
          return {
            status: 200,
            body: [
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
          };
        if (url === "/api/prices/current")
          return {
            status: 200,
            body: {
              date: today,
              bottleTypes: [],
              customerTypes: [],
              deposits: [],
              water: [
                {
                  key: "k",
                  kind: "WATER",
                  bottleTypeCode: "B20",
                  customerTypeId: 1,
                  current: { entryId: 1, price: 350, effectiveFrom: "2026-07-01" },
                  next: null,
                  confirmed: false,
                },
              ],
            },
          };
        if (url === "/api/customers/register" && init?.method === "POST") {
          posts++;
          return posts === 1
            ? {
                status: 422,
                body: {
                  code: "DUP_PHONE",
                  message: "Phone number already used by C0012 – Hiru Salon & Spa.",
                  details: {},
                },
              }
            : { status: 201, body: { customer: { ...C0008, id: 41, code: "C0041" }, agreedPricesSet: 0 } };
        }
        return { status: 200, body: [] };
      },
    );
    render(wrap(<CustomerDialog customerId={null} onClose={vi.fn()} />));
    const dialog = await screen.findByRole("dialog", { name: "New customer" });
    await within(dialog).findByRole("option", { name: "Nugegoda" });

    // Wednesday delivery → next delivery is the next Wednesday after today.
    await user.selectOptions(within(dialog).getByLabelText("Delivery day"), "3");
    expect(within(dialog).getByLabelText("Next delivery")).toHaveValue(
      nextDeliveryAfter(today, 3).split("-").reverse().join("/"),
    );
    expect(within(dialog).getByText("Wednesday", { selector: ".help" })).toBeInTheDocument();
    expect(await within(dialog).findByText(/Standard Household: Rs\. 350\.00/)).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText("Name"), "Nimali Silva");
    await user.type(within(dialog).getByLabelText("Phone"), "075 882 1043");
    await user.type(within(dialog).getByLabelText("Address"), "5, Lake Road");
    await user.selectOptions(within(dialog).getByLabelText("Area"), "1");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    const confirm = await screen.findByRole("dialog", { name: "Phone already used" });
    expect(
      within(confirm).getByText("Phone number already used by C0012 – Hiru Salon & Spa."),
    ).toBeInTheDocument();
    await user.click(within(confirm).getByRole("button", { name: "Save anyway" }));
    await vi.waitFor(() => expect(posts).toBe(2));
    const bodies = fetch.mock.calls
      .filter(([u, i]) => u.startsWith("/api/customers/register") && i?.method === "POST")
      .map(([, i]) => JSON.parse(String(i?.body)));
    expect(bodies[0]).toMatchObject({
      allowDuplicatePhone: false,
      deliveryDay: 3,
      payType: "CASH",
      areaId: 1,
      customerTypeId: 1,
    });
    expect(bodies[1].allowDuplicatePhone).toBe(true);
  });
});
