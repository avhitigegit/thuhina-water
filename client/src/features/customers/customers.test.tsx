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

  it("lists payments in History and fills the Payments and Invoices tabs (M11)", async () => {
    const user = userEvent.setup();
    const { wrap } = setup([P.CUSTOMERS_VIEW, P.SALES_VIEW, P.BILLING_VIEW], (url) => {
      if (url === "/api/customers/8") return { status: 200, body: C0008 };
      if (url === "/api/customers/8/history")
        return {
          status: 200,
          body: [
            {
              at: "2026-08-08T04:00:00Z",
              date: "2026-08-08",
              kind: "PAYMENT",
              description: "Payment RC-00003 – Bank transfer (BOC TRF 88213)",
              by: "shanika.acc",
              byName: "Shanika",
              txnId: null,
              billNo: "RC-00003",
              amount: null,
              paid: 6000,
              balance: 6760,
            },
          ],
        };
      if (url === "/api/payments")
        return {
          status: 200,
          body: {
            items: [
              {
                id: 3,
                receiptNo: "RC-00003",
                date: "2026-08-08",
                customer: { id: 8, code: "C0008", name: "Ceyknit" },
                amount: 6000,
                method: "BANK_TRANSFER",
                methodLabel: "Bank transfer",
                reference: "BOC TRF 88213",
                invoiceNo: "INV-2607-0001",
              },
            ],
            page: 0,
            size: 100,
            total: 1,
          },
        };
      if (url === "/api/invoices")
        return {
          status: 200,
          body: [
            {
              id: 4,
              invoiceNo: "INV-2608-0001",
              period: "2026-08",
              periodLabel: "August 2026",
              amount: 0,
              totalDue: 6760,
            },
          ],
        };
      return { status: 200, body: [] };
    });
    render(wrap(<CustomerPanel customerId={8} onClose={vi.fn()} />));
    const panel = await screen.findByRole("dialog", { name: "Ceyknit Garments (Pvt) Ltd" });
    const hist = (await within(panel).findByText(/Payment RC-00003/)).closest("tr") as HTMLElement;
    expect(hist).toHaveTextContent("08/08/2026");
    expect(hist).toHaveTextContent("Rs. 6,000.00");
    // A receipt number is not a bill link.
    expect(within(hist).queryByRole("link")).not.toBeInTheDocument();

    await user.click(within(panel).getByRole("tab", { name: "Payments" }));
    const pay = (await within(panel).findByRole("button", { name: "RC-00003" })).closest("tr") as HTMLElement;
    expect(pay).toHaveTextContent("BOC TRF 88213");
    expect(pay).toHaveTextContent("for INV-2607-0001");

    await user.click(within(panel).getByRole("tab", { name: "Invoices" }));
    expect(await within(panel).findByRole("link", { name: "INV-2608-0001" })).toHaveAttribute(
      "href",
      "/billing?invoice=4",
    );
    expect(within(panel).getByText("August 2026").closest("tr")).toHaveTextContent("Rs. 6,760.00");
  });

  it("hides Payments and Invoices from roles that do not see Billing", async () => {
    const { wrap } = setup([P.CUSTOMERS_VIEW], (url) =>
      url === "/api/customers/8" ? { status: 200, body: C0008 } : { status: 200, body: [] },
    );
    render(wrap(<CustomerPanel customerId={8} onClose={vi.fn()} />));
    const panel = await screen.findByRole("dialog", { name: "Ceyknit Garments (Pvt) Ltd" });
    expect(within(panel).getByRole("tab", { name: "History" })).toBeInTheDocument();
    expect(within(panel).queryByRole("tab", { name: "Payments" })).not.toBeInTheDocument();
    expect(within(panel).queryByRole("tab", { name: "Invoices" })).not.toBeInTheDocument();
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
    // A usual quantity is required for at least one bottle type (N-09).
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(
      await within(dialog).findByText("Enter the usual quantity for at least one bottle type."),
    ).toBeInTheDocument();
    expect(posts).toBe(0);
    await user.type(within(dialog).getByLabelText("Usual 20L per delivery"), "2");
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
      usualQty: { B20: 2 },
      payType: "CASH",
      areaId: 1,
      customerTypeId: 1,
    });
    expect(bodies[1].allowDuplicatePhone).toBe(true);
  });

  it("offers a brand choice when more than one old-bottle brand is accepted for the size (N-03)", async () => {
    const user = userEvent.setup();
    const { fetch, wrap } = setup(
      [P.CUSTOMERS_VIEW, P.CUSTOMERS_EDIT, P.MASTERDATA_VIEW, P.SALES_EDIT],
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
            body: { date: todayIso(), bottleTypes: [], customerTypes: [], deposits: [], water: [] },
          };
        if (url === "/api/old-bottle-brands")
          return {
            status: 200,
            body: [
              { id: 1, name: "American Water", bottleTypeCode: "B20", active: true, version: 0 },
              { id: 2, name: "Test Brand", bottleTypeCode: "B20", active: true, version: 0 },
            ],
          };
        if (url === "/api/customers/register/preview")
          return {
            status: 200,
            body: {
              lines: [],
              water: 700,
              deposit: 2000,
              amount: 2700,
              paid: 2700,
              credit: { balance: 0, after: 0, limit: 0, exceeds: false },
              problems: [],
            },
          };
        if (url === "/api/customers/register" && init?.method === "POST")
          return { status: 201, body: { customer: { ...C0008, id: 41, code: "C0041" }, agreedPricesSet: 0 } };
        return { status: 200, body: [] };
      },
    );
    render(wrap(<CustomerDialog customerId={null} onClose={vi.fn()} />));
    const dialog = await screen.findByRole("dialog", { name: "New customer" });
    await within(dialog).findByRole("option", { name: "Nugegoda" });
    expect(await within(dialog).findByRole("combobox", { name: "Old bottle brand" })).toHaveValue("1");
    expect(within(dialog).getByLabelText("Old bottles handed in")).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText("Name"), "Nimali Silva");
    await user.type(within(dialog).getByLabelText("Phone"), "075 882 1043");
    await user.type(within(dialog).getByLabelText("Address"), "5, Lake Road");
    await user.selectOptions(within(dialog).getByLabelText("Area"), "1");
    await user.type(within(dialog).getByLabelText("Usual 20L per delivery"), "2");
    await user.selectOptions(within(dialog).getByLabelText("Old bottle brand"), "2");
    await user.clear(within(dialog).getByLabelText("Old bottles handed in"));
    await user.type(within(dialog).getByLabelText("Old bottles handed in"), "1");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await vi.waitFor(() =>
      expect(
        fetch.mock.calls.some(
          ([u, i]) => u.split("?")[0] === "/api/customers/register" && i?.method === "POST",
        ),
      ).toBe(true),
    );
    const body = JSON.parse(
      String(
        fetch.mock.calls.find(
          ([u, i]) => u.split("?")[0] === "/api/customers/register" && i?.method === "POST",
        )![1]!.body,
      ),
    );
    expect(body).toMatchObject({ oldBottles: 1, oldBrandId: 2 });
  });

  it("registers from an accepted quotation with its details and quoted price, linked to it (M16)", async () => {
    const user = userEvent.setup();
    const { fetch, wrap } = setup(
      [P.CUSTOMERS_VIEW, P.CUSTOMERS_EDIT, P.CUSTOMER_PRICES_EDIT, P.MASTERDATA_VIEW],
      (url, init) => {
        if (url === "/api/areas")
          return {
            status: 200,
            body: [{ id: 2, name: "Maharagama", active: true, activeCustomers: 3, version: 0 }],
          };
        if (url === "/api/customer-types")
          return {
            status: 200,
            body: [
              { id: 1, name: "Household", description: null, active: true, activeCustomers: 3, version: 0 },
              { id: 3, name: "Office", description: null, active: true, activeCustomers: 3, version: 0 },
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
              date: todayIso(),
              bottleTypes: [],
              customerTypes: [],
              deposits: [],
              water: [
                {
                  key: "k",
                  kind: "WATER",
                  bottleTypeCode: "B20",
                  customerTypeId: 3,
                  current: { entryId: 1, price: 320, effectiveFrom: "2026-07-01" },
                  next: null,
                  confirmed: false,
                },
              ],
            },
          };
        if (url === "/api/customers/register" && init?.method === "POST")
          return { status: 201, body: { customer: { ...C0008, id: 42, code: "C0042" }, agreedPricesSet: 1 } };
        return { status: 200, body: [] };
      },
    );
    render(
      wrap(
        <CustomerDialog
          customerId={null}
          prefill={{
            quotationId: 7,
            name: "Divisional Secretariat – Maharagama",
            phone: "011 285 0153",
            address: "High Level Road, Maharagama",
            areaId: 2,
            customerTypeId: 3,
            notes: "From quotation QT-2026-001 · contact Mrs. W.A. Dilrukshi",
            agreedPrices: { B20: 300 },
            agreedReason: "Quotation QT-2026-001 accepted",
            usualQty: { B20: 10 },
          }}
          onClose={vi.fn()}
        />,
      ),
    );
    const dialog = await screen.findByRole("dialog", { name: "New customer" });
    await within(dialog).findByRole("option", { name: "Maharagama" });
    expect(within(dialog).getByLabelText("Name")).toHaveValue("Divisional Secretariat – Maharagama");
    expect(within(dialog).getByLabelText("Customer type")).toHaveValue("3");
    expect(within(dialog).getByLabelText("20L agreed price (Rs.)")).toHaveValue(300);
    expect(within(dialog).getByLabelText("Usual 20L per delivery")).toHaveValue(10);
    expect(within(dialog).getByLabelText("Reason for the agreed price")).toHaveValue(
      "Quotation QT-2026-001 accepted",
    );
    expect(within(dialog).getByLabelText("Notes")).toHaveValue(
      "From quotation QT-2026-001 · contact Mrs. W.A. Dilrukshi",
    );
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await vi.waitFor(() =>
      expect(
        fetch.mock.calls.some(([u, i]) => u.startsWith("/api/customers/register") && i?.method === "POST"),
      ).toBe(true),
    );
    const body = JSON.parse(
      String(
        fetch.mock.calls.find(
          ([u, i]) => u.startsWith("/api/customers/register") && i?.method === "POST",
        )![1]!.body,
      ),
    );
    expect(body).toMatchObject({
      fromQuotationId: 7,
      customerTypeId: 3,
      areaId: 2,
      usualQty: { B20: 10 },
      agreedPrices: { B20: 300 },
      agreedReason: "Quotation QT-2026-001 accepted",
    });
  });
});
