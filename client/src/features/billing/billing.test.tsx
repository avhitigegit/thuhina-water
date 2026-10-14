import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PageActionsSlotContext } from "@/components/layout/PageActions";
import { Toaster } from "@/components/shared/Toaster";
import { AccessProvider } from "@/features/auth/access";
import type {
  AccountList,
  AccountRow,
  AccountSummary,
  InvoicePreview,
  InvoiceView,
  MeResponse,
  PaymentSaved,
} from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { Billing } from "./Billing";
import { generatedText } from "./InvoicesTab";
import { paymentProblem, startOfLastMonth } from "./PaymentDialogs";

let search = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));

const TODAY = "2026-10-14";

type Role = "ADMIN" | "ACCOUNTANT" | "VIEWER";

function me(role: Role): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role, roleName: role },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions:
      role === "VIEWER" ? [P.BILLING_VIEW] : [P.BILLING_VIEW, P.BILLING_EDIT, P.CUSTOMERS_VIEW, P.SALES_VIEW],
    menu: [],
  } as MeResponse;
}

function account(over: Partial<AccountRow>): AccountRow {
  return {
    customerId: 12,
    code: "C0012",
    name: "Hiru Salon",
    phone: "077 555 1212",
    payType: "CREDIT",
    payTypeLabel: "Credit",
    creditLimit: 10000,
    status: "ACTIVE",
    days0to30: 0,
    days31to60: 0,
    days61to90: 0,
    over90: 7900,
    balance: 7900,
    overLimit: false,
    oldest: "2026-06-20",
    ...over,
  };
}

const ACCOUNTS: AccountList = {
  items: [
    account({}),
    account({
      customerId: 8,
      code: "C0008",
      name: "Ceyknit Garments",
      phone: "038 224 1180",
      payType: "MONTHLY_BILL",
      payTypeLabel: "Monthly bill",
      creditLimit: 5000,
      over90: 0,
      days61to90: 6760,
      balance: 6760,
      overLimit: true,
    }),
    account({
      customerId: 30,
      code: "C0030",
      name: "Advance Ltd",
      payType: "CASH",
      payTypeLabel: "Cash",
      creditLimit: 0,
      over90: 0,
      balance: -300,
      oldest: null,
    }),
  ],
  page: 0,
  size: 50,
  total: 3,
  totals: { days0to30: 0, days31to60: 0, days61to90: 6760, over90: 7900, balance: 14360 },
};

const SUMMARY: AccountSummary = {
  totalOutstanding: 14360,
  days0to30: 0,
  days31to60: 0,
  days61to90: 6760,
  over90: 7900,
  customers: 3,
  overLimit: 1,
  asOf: TODAY,
};

const PREVIEW: InvoicePreview = {
  period: "2026-09",
  periodLabel: "September 2026",
  periods: [
    { period: "2026-10", label: "October 2026" },
    { period: "2026-09", label: "September 2026" },
    { period: "2026-08", label: "August 2026" },
    { period: "2026-07", label: "July 2026" },
  ],
  rows: [
    {
      customerId: 8,
      code: "C0008",
      name: "Ceyknit Garments",
      status: "ACTIVE",
      deliveries: 0,
      charges: 0,
      paidOnBills: 0,
      thisMonth: 0,
      opening: 6760,
      payments: 0,
      totalDue: 6760,
      invoice: null,
    },
    {
      customerId: 9,
      code: "C0009",
      name: "Vertex Software",
      status: "ACTIVE",
      deliveries: 4,
      charges: 2800,
      paidOnBills: 700,
      thisMonth: 2100,
      opening: 1000,
      payments: 1000,
      totalDue: 2100,
      invoice: {
        id: 41,
        invoiceNo: "INV-2609-0001",
        period: "2026-09",
        periodLabel: "September 2026",
        totalDue: 2100,
      },
    },
  ],
  toGenerate: 1,
};

