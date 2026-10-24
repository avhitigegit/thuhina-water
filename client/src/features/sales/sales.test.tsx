import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccessProvider } from "@/features/auth/access";
import { CustomerDialog } from "@/features/customers/CustomerDialog";
import { findMatch } from "@/features/customers/CustomerPicker";
import type { CustomerDetail, MeResponse, SaleLine, SalePreview, TxnDetail } from "@/lib/api/types";
import { todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { BillDialog } from "./BillDialog";
import { lineText } from "./labels";
import { FirstPurchaseDialog } from "./SaleDialogs";

const ADMIN = [P.SALES_VIEW, P.SALES_EDIT, P.SALES_APPROVE_OVER_LIMIT, P.CUSTOMERS_VIEW, P.CUSTOMERS_EDIT];

const C0041: CustomerDetail = {
  id: 41,
  code: "C0041",
  name: "Nimali Silva",
  address: "5, Lake Road",
  areaId: 1,
  areaName: "Nugegoda",
  phone: "075 882 1043",
  customerTypeId: 1,
  typeName: "Household",
  deliveryDay: 1,
  cycleWeeks: 1,
  nextDeliveryDate: "2099-10-12",
  usualQty: { B20: 3 },
  payType: "CREDIT",
  creditLimit: 3000,
  termsDays: 30,
  registeredOn: "2026-10-11",
  status: "ACTIVE",
  statusDate: null,
  statusReason: null,
  notes: null,
  whatsappOptIn: false,
  whatsappNumber: null,
  bottles: [{ bottleTypeCode: "B20", label: "20L", held: 0, toCollect: 0, owed: 0 }],
  outstanding: 0,
  overLimit: false,
  overdue: false,
  prices: [
    {
      bottleTypeCode: "B20",
      label: "20L",
      price: 350,
      agreed: false,
      standard: 350,
      next: null,
      bottlesPerMonth: 13,
      fillingCharge: 60,
    },
  ],
  version: 0,
};

const LOOKUP = {
  id: 41,
  code: "C0041",
  name: "Nimali Silva",
  phone: "075 882 1043",
  areaName: "Nugegoda",
  status: "ACTIVE",
};

const BOTTLES = [
  {
    code: "B20",
    name: "20L Bottle",
    litres: 20,
    active: true,
    deposit: 1000,
    inCirculation: null,
    version: 0,
  },
];
const BRANDS = [
  {
    id: 1,
    name: "American Water",
    bottleTypeCode: "B20",
    bottleTypeName: "20L Bottle",
    note: null,
    active: true,
    addedOn: "2026-01-01",
    takenIn: 0,
    version: 0,
  },
];

function line(over: Partial<SaleLine>): SaleLine {
  return {
    bottleTypeCode: "B20",
    productCode: null,
    label: "20L",
    filled: 0,
    empties: 0,
    unitPrice: null,
    agreedPrice: false,
    depositUnit: null,
    depositCount: 0,
    oldBottles: 0,
    oldBrandId: null,
    newBottles: 0,
    owedAdded: 0,
    owedCleared: 0,
    toCollectAdded: 0,
    toCollectCleared: 0,
    damaged: 0,
    qty: 0,
    unitCost: null,
    lineAmount: 0,
    ...over,
  };
}

/** 3 × 20L with 1 American Water bottle, Household: water 3 × 350 + deposit 2 × 1,000 = Rs. 3,050 (reference). */
const FIRST_LINE = line({
  filled: 3,
  newBottles: 3,
  unitPrice: 350,
  depositUnit: 1000,
  depositCount: 2,
  oldBottles: 1,
  oldBrandId: 1,
  lineAmount: 3050,
});

const PREVIEW: SalePreview = {
  lines: [FIRST_LINE],
  water: 1050,
  deposit: 2000,
  product: 0,
  amount: 3050,
  paid: 0,
  after: [
    {
      bottleTypeCode: "B20",
      label: "20L",
      held: 3,
      toCollect: 0,
      owed: 0,
      heldChange: 3,
      toCollectChange: 0,
      owedChange: 0,
    },
  ],
  credit: { balance: 0, after: 3050, limit: 3000, exceeds: true },
  problems: [],
  warnings: [],
};

function bill(over: Partial<TxnDetail>): TxnDetail {
  return {
    id: 13,
    billNo: "B000013",
    paperBillNo: null,
    kind: "FIRST_PURCHASE",
    kindLabel: "First purchase",
    date: "2026-10-11",
    customerId: 41,
    customerCode: "C0041",
    customerName: "Nimali Silva",
    customerAddress: "5, Lake Road",
    customerPhone: "075 882 1043",
    payType: "CREDIT",
    lines: [FIRST_LINE],
    water: 1050,
    deposit: 2000,
    product: 0,
    productCost: 0,
    amount: 3050,
    paid: 0,
    payMethod: null,
    note: null,
    phoneConfirmed: false,
    damageMode: null,
    settleMethod: null,
    creditOverrideBy: null,
    creditOverrideByName: null,
    reversalOf: null,
    reversedBy: null,
    effects: [{ item: "20L", place: "Filled in store", change: -3 }],
    createdBy: "dilini.admin",
    createdByName: "Dilini Admin",
    createdAt: "2026-10-11T04:30:00Z",
    ...over,
  };
}

type Handler = (url: string, init?: RequestInit) => { status: number; body: unknown } | undefined;

function setup(permissions: string[], handler: Handler) {
  const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(async (url, init) => {
    const path = url.split("?")[0];
    const r =
      handler(path, init) ??
      (path === "/api/bottle-types"
        ? { status: 200, body: BOTTLES }
        : path === "/api/old-bottle-brands"
          ? { status: 200, body: BRANDS }
          : path === "/api/customers/41"
            ? { status: 200, body: C0041 }
            : { status: 200, body: [] });
    return new Response(JSON.stringify(r.body), { status: r.status });
  });
  vi.stubGlobal("fetch", fetch);
  const me = {
    user: { id: 1, username: "dilini.admin", fullName: "Dilini Admin", role: "ADMIN", roleName: "Admin" },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions,
    menu: [],
  } as MeResponse;
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrap = (children: ReactNode) => (
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me, viewOnly: false }}>{children}</AccessProvider>
    </QueryClientProvider>
  );
  const posts = (path: string) =>
    fetch.mock.calls
      .filter(([u, i]) => u.split("?")[0] === path && i?.method === "POST")
      .map(([, i]) => JSON.parse(String(i?.body)));
  return { fetch, wrap, posts };
}

