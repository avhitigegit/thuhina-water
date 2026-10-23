import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PageActionsSlotContext } from "@/components/layout/PageActions";
import { Toaster } from "@/components/shared/Toaster";
import { DemoDataTab } from "@/features/admin/DemoDataTab";
import { AccessProvider } from "@/features/auth/access";
import type { DashboardView, MeResponse, ReportView } from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { Dashboard } from "./Dashboard";
import { cellText, quickRange, Reports } from "./Reports";

let search = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));

const TODAY = "2026-10-15";

function me(permissions: string[]): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role: "ADMIN", roleName: "Admin" },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions,
    menu: [],
  } as MeResponse;
}

const DASH: DashboardView = {
  today: TODAY,
  lowStock: [
    {
      bottleTypeCode: "B20",
      name: "20L",
      filled: 63,
      minFilled: 150,
      atFactory: 66,
      message: "Low stock: only 63 filled 20L bottles in store (minimum 150). 66 are at the factory.",
    },
  ],
  lowStockHref: "/production",
  kpis: [
    {
      key: "sales",
      label: "Sales today",
      value: "Rs. 5,470.00",
      hint: "6 bills",
      href: "/sales/bills",
      alert: false,
    },
    {
      key: "filled",
      label: "Filled 20L in store",
      value: "63",
      hint: "minimum 150",
      href: null,
      alert: true,
    },
  ],
  deliveries: [
    {
      customerId: 1,
      code: "C0001",
      name: "Green Leaf Montessori",
      area: "Nugegoda",
      usual: "4×20L 2×10L",
      status: "DUE",
      statusLabel: "Due",
    },
    {
      customerId: 2,
      code: "C0002",
      name: "Dr. A. Ramanathan",
      area: "Kotte",
      usual: "2×20L",
      status: "MISSED",
      statusLabel: "Missed 12/10/2026",
    },
  ],
  deliveriesHref: "/delivery/daily-list",
  attention: [
    { kind: "PO_RECEIVE", linkText: "PO-2026-0041", text: " – goods to receive", href: "/purchasing#pos" },
    { kind: "OVER_90", linkText: "1 customer", text: " owing for more than 90 days", href: null },
  ],
  sales14: [
    { date: "2026-10-14", amount: 6900 },
    { date: "2026-10-15", amount: 0 },
  ],
};

const SALES: ReportView = {
  key: "sales",
  name: "Sales",
  from: "2026-09-01",
  to: TODAY,
  kpis: [
    { label: "Total sales", value: "419540.00", type: "MONEY", alert: false },
    { label: "Bills", value: "240", type: "NUMBER", alert: false },
  ],
  chart: { title: "By day", points: [{ label: "01", value: "5470.00", title: "01/09/2026: Rs. 5,470.00" }] },
  tables: [
    {
      title: "By sale type",
      columns: [
        { header: "Sale type", type: "TEXT" },
        { header: "Bills", type: "NUMBER" },
        { header: "Total", type: "MONEY" },
      ],
      rows: [
        ["Exchange", "227", "241500.00"],
        ["Extra order", "9", "24040.00"],
      ],
      footer: ["Total", "236", "265540.00"],
      boldRows: [1],
    },
    {
      title: "Customers on agreed prices – water billed vs standard price",
      columns: [{ header: "Customer", type: "TEXT" }],
      rows: [],
      footer: null,
      boldRows: [],
    },
  ],
  note: "Deposits count as revenue.",
};

const DELIVERY_NOTE = "The Delivery report shows at most the last 92 days of the range.";

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let calls: { method: string; url: string; body: unknown }[] = [];
let opened: string[] = [];

