import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PageActionsSlotContext } from "@/components/layout/PageActions";
import { Toaster } from "@/components/shared/Toaster";
import { AccessProvider } from "@/features/auth/access";
import type { ExpenseMonthView, ExpenseRow, ExpenseSummary, ExpenseType, MeResponse } from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { Expenses } from "./Expenses";

let search = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));

const TODAY = "2026-10-14";

function me(canEdit: boolean): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role: "ACCOUNTANT", roleName: "Accountant" },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions: canEdit ? [P.EXPENSES_VIEW, P.EXPENSES_EDIT] : [P.EXPENSES_VIEW],
    menu: [],
  } as MeResponse;
}

const TYPES: ExpenseType[] = [
  { id: 1, name: "Electricity", description: "CEB / LECO bill", active: true, version: 0 },
  { id: 3, name: "Salaries", description: "Staff salaries and wages", active: true, version: 0 },
  { id: 6, name: "Vehicle spare parts & repairs", description: null, active: true, version: 0 },
  { id: 10, name: "Insurance", description: null, active: false, version: 2 },
];

function row(over: Partial<ExpenseRow>): ExpenseRow {
  return {
    id: 20,
    expenseNo: "EX-0020",
    paidDate: "2026-10-02",
    forMonth: "2026-09",
    forMonthLabel: "September 2026",
    paidInOtherMonth: true,
    typeId: 1,
    typeName: "Electricity",
    description: "Store – September bill",
    paidTo: "CEB",
    billNo: "A/C 4471230981",
    amount: 4260,
    method: "CASH",
    methodLabel: "Cash",
    reference: null,
    createdByName: "Shanika Fernando",
    version: 0,
    ...over,
  };
}

const MONTHS = [
  { month: "2026-10", label: "October 2026" },
  { month: "2026-09", label: "September 2026" },
  { month: "2026-08", label: "August 2026" },
];

function view(month: string): ExpenseMonthView {
  const sept = month === "2026-09";
  const items = sept
    ? [
        row({
          id: 13,
          expenseNo: "EX-0013",
          paidDate: "2026-09-30",
          paidInOtherMonth: false,
          typeId: 3,
          typeName: "Salaries",
          description: "Delivery helper – September",
          paidTo: "Staff",
          billNo: null,
          amount: 46000,
          method: "BANK_TRANSFER",
          methodLabel: "Bank transfer",
          reference: "TRF-788310",
        }),
        row({}),
      ]
    : [];
  return {
    month,
    monthLabel: MONTHS.find((m) => m.month === month)!.label,
    months: MONTHS,
    items,
    shownTotal: sept ? 50260 : 0,
    total: sept ? 50260 : 0,
    entries: items.length,
    previousMonth: "2026-08",
    previousLabel: "August 2026",
    previousTotal: sept ? 100590 : 50260,
    difference: sept ? -50330 : -50260,
    byType: sept
      ? [
          { typeId: 3, name: "Salaries", total: 46000 },
          { typeId: 1, name: "Electricity", total: 4260 },
        ]
      : [],
    payees: ["CEB", "Staff"],
  };
}

const SUMMARY: ExpenseSummary = {
  from: "2026-08",
  to: "2026-10",
  months: [
    { month: "2026-08", label: "August 2026", total: 100590 },
    { month: "2026-09", label: "September 2026", total: 50260 },
    { month: "2026-10", label: "October 2026", total: 0 },
  ],
  rows: [
    { typeId: 1, name: "Electricity", active: true, amounts: [4180, 4260, 0], total: 8440 },
    { typeId: 3, name: "Salaries", active: true, amounts: [46000, 46000, 0], total: 92000 },
  ],
  total: 150850,
};

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let calls: { method: string; url: string; path: string; body: unknown }[] = [];
let opened: string[] = [];
let refuseNext = false;