afterEach(() => vi.unstubAllGlobals());

describe("words", () => {
  it("describes bill lines as in the prototype", () => {
    expect(lineText(FIRST_LINE)).toBe("20L: 3 filled @ Rs. 350.00 2 deposit(s) 1 old bottle(s) in");
    expect(
      lineText(
        line({ filled: 4, empties: 3, unitPrice: 330, agreedPrice: true, owedAdded: 1, lineAmount: 1320 }),
      ),
    ).toBe("20L: 4 filled @ Rs. 330.00 (agreed price) 3 empties back 1 owed");
    expect(
      lineText(
        line({ bottleTypeCode: null, productCode: "P01", label: "Water dispenser", qty: 1, unitPrice: 8500 }),
      ),
    ).toBe("1 × Water dispenser @ Rs. 8,500.00");
  });

  it("customer picker understands codes, numbers, phones and the full label", () => {
    const rows = [
      LOOKUP,
      { ...LOOKUP, id: 3, code: "C0003", name: "Dilshan Fernando", phone: "077 123 4567" },
    ];
    expect(findMatch("C0041", rows)?.id).toBe(41);
    expect(findMatch("3", rows)?.id).toBe(3);
    expect(findMatch("077 1234567", rows)?.id).toBe(3);
    expect(findMatch("C0041 – Nimali Silva – 075 882 1043", rows)?.id).toBe(41);
    expect(findMatch("Nimali", rows)).toBeNull();
  });
});