const INVOICE: InvoiceView = {
  id: 41,
  invoiceNo: "INV-2609-0001",
  customer: {
    id: 9,
    code: "C0009",
    name: "Vertex Software",
    address: "12 Main St",
    area: "Nugegoda",
    phone: "077 000 0009",
    payType: "MONTHLY_BILL",
    payTypeLabel: "Monthly bill",
    termsDays: 30,
  },
  period: "2026-09",
  periodLabel: "September 2026",
  from: "2026-09-01",
  to: "2026-09-30",
  invoiceDate: "2026-10-01",
  dueDate: "2026-10-31",
  deliveries: 4,
  opening: 1000,
  charges: 2800,
  paidOnBills: 700,
  payments: 1000,
  amount: 2100,
  totalDue: 2100,
  paid: 0,
  lines: [
    {
      date: "2026-09-02",
      billNo: "B000301",
      description: "Water 20L Bottle (left at door)",
      qty: 2,
      unitPrice: 350,
      amount: 700,
    },
    {
      date: "2026-09-09",
      billNo: "B000340",
      description: "Paid on delivery",
      qty: null,
      unitPrice: null,
      amount: -700,
    },
  ],
  createdByName: "Shanika",
};

function saved(amount: number, after: number): PaymentSaved {
  return {
    payment: {
      id: 16,
      receiptNo: "RC-00016",
      date: TODAY,
      customer: { id: 12, code: "C0012", name: "Hiru Salon" },
      amount,
      method: "CHEQUE",
      methodLabel: "Cheque",
      reference: "551203",
      bank: null,
      chequeDate: null,
      invoiceId: null,
      invoiceNo: null,
      note: null,
      balanceBefore: 7900,
      balanceAfter: after,
      partPayment: true,
      createdByName: "U",
      createdAt: "2026-10-14T05:00:00Z",
    },
    balanceNow: after,
    partPayment: true,
    advance: false,
    message: `Payment saved – Rs. 2,000.00 received from Hiru Salon. Balance now Rs. 5,900.00.`,
  };
}

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let calls: { method: string; url: string; path: string; body: unknown }[] = [];
let opened: string[] = [];

function server() {
  return vi.fn<Fetch>(async (url, init) => {
    const [path] = url.split("?");
    const method = init?.method ?? "GET";
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    calls.push({ method, url, path, body });
    const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status });
    if (method === "GET") {
      if (path === "/api/accounts/summary") return json(SUMMARY);
      if (path === "/api/accounts") return json(ACCOUNTS);
      if (path === "/api/accounts/12")
        return json({
          account: ACCOUNTS.items[0],
          invoices: [],
        });
      if (path === "/api/accounts/8")
        return json({
          account: ACCOUNTS.items[1],
          invoices: [
            {
              id: 40,
              invoiceNo: "INV-2608-0001",
              period: "2026-08",
              periodLabel: "August 2026",
              totalDue: 6760,
            },
          ],
        });
      if (path === "/api/payments/recent")
        return json([
          {
            kind: "PAYMENT",
            id: 15,
            ref: "RC-00015",
            date: "2026-09-25",
            at: "2026-09-25T05:00:00Z",
            customerId: 21,
            customerName: "Lanka Bakers",
            how: "Cash",
            amount: 1400,
          },
          {
            kind: "BILL",
            id: 278,
            ref: "B000278",
            date: "2026-09-24",
            at: "2026-09-24T05:00:00Z",
            customerId: null,
            customerName: "Walk-in",
            how: "Cash on delivery – bill B000278",
            amount: 700,
          },
        ]);
      if (path === "/api/invoices/preview") return json(PREVIEW);
      if (path === "/api/invoices/41") return json(INVOICE);
      if (path === "/api/customers/lookup")
        return json([
          {
            id: 12,
            code: "C0012",
            name: "Hiru Salon",
            phone: "077 555 1212",
            areaName: "Maharagama",
            status: "ACTIVE",
          },
        ]);
    }
    if (path === "/api/payments") {
      const b = body as { amount: number };
      return json(saved(b.amount, 7900 - b.amount), 201);
    }
    if (path === "/api/invoices/generate")
      return json({
        period: "2026-09",
        periodLabel: "September 2026",
        created: [{ id: 42, invoiceNo: "INV-2609-0002" }],
        skipped: [
          { customerId: 9, code: "C0009", name: "Vertex", reason: "Invoice already exists" },
          { customerId: 10, code: "C0010", name: "X", reason: "No deliveries and nothing due" },
        ],
      });
    return json({ code: "NOT_FOUND", message: "Not found " + method + " " + path }, 404);
  });
}