function server() {
  return vi.fn<Fetch>(async (url, init) => {
    const [path, qs] = url.split("?");
    const method = init?.method ?? "GET";
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    calls.push({ method, url, path, body });
    const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status });
    if (method === "GET") {
      if (path === "/api/expenses") return json(view(new URLSearchParams(qs).get("month") || "2026-10"));
      if (path === "/api/expenses/summary") return json(SUMMARY);
      if (path === "/api/expense-types") return json(TYPES);
    }
    if (path === "/api/expenses" || /^\/api\/expenses\/\d+$/.test(path)) {
      if (method === "DELETE") return new Response(null, { status: 204 });
      if (refuseNext) {
        refuseNext = false;
        return json(
          {
            code: "VALIDATION",
            message: "Please check the highlighted fields.",
            details: { fields: { reference: "Enter the cheque number or bank reference." } },
          },
          400,
        );
      }
      const b = body as { forMonth: string; amount: number };
      return json(
        {
          expense: row({ id: 21, expenseNo: "EX-0021", forMonth: b.forMonth, amount: b.amount }),
          message: method === "POST" ? `EX-0021 saved – Rs. 1,250.00 for September 2026` : "Expense updated",
        },
        method === "POST" ? 201 : 200,
      );
    }
    if (path.startsWith("/api/expense-types"))
      return json({ ...TYPES[0], ...(body as object) }, method === "POST" ? 201 : 200);
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

function renderPage(canEdit = true) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(canEdit), viewOnly: !canEdit }}>
        <Shell>
          <Expenses today={TODAY} />
        </Shell>
        <Toaster />
      </AccessProvider>
    </QueryClientProvider>,
  );
}

const sent = (method: string) => calls.filter((c) => c.method === method);