describe("First purchase", () => {
  it("shows the live calculation and balances after, and the Admin approves the credit limit", async () => {
    const user = userEvent.setup();
    let saves = 0;
    const { wrap, posts } = setup(ADMIN, (url, init) => {
      if (url === "/api/sales/first-purchase/preview") return { status: 200, body: PREVIEW };
      if (url === "/api/sales/first-purchase" && init?.method === "POST") {
        saves++;
        return saves === 1
          ? {
              status: 422,
              body: {
                code: "CREDIT_LIMIT",
                message:
                  "Nimali Silva would exceed the credit limit (Rs. 3,000.00). Balance after this bill: Rs. 3,050.00. Admin approval is required.",
                details: { limit: 3000, balance: 0, after: 3050 },
              },
            }
          : { status: 201, body: { txn: bill({}), schedule: null } };
      }
    });
    const onClose = vi.fn();
    render(wrap(<FirstPurchaseDialog initialCustomer={LOOKUP} onClose={onClose} />));
    const dialog = await screen.findByRole("dialog", { name: "First purchase" });
    expect(within(dialog).getByLabelText("Customer")).toHaveValue("C0041 – Nimali Silva – 075 882 1043");
    expect(within(dialog).getByLabelText("Bill no.")).toHaveValue("Generated on save");

    const qty = within(dialog).getByLabelText("Bottles given");
    await user.clear(qty);
    await user.type(qty, "3");
    const old = within(dialog).getByLabelText("Old bottles handed in");
    await user.clear(old);
    await user.type(old, "1");

    expect(await within(dialog).findByText("Water 3 × Rs. 350.00")).toBeInTheDocument();
    expect(within(dialog).getByText("Deposit 2 × Rs. 1,000.00")).toBeInTheDocument();
    expect(within(dialog).getByText("Old bottles handed in (no deposit)")).toBeInTheDocument();
    expect(within(dialog).getByText("Rs. 3,050.00", { selector: "b.total" })).toBeInTheDocument();
    // Customer card "after this bill": 3 held (+3), balance + Rs. 3,050 over the limit.
    const held = within(dialog).getByTestId("bal-B20-held");
    expect(held).toHaveTextContent("3");
    expect(held).toHaveTextContent("+3 (was 0)");
    expect(within(dialog).getByTestId("bal-outstanding")).toHaveTextContent("Rs. 3,050.00");
    // Credit customer: nothing paid unless typed.
    expect(within(dialog).getByLabelText("Cash paid (Rs.)")).toHaveValue(0);

    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    const approve = await screen.findByRole("dialog", { name: "Credit limit exceeded" });
    expect(within(approve).getByText(/As Admin you can approve this sale/)).toBeInTheDocument();
    await user.click(within(approve).getByRole("button", { name: "Approve and save" }));
    await vi.waitFor(() => expect(onClose).toHaveBeenCalled());

    const bodies = posts("/api/sales/first-purchase");
    expect(bodies).toHaveLength(2);
    expect(bodies[0]).toMatchObject({
      customerId: 41,
      date: todayIso(),
      bottleTypeCode: "B20",
      qty: 3,
      oldBottles: 1,
      oldBrandId: 1,
      paid: 0,
      approveOverLimit: false,
    });
    expect(bodies[1].approveOverLimit).toBe(true);
  });

  it("tells other users that only an Admin can approve", async () => {
    const user = userEvent.setup();
    const { wrap } = setup([P.SALES_VIEW, P.SALES_EDIT, P.CUSTOMERS_VIEW], (url, init) => {
      if (url === "/api/sales/first-purchase/preview") return { status: 200, body: PREVIEW };
      if (url === "/api/sales/first-purchase" && init?.method === "POST")
        return {
          status: 422,
          body: {
            code: "CREDIT_LIMIT",
            message: "Over the limit.",
            details: { limit: 3000, balance: 0, after: 3050 },
          },
        };
    });
    render(wrap(<FirstPurchaseDialog initialCustomer={LOOKUP} onClose={vi.fn()} />));
    const dialog = await screen.findByRole("dialog", { name: "First purchase" });
    await within(dialog).findByText("Water 3 × Rs. 350.00");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    const approve = await screen.findByRole("dialog", { name: "Credit limit exceeded" });
    expect(
      within(approve).getByText("Only an Admin can approve a sale over the credit limit."),
    ).toBeInTheDocument();
    expect(within(approve).queryByRole("button", { name: "Approve and save" })).not.toBeInTheDocument();
  });

  it("puts a field message from the server under its field", async () => {
    const user = userEvent.setup();
    const { wrap } = setup(ADMIN, (url, init) => {
      if (url === "/api/sales/first-purchase/preview")
        return { status: 200, body: { ...PREVIEW, credit: null } };
      if (url === "/api/sales/first-purchase" && init?.method === "POST")
        return {
          status: 400,
          body: {
            code: "VALIDATION",
            message: "Check the highlighted fields.",
            details: {
              fields: { oldBottles: "Old bottles handed in cannot be more than the bottles given." },
            },
          },
        };
    });
    render(wrap(<FirstPurchaseDialog initialCustomer={LOOKUP} onClose={vi.fn()} />));
    const dialog = await screen.findByRole("dialog", { name: "First purchase" });
    await within(dialog).findByText("Water 3 × Rs. 350.00");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(
      await within(dialog).findByText("Old bottles handed in cannot be more than the bottles given."),
    ).toHaveClass("error");
    expect(within(dialog).getByLabelText("Old bottles handed in")).toHaveClass("invalid");
  });
});