function server() {
  return vi.fn<Fetch>(async (url, init) => {
    const [path] = url.split("?");
    const method = init?.method ?? "GET";
    calls.push({ method, url, body: typeof init?.body === "string" ? JSON.parse(init.body) : undefined });
    const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status });
    if (path === "/api/dashboard") return json(DASH);
    if (path === "/api/reports")
      return json([
        { key: "sales", name: "Sales" },
        { key: "profit", name: "Profit" },
        { key: "delivery", name: "Delivery" },
      ]);
    if (path.startsWith("/api/reports/")) {
      const key = path.split("/")[3];
      if (key === "delivery") {
        const to = new URL(url, "http://x").searchParams.get("to");
        return json({ ...SALES, key, name: "Delivery", from: "2026-09-20", to, note: DELIVERY_NOTE });
      }
      return json({ ...SALES, key, name: key === "profit" ? "Profit" : "Sales" });
    }
    if (path === "/api/admin/demo-data" && method === "POST")
      return json({
        from: "2026-08-15",
        to: "2026-10-14",
        bills: 227,
        leftAtDoor: 17,
        shortages: 5,
        extraOrders: 9,
        productSales: 4,
        dispatches: 9,
        returns: 18,
        payments: 42,
        invoices: 11,
        skipped: 1,
        problems: ["17/08/2026 C0028 bill: Not enough filled stock."],
        message: "227 delivery bills made for 15/08/2026 – 14/10/2026 (1 steps skipped)",
      });
    return json({ code: "NOT_FOUND", message: "Not found " + path }, 404);
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

function renderWith(ui: ReactNode, permissions: string[]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(permissions), viewOnly: false }}>
        <Shell>{ui}</Shell>
        <Toaster />
      </AccessProvider>
    </QueryClientProvider>,
  );
}

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
});
afterEach(() => vi.unstubAllGlobals());

describe("report helpers", () => {
  it("formats cells by type and keeps words in number columns", () => {
    expect(cellText("1350.5", "MONEY")).toBe("1,350.50");
    expect(cellText("12500", "NUMBER")).toBe("12,500");
    expect(cellText("2026-09-30", "DATE")).toBe("30/09/2026");
    expect(cellText("Total", "MONEY")).toBe("Total");
    expect(cellText("", "MONEY")).toBe("");
  });

  it("quick ranges", () => {
    expect(quickRange("today", TODAY)).toEqual([TODAY, TODAY]);
    expect(quickRange("week", TODAY)).toEqual(["2026-10-09", TODAY]);
    expect(quickRange("month", TODAY)).toEqual(["2026-10-01", TODAY]);
    expect(quickRange("last", TODAY)).toEqual(["2026-09-01", "2026-09-30"]);
    expect(quickRange("last", "2027-01-05")).toEqual(["2026-12-01", "2026-12-31"]);
    expect(quickRange("quarter", TODAY)).toEqual(["2026-08-01", TODAY]);
  });
});