beforeEach(() => {
  calls = [];
  opened = [];
  refuseNext = false;
  search = "month=2026-09";
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

describe("Expenses", () => {
  it("shows the month's KPIs, the list with the paid-in-another-month mark, totals and bars", async () => {
    const user = userEvent.setup();
    renderPage();
    expect((await screen.findByText("Total for September 2026")).nextSibling).toHaveTextContent(
      "Rs. 50,260.00",
    );
    const prev = screen.getByText("August 2026", { selector: ".label" }).parentElement as HTMLElement;
    expect(prev).toHaveTextContent("Rs. 100,590.00");
    expect(prev).toHaveTextContent("−Rs. 50,330.00 this month");
    expect(screen.getByText("Biggest expense type").parentElement).toHaveTextContent("SalariesRs. 46,000.00");
    const elec = (await screen.findByText("Store – September bill")).closest("tr") as HTMLElement;
    expect(elec).toHaveTextContent("EX-002002/10/2026paid Oct");
    expect(elec).toHaveTextContent("CEB · bill A/C 4471230981");
    const foot = screen.getByText("Total", { selector: "td" }).closest("tr") as HTMLElement;
    expect(foot).toHaveTextContent("2 entries");
    expect(foot).toHaveTextContent("Rs. 50,260.00");
    expect(document.querySelectorAll(".hbar")).toHaveLength(2);
    await user.click(screen.getByRole("button", { name: "Print month" }));
    expect(opened).toEqual(["/api/print/expenses?month=2026-09"]);
  });

  it("adds an expense for an earlier month, shows the server message, and saves another", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Store – September bill");
    await user.click(screen.getByRole("button", { name: "+ Add expense" }));
    const dialog = await screen.findByRole("dialog", { name: "Add expense" });
    expect(within(dialog).getByLabelText("Expense no.")).toHaveValue("Generated on save");
    expect(within(dialog).getByLabelText("For month")).toHaveValue("2026-09");
    // Only active types (Insurance is inactive).
    expect(
      within(within(dialog).getByLabelText("Expense type")).queryByText("Insurance"),
    ).not.toBeInTheDocument();
    await user.selectOptions(within(dialog).getByLabelText("Expense type"), "1");
    await user.type(within(dialog).getByLabelText("Description"), "Late meter fee");
    await user.type(within(dialog).getByLabelText("Amount (Rs.)"), "1250");
    expect(within(dialog).queryByLabelText("Cheque no. / bank ref.")).not.toBeInTheDocument();
    await user.selectOptions(within(dialog).getByLabelText("Paid by"), "CHEQUE");

    refuseNext = true;
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(await within(dialog).findByText("Enter the cheque number or bank reference.")).toBeInTheDocument();

    await user.type(within(dialog).getByLabelText("Cheque no. / bank ref."), "Cheque 330200");
    await user.click(within(dialog).getByRole("button", { name: "Save & add another" }));
    await waitFor(() => expect(sent("POST")).toHaveLength(2));
    expect(sent("POST")[1].body).toEqual({
      paidDate: TODAY,
      forMonth: "2026-09",
      typeId: 1,
      description: "Late meter fee",
      paidTo: "",
      billNo: "",
      amount: 1250,
      method: "CHEQUE",
      reference: "Cheque 330200",
    });
    expect(await screen.findByText("EX-0021 saved – Rs. 1,250.00 for September 2026")).toBeInTheDocument();
    // Still open for the next one: same month, the rest cleared.
    const again = screen.getByRole("dialog", { name: "Add expense" });
    expect(within(again).getByLabelText("For month")).toHaveValue("2026-09");
    expect(within(again).getByLabelText("Expense type")).toHaveValue("");
    expect(within(again).getByLabelText("Amount (Rs.)")).toHaveValue(null);
  });

  it("edits with the version and deletes with a reason", async () => {
    const user = userEvent.setup();
    renderPage();
    const elec = (await screen.findByText("Store – September bill")).closest("tr") as HTMLElement;
    await user.click(within(elec).getByRole("button", { name: "Edit" }));
    const dialog = await screen.findByRole("dialog", { name: "Edit expense EX-0020" });
    expect(within(dialog).queryByRole("button", { name: "Save & add another" })).not.toBeInTheDocument();
    const amount = within(dialog).getByLabelText("Amount (Rs.)");
    await user.clear(amount);
    await user.type(amount, "4300");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await waitFor(() => expect(sent("PUT")).toHaveLength(1));
    expect(sent("PUT")[0].path).toBe("/api/expenses/20");
    expect(sent("PUT")[0].body).toMatchObject({ amount: 4300, version: 0, typeId: 1, forMonth: "2026-09" });
    expect(await screen.findByText("Expense updated")).toBeInTheDocument();

    await user.click(within(elec).getByRole("button", { name: "Delete" }));
    const del = await screen.findByRole("dialog", { name: "Delete EX-0020?" });
    expect(del).toHaveTextContent("Electricity Rs. 4,260.00 for September 2026");
    await user.click(within(del).getByRole("button", { name: "Delete" }));
    expect(within(del).getByText("Enter a reason.")).toBeInTheDocument();
    await user.type(within(del).getByLabelText("Reason for deleting"), "Entered twice");
    await user.click(within(del).getByRole("button", { name: "Delete" }));
    await waitFor(() => expect(sent("DELETE")).toHaveLength(1));
    expect(sent("DELETE")[0].body).toEqual({ reason: "Entered twice", version: 0 });
    expect(await screen.findByText("Expense deleted")).toBeInTheDocument();
  });

  it("shows month by month, opens a month from it and exports Excel", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("tab", { name: "Month by month" }));
    expect(await screen.findByTestId("summary-total")).toHaveTextContent("150,850");
    const salaries = screen.getByText("Salaries", { selector: "b" }).closest("tr") as HTMLElement;
    expect(salaries).toHaveTextContent("46,00046,000–92,000");
    // A real .xlsx: the Expenses report for the months shown (Step 4).
    expect(screen.getByRole("link", { name: "Export Excel" })).toHaveAttribute(
      "href",
      expect.stringMatching(
        /^\/api\/reports\/expenses\/export\?format=xlsx&from=2026-08-01&to=2026-\d\d-\d\d$/,
      ),
    );

    await user.click(screen.getByRole("button", { name: "August ’26" }));
    expect(await screen.findByRole("heading", { name: "Expenses for August 2026" })).toBeInTheDocument();
    expect(calls.some((c) => c.path === "/api/expenses" && /month=2026-08/.test(c.url))).toBe(true);
  });

  it("manages the expense types", async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText("Store – September bill");
    await user.click(screen.getByRole("button", { name: "Expense types" }));
    const dialog = await screen.findByRole("dialog", { name: "Expense types" });
    const elec = within(dialog).getByText("Electricity").closest("tr") as HTMLElement;
    expect(elec).toHaveTextContent("Rs. 4,260.00");
    expect(elec).toHaveTextContent("Active");
    await user.click(within(dialog).getByRole("button", { name: "Activate Insurance" }));
    await waitFor(() => expect(sent("PUT")).toHaveLength(1));
    expect(sent("PUT")[0]).toMatchObject({
      path: "/api/expense-types/10",
      body: { active: true, version: 2 },
    });
    await user.type(within(dialog).getByLabelText("New type"), "Security");
    await user.click(within(dialog).getByRole("button", { name: "Add" }));
    await waitFor(() => expect(sent("POST")).toHaveLength(1));
    expect(sent("POST")[0].body).toEqual({ name: "Security", description: "" });
    expect(await screen.findByText("Expense type added")).toBeInTheDocument();
  });

  it("opens Add expense from a link (?new=1)", async () => {
    search = "new=1";
    renderPage();
    expect(await screen.findByRole("dialog", { name: "Add expense" })).toBeInTheDocument();
  });

  it("without the edit permission the page is view only", async () => {
    search = "month=2026-09&new=1";
    renderPage(false);
    const elec = (await screen.findByText("Store – September bill")).closest("tr") as HTMLElement;
    expect(within(elec).queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ Add expense" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Expense types" })).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Print month" })).toBeInTheDocument();
  });
});