describe("Bill details", () => {
  it("reverses a bill with a reason", async () => {
    const user = userEvent.setup();
    const { wrap, posts } = setup(ADMIN, (url, init) => {
      if (url === "/api/sales/txns/13") return { status: 200, body: bill({}) };
      if (url === "/api/sales/txns/13/reverse" && init?.method === "POST")
        return {
          status: 201,
          body: { txn: bill({ id: 14, billNo: "B000014", kind: "REVERSAL", amount: -3050 }), schedule: null },
        };
    });
    const onClose = vi.fn();
    render(wrap(<BillDialog billId={13} onClose={onClose} />));
    const dialog = await screen.findByRole("dialog", { name: "Bill B000013" });
    expect(
      await within(dialog).findByText("20L: 3 filled @ Rs. 350.00 2 deposit(s) 1 old bottle(s) in"),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "Nimali Silva" })).toHaveAttribute(
      "href",
      "/master-data/customers?id=C0041",
    );
    expect(within(dialog).getByRole("button", { name: "Print bill" })).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Reverse this bill" }));
    const ask = await screen.findByRole("dialog", { name: "Reverse bill B000013?" });
    await user.click(within(ask).getByRole("button", { name: "Reverse" }));
    expect(within(ask).getByText("Enter a reason.")).toBeInTheDocument();
    await user.type(
      within(ask).getByLabelText("Reason for the correction"),
      "Entered for the wrong customer",
    );
    await user.click(within(ask).getByRole("button", { name: "Reverse" }));
    await vi.waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(posts("/api/sales/txns/13/reverse")).toEqual([{ reason: "Entered for the wrong customer" }]);
  });

  it("shows a reversed bill without the Reverse button, and a view-only user cannot reverse", async () => {
    const { wrap } = setup([P.SALES_VIEW], (url) =>
      url === "/api/sales/txns/13"
        ? {
            status: 200,
            body: bill({
              creditOverrideBy: "dilini.admin",
              creditOverrideByName: "Dilini Admin",
              reversedBy: {
                id: 14,
                billNo: "B000014",
                date: "2026-10-11",
                note: "Entered for the wrong customer",
              },
            }),
          }
        : undefined,
    );
    render(wrap(<BillDialog billId={13} onClose={vi.fn()} />));
    const dialog = await screen.findByRole("dialog", { name: "Bill B000013" });
    expect(await within(dialog).findByText(/Entered for the wrong customer/)).toBeInTheDocument();
    expect(within(dialog).getByText("Over the credit limit – approved by Dilini Admin")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Reverse this bill" })).not.toBeInTheDocument();
  });
});