describe("Dashboard", () => {
  it("shows the quick buttons, low stock, KPIs (links only where allowed), deliveries, needs attention and the chart", async () => {
    renderWith(<Dashboard />, [P.DASHBOARD_VIEW, P.SALES_EDIT, P.BILLING_EDIT, P.PRODUCTION_EDIT]);
    expect(await screen.findByText(/only 63 filled 20L bottles in store/)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Send empties" })).toHaveAttribute("href", "/production?send=1");
    const sales = screen.getByTestId("kpi-sales");
    expect(within(sales).getByRole("link", { name: "Rs. 5,470.00" })).toHaveAttribute("href", "/sales/bills");
    expect(sales).toHaveTextContent("6 bills");
    const filled = screen.getByTestId("kpi-filled");
    expect(filled).toHaveClass("alert");
    expect(within(filled).queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("Missed 12/10/2026")).toHaveClass("badge", "bad");
    expect(screen.getByText("4×20L 2×10L")).toBeInTheDocument();
    const todo = screen.getByTestId("attention");
    expect(within(todo).getByRole("link", { name: "PO-2026-0041" })).toHaveAttribute(
      "href",
      "/purchasing#pos",
    );
    expect(todo).toHaveTextContent("1 customer owing for more than 90 days");
    expect(within(todo).queryByRole("link", { name: "1 customer" })).not.toBeInTheDocument();
    expect(screen.getByRole("img", { name: "Sales – last 14 days" })).toBeInTheDocument();
    const actions = screen.getByTestId("actions");
    expect(within(actions).getByRole("link", { name: "Enter bills" })).toHaveAttribute(
      "href",
      "/sales/enter-bills",
    );
    expect(within(actions).getByRole("link", { name: "Receive payment" })).toHaveAttribute(
      "href",
      "/billing?pay=new",
    );
  });

  it("the Accountant has no Enter bills button", async () => {
    renderWith(<Dashboard />, [P.DASHBOARD_VIEW, P.BILLING_EDIT]);
    await screen.findByText("Sales today");
    expect(screen.queryByRole("link", { name: "Enter bills" })).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Receive payment" })).toBeInTheDocument();
  });
});

describe("Reports", () => {
  it("runs the first report for last month, shows KPIs, chart and tables, and exports", async () => {
    const user = userEvent.setup();
    renderWith(<Reports today={TODAY} />, [P.REPORTS_VIEW]);
    expect(await screen.findByText("Total sales")).toBeInTheDocument();
    expect(calls.some((c) => c.url === "/api/reports/sales?from=2026-09-01&to=2026-10-15")).toBe(true);
    expect(screen.getByText("Total sales").nextSibling).toHaveTextContent("Rs. 419,540.00");
    expect(screen.getByText("Bills", { selector: ".kpi .label" }).nextSibling).toHaveTextContent("240");
    expect(screen.getByRole("img", { name: "Sales – By day" })).toBeInTheDocument();
    const exchange = screen.getByText("Exchange").closest("tr") as HTMLElement;
    expect(exchange).toHaveTextContent("241,500.00");
    expect(
      within(screen.getByText("Extra order").closest("tr") as HTMLElement).getByText("24,040.00").tagName,
    ).toBe("B");
    expect(screen.getByText("265,540.00").closest("tfoot")).not.toBeNull();
    expect(screen.getByText("Nothing in this period.")).toBeInTheDocument();
    expect(screen.getByText("Deposits count as revenue.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Export PDF" }));
    expect(opened).toEqual(["/api/reports/sales/export?format=pdf&from=2026-09-01&to=2026-10-15"]);
    expect(screen.getByRole("link", { name: "Export Excel" })).toHaveAttribute(
      "href",
      "/api/reports/sales/export?format=xlsx&from=2026-09-01&to=2026-10-15",
    );
  });

  it("changes the report and the range", async () => {
    const user = userEvent.setup();
    search = "r=profit";
    renderWith(<Reports today={TODAY} />, [P.REPORTS_VIEW]);
    await waitFor(() => expect(screen.getByLabelText("Report")).toHaveValue("profit"));
    await user.selectOptions(screen.getByLabelText("Quick"), "week");
    await waitFor(() =>
      expect(calls.some((c) => c.url === "/api/reports/profit?from=2026-10-09&to=2026-10-15")).toBe(true),
    );
    expect(screen.getByLabelText("From")).toHaveValue("09/10/2026");
    // A From after To is refused before calling the server.
    const from = screen.getByLabelText("From");
    await user.clear(from);
    await user.type(from, "20/10/2026");
    await user.click(screen.getByRole("button", { name: "Apply" }));
    expect(screen.getByText("The From date must be on or before the To date.")).toBeInTheDocument();
  });
});

describe("Reports – shortened range", () => {
  it("says at the top when the server shows a shorter range (N-11)", async () => {
    search = "r=delivery";
    renderWith(<Reports today={TODAY} />, [P.REPORTS_VIEW, P.REPORTS_ALL]);
    expect(
      await screen.findByText(
        `You chose 01/09/2026 to 15/10/2026; this report shows 20/09/2026 to 15/10/2026. ${DELIVERY_NOTE}`,
      ),
    ).toBeInTheDocument();
  });
});

describe("Demo data", () => {
  it("makes demo data for the suggested range and shows what was made", async () => {
    const user = userEvent.setup();
    renderWith(
      <DemoDataTab
        status={{ suggestedFrom: "2026-08-15", suggestedTo: "2026-10-14", lastDeliveryBill: null }}
        today={TODAY}
      />,
      [P.ADMIN_MANAGE],
    );
    expect(screen.getByLabelText("From")).toHaveValue("15/08/2026");
    await user.click(screen.getByRole("button", { name: "Make demo data" }));
    await waitFor(() => expect(calls.some((c) => c.method === "POST")).toBe(true));
    expect(calls.find((c) => c.method === "POST")?.body).toEqual({ from: "2026-08-15", to: "2026-10-14" });
    const result = await screen.findByTestId("demo-result");
    expect(result).toHaveTextContent("Delivery bills227");
    expect(result).toHaveTextContent("17/08/2026 C0028 bill: Not enough filled stock.");
    expect(
      await screen.findByText("227 delivery bills made for 15/08/2026 – 14/10/2026 (1 steps skipped)"),
    ).toBeInTheDocument();
  });
});