function Shell({ children }: { children: ReactNode }) {
  const [slot, setSlot] = useState<HTMLElement | null>(null);
  return (
    <>
      <div ref={setSlot} data-testid="actions" />
      <PageActionsSlotContext.Provider value={slot}>{children}</PageActionsSlotContext.Provider>
    </>
  );
}

function renderPage(role: Role = "ADMIN") {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(role), viewOnly: role === "VIEWER" }}>
        <Shell>
          <Billing today={TODAY} />
        </Shell>
        <Toaster />
      </AccessProvider>
    </QueryClientProvider>,
  );
}

const posted = (path: string) => calls.filter((c) => c.method !== "GET" && c.path === path);

beforeEach(() => {
  calls = [];
  opened = [];
  search = "";
  vi.stubGlobal("fetch", server());
  vi.stubGlobal(
    "open",
    vi.fn((u: string) => {
      opened.push(u);
      return null;
    }),
  );
  window.location.hash = "";
});
afterEach(() => vi.unstubAllGlobals());

describe("billing rules", () => {
  it("checks a payment like the server", () => {
    const ok = { customer: true, date: TODAY, amount: 100, method: "CASH" as const, reference: "" };
    expect(paymentProblem(ok)).toBeNull();
    expect(paymentProblem({ ...ok, customer: false })).toBe("Select a customer.");
    expect(paymentProblem({ ...ok, amount: 0 })).toBe("Enter the amount received.");
    expect(paymentProblem({ ...ok, method: "BANK_TRANSFER" })).toBe("Enter the bank reference.");
    expect(paymentProblem({ ...ok, method: "CHEQUE", reference: " " })).toBe("Enter the cheque number.");
    expect(startOfLastMonth("2026-10-14")).toBe("2026-09-01");
    expect(startOfLastMonth("2027-01-05")).toBe("2026-12-01");
  });

  it("says how many invoices were made and why the others were skipped", () => {
    const r = (reasons: string[], created = 1) => ({
      period: "2026-09",
      periodLabel: "September 2026",
      created: Array.from({ length: created }, () => ({}) as never),
      skipped: reasons.map((reason, i) => ({ customerId: i, code: "C", name: "N", reason })),
    });
    expect(generatedText(r([]))).toBe("1 invoice generated");
    expect(generatedText(r(["No deliveries and nothing due", "No deliveries and nothing due"], 0))).toBe(
      "0 invoices generated, 2 skipped (No deliveries and nothing due)",
    );
    expect(generatedText(r(["Invoice already exists", "No deliveries and nothing due"], 2))).toBe(
      "2 invoices generated, 2 skipped (Invoice already exists: 1; No deliveries and nothing due: 1)",
    );
  });
});