describe("Registration with bottles", () => {
  it("shows the bills of the bottles given and saves them with the customer", async () => {
    const user = userEvent.setup();
    const today = todayIso();
    const { wrap, posts } = setup(ADMIN, (url, init) => {
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
      if (url === "/api/customers/register/preview")
        return {
          status: 200,
          body: {
            lines: [FIRST_LINE],
            water: 1050,
            deposit: 2000,
            amount: 3050,
            paid: 0,
            credit: { balance: 0, after: 3050, limit: 10000, exceeds: false },
            problems: [],
          },
        };
      if (url === "/api/customers/register" && init?.method === "POST")
        return { status: 201, body: { customer: C0041, agreedPricesSet: 0, bills: [bill({})] } };
    });
    const onSaved = vi.fn();
    render(wrap(<CustomerDialog customerId={null} onClose={vi.fn()} onSaved={onSaved} />));
    const dialog = await screen.findByRole("dialog", { name: "New customer" });
    await within(dialog).findByRole("option", { name: "Nugegoda" });
    // 2 × 20L by default (prototype); give 3 and 1 old American Water bottle.
    const given = await within(dialog).findByLabelText("Filled 20L bottles given");
    expect(given).toHaveValue(2);
    await user.clear(given);
    await user.type(given, "3");
    await user.clear(await within(dialog).findByLabelText("Old American Water bottles handed in"));
    await user.type(within(dialog).getByLabelText("Old American Water bottles handed in"), "1");

    const summary = await within(dialog).findByTestId("registration-summary");
    expect(summary).toHaveTextContent(
      "3 × 20L: water Rs. 1,050.00 + deposit 2 × Rs. 1,000.00 (1 old bottle – no deposit)",
    );
    expect(summary).toHaveTextContent("Total Rs. 3,050.00 · paid Rs. 3,050.00");
    // Cash customer: the total is paid unless changed.
    expect(within(dialog).getByLabelText("Cash paid now (Rs.)")).toHaveValue(3050);

    await user.type(within(dialog).getByLabelText("Name"), "Nimali Silva");
    await user.type(within(dialog).getByLabelText("Phone"), "075 882 1043");
    await user.type(within(dialog).getByLabelText("Address"), "5, Lake Road");
    await user.selectOptions(within(dialog).getByLabelText("Area"), "1");
    await user.type(within(dialog).getByLabelText("Usual 20L per delivery"), "3");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));

    const done = await screen.findByRole("dialog", { name: "Customer registered" });
    expect(within(done).getByText(/3 × 20L – bill B000013 \(Rs\. 3,050\.00\)/)).toBeInTheDocument();
    expect(within(done).getByRole("button", { name: "Print bill" })).toBeInTheDocument();
    expect(posts("/api/customers/register")[0]).toMatchObject({
      bottlesGiven: { B20: 3 },
      oldBottles: 1,
      paid: 3050,
      approveOverLimit: false,
    });
    // The footer button (the × in the header is also called "Close").
    await user.click(within(done).getAllByRole("button", { name: "Close" }).at(-1)!);
    expect(onSaved).toHaveBeenCalledWith(expect.objectContaining({ code: "C0041" }));
  });
});