describe("Billing & Payments", () => {
  it("shows the KPIs, the aging table with totals and the recent money", async () => {
    renderPage();
    expect((await screen.findByText("Total outstanding")).nextSibling).toHaveTextContent("Rs. 14,360.00");
    expect(screen.getByText("Over 90 days").closest(".kpi")).toHaveClass("alert");
    const hiru = (await screen.findByRole("button", { name: "Hiru Salon" })).closest("tr") as HTMLElement;
    expect(within(hiru).getByText("7,900")).toHaveStyle({ color: "var(--bad)" });
    expect(within(hiru).getByRole("button", { name: "Receive" })).toBeInTheDocument();
    const ceyknit = screen.getByRole("button", { name: "Ceyknit Garments" }).closest("tr") as HTMLElement;
    expect(within(ceyknit).getByText("6,760")).toHaveStyle({ color: "var(--warn)" });
    expect(ceyknit).toHaveTextContent("over limit");
    const adv = screen.getByRole("button", { name: "Advance Ltd" }).closest("tr") as HTMLElement;
    expect(adv).toHaveTextContent("-Rs. 300.00");
    expect(adv).toHaveTextContent("advance");
    expect(within(adv).queryByRole("button", { name: "Receive" })).not.toBeInTheDocument();
    const foot = screen.getByText("Total").closest("tr") as HTMLElement;
    expect(foot).toHaveTextContent("Rs. 14,360.00");
    expect(await screen.findByText("Cash on delivery – bill B000278")).toBeInTheDocument();
    expect(screen.getByText("RC-00015")).toBeInTheDocument();
  });

  it("filters by payment type and show", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByRole("button", { name: "Hiru Salon" });
    await user.selectOptions(screen.getByLabelText("Payment type"), "MONTHLY_BILL");
    await user.selectOptions(screen.getByLabelText("Show"), "over");
    await waitFor(() =>
      expect(
        calls.some(
          (c) => c.path === "/api/accounts" && /pay=MONTHLY_BILL/.test(c.url) && /show=over/.test(c.url),
        ),
      ).toBe(true),
    );
  });

  it("receives a part payment by cheque and offers the receipt", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = (await screen.findByRole("button", { name: "Hiru Salon" })).closest("tr") as HTMLElement;
    await user.click(within(row).getByRole("button", { name: "Receive" }));
    const dialog = await screen.findByRole("dialog", { name: "Receive payment" });
    const amount = within(dialog).getByLabelText("Amount (Rs.)");
    await waitFor(() => expect(amount).toHaveValue(7900));
    expect(within(dialog).getByTestId("pay-balance")).toHaveTextContent("Rs. 7,900.00");
    await user.clear(amount);
    await user.type(amount, "2000");
    expect(within(dialog).getByTestId("pay-after")).toHaveTextContent("Rs. 5,900.00");
    expect(within(dialog).getByText("Part payment")).toBeInTheDocument();

    await user.selectOptions(within(dialog).getByLabelText("How paid"), "CHEQUE");
    await user.click(within(dialog).getByRole("button", { name: "Save payment" }));
    expect(within(dialog).getByText("Enter the cheque number.")).toBeInTheDocument();
    expect(posted("/api/payments")).toHaveLength(0);

    await user.type(within(dialog).getByLabelText("Cheque number"), "551203");
    await user.type(within(dialog).getByLabelText("Bank"), "People's Bank");
    await user.click(within(dialog).getByRole("button", { name: "Save payment" }));
    await waitFor(() => expect(posted("/api/payments")).toHaveLength(1));
    expect(posted("/api/payments")[0].body).toEqual({
      customerId: 12,
      date: TODAY,
      amount: 2000,
      method: "CHEQUE",
      reference: "551203",
      bank: "People's Bank",
    });
    const done = await screen.findByRole("dialog", { name: "Payment saved" });
    expect(done).toHaveTextContent(
      "Payment saved – Rs. 2,000.00 received from Hiru Salon. Balance now Rs. 5,900.00.",
    );
    expect(done).toHaveTextContent("RC-00016");
    await user.click(within(done).getByRole("button", { name: "Print receipt" }));
    expect(opened).toEqual(["/api/print/receipt/16"]);
  });

  it("links a Monthly bill payment to the latest invoice and shows an advance", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = (await screen.findByRole("button", { name: "Ceyknit Garments" })).closest(
      "tr",
    ) as HTMLElement;
    await user.click(within(row).getByRole("button", { name: "Receive" }));
    const dialog = await screen.findByRole("dialog", { name: "Receive payment" });
    await waitFor(() => expect(within(dialog).getByLabelText("Against invoice")).toHaveValue("40"));
    const amount = within(dialog).getByLabelText("Amount (Rs.)");
    await user.clear(amount);
    await user.type(amount, "7000");
    expect(within(dialog).getByText("Paid in advance – Rs. 240.00 credit balance.")).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Save payment" }));
    await waitFor(() => expect(posted("/api/payments")).toHaveLength(1));
    expect(posted("/api/payments")[0].body).toMatchObject({
      customerId: 8,
      amount: 7000,
      method: "CASH",
      invoiceId: 40,
    });
  });

  it("opens Receive payment for the customer in the link (?pay=C0012)", async () => {
    search = "pay=C0012";
    renderPage();
    const dialog = await screen.findByRole("dialog", { name: "Receive payment" });
    expect(within(dialog).getByLabelText("Customer")).toHaveValue("C0012 – Hiru Salon – 077 555 1212");
    await waitFor(() => expect(within(dialog).getByLabelText("Amount (Rs.)")).toHaveValue(7900));
  });

  it("prints a statement from the start of last month to today", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = (await screen.findByRole("button", { name: "Hiru Salon" })).closest("tr") as HTMLElement;
    await user.click(within(row).getByRole("button", { name: "Statement" }));
    const dialog = await screen.findByRole("dialog", { name: "Statement – Hiru Salon" });
    expect(within(dialog).getByLabelText("From")).toHaveValue("01/09/2026");
    expect(within(dialog).getByLabelText("To")).toHaveValue("14/10/2026");
    await user.click(within(dialog).getByRole("button", { name: "Print / PDF" }));
    expect(opened).toEqual(["/api/print/statement/12?from=2026-09-01&to=2026-10-14"]);
  });

  it("shows the monthly invoices, generates the missing ones and views an invoice", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("tab", { name: "Monthly invoices" }));
    expect(await screen.findByLabelText("Month")).toHaveValue("2026-09");
    const ceyknit = (await screen.findByRole("button", { name: "Ceyknit Garments" })).closest(
      "tr",
    ) as HTMLElement;
    expect(ceyknit).toHaveTextContent("Not generated");
    const vertex = screen.getByRole("button", { name: "Vertex Software" }).closest("tr") as HTMLElement;
    expect(vertex).toHaveTextContent("Generated INV-2609-0001");
    expect(vertex).toHaveTextContent("– Rs. 1,000.00");

    await user.click(screen.getByRole("button", { name: "Generate invoices" }));
    const confirm = await screen.findByRole("dialog", { name: "Generate invoices for September 2026?" });
    expect(confirm).toHaveTextContent("1 Monthly bill customer without an invoice");
    await user.click(within(confirm).getByRole("button", { name: "Generate" }));
    await waitFor(() => expect(posted("/api/invoices/generate")).toHaveLength(1));
    expect(posted("/api/invoices/generate")[0].body).toEqual({ period: "2026-09" });
    expect(
      await screen.findByText(
        "1 invoice generated, 2 skipped (Invoice already exists: 1; No deliveries and nothing due: 1)",
      ),
    ).toBeInTheDocument();

    await user.click(within(vertex).getByRole("button", { name: "View / Print" }));
    const inv = await screen.findByRole("dialog", { name: "Invoice INV-2609-0001" });
    expect(await within(inv).findByText("Water 20L Bottle (left at door)")).toBeInTheDocument();
    expect(within(inv).getByText("-700.00")).toBeInTheDocument();
    expect(within(inv).getByTestId("inv-total")).toHaveTextContent("Rs. 2,100.00");
    expect(inv).toHaveTextContent("VAT not applied (to be confirmed)");
    await user.click(within(inv).getByRole("button", { name: "Print / PDF" }));
    expect(opened).toEqual(["/api/print/invoice/41"]);

    await user.selectOptions(screen.getByLabelText("Month", { selector: "select" }), "2026-08");
    await waitFor(() => expect(calls.some((c) => /period=2026-08/.test(c.url))).toBe(true));
  });

  it("opens an invoice from a link (?invoice=41)", async () => {
    search = "invoice=41";
    renderPage();
    expect(await screen.findByRole("dialog", { name: "Invoice INV-2609-0001" })).toBeInTheDocument();
  });

  it("a role with view only sees the accounts and invoices but cannot receive or generate", async () => {
    const user = userEvent.setup();
    search = "pay=C0012";
    renderPage("VIEWER");
    const row = (await screen.findByRole("button", { name: "Hiru Salon" })).closest("tr") as HTMLElement;
    expect(within(row).queryByRole("button", { name: "Receive" })).not.toBeInTheDocument();
    expect(within(row).getByRole("button", { name: "Statement" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ Receive payment" })).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "Monthly invoices" }));
    expect(await screen.findByRole("button", { name: "Vertex Software" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Generate invoices" })).not.toBeInTheDocument();
  });
});
