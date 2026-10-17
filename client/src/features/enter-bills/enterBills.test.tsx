import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { AccessProvider } from "@/features/auth/access";
import type { CustomerDetail, DueCustomer, DueList, MeResponse } from "@/lib/api/types";
import { addDaysIso, formatDate, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { EnterBills } from "./EnterBills";

let search = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));

const TODAY = todayIso();
const DAY = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"][
  new Date(TODAY + "T00:00:00Z").getUTCDay()
];

const ME = {
  user: { id: 1, username: "dilini.admin", fullName: "Dilini Admin", role: "ADMIN", roleName: "Admin" },
  mustChangePassword: false,
  landingPage: "/dashboard",
  permissions: [P.SALES_VIEW, P.SALES_EDIT, P.SALES_APPROVE_OVER_LIMIT, P.CUSTOMERS_VIEW],
  menu: [],
} as MeResponse;

function due(
  id: number,
  name: string,
  over: Partial<DueCustomer> = {},
  held = 2,
  toCollect = 0,
): DueCustomer {
  return {
    id,
    code: "C" + String(id).padStart(4, "0"),
    name,
    phone: "077 000 00" + String(id).padStart(2, "0"),
    address: id + ", Main Road",
    notes: null,
    areaId: 1,
    areaName: "Nugegoda",
    payType: "CASH",
    payTypeLabel: "Cash",
    creditLimit: 0,
    balance: 0,
    deliveryDay: 1,
    cycleWeeks: 1,
    nextDeliveryDate: TODAY,
    nextAfterBill: TODAY,
    due: true,
    overdue: false,
    daysLate: 0,
    entered: false,
    noBottles: held === 0,
    listed: held > 0,
    enteredBills: [],
    bottles: [
      {
        bottleTypeCode: "B20",
        label: "20L",
        usual: 2,
        held,
        toCollect,
        owed: 0,
        filled: 2,
        empties: 2 + toCollect,
        price: id === 4 ? 300 : 350,
        agreedPrice: id === 4,
        standardPrice: 350,
      },
    ],
    ...over,
  };
}

const LIST: DueList = {
  date: TODAY,
  dayName: DAY,
  today: true,
  counts: { due: 4, listed: 2, entered: 1, noBottles: 1, overdue: 0 },
  totals: {
    load: [{ bottleTypeCode: "B20", label: "20L", qty: 8 }],
    toCollect: 1,
    owed: 0,
    moneyToCollect: 900,
  },
  items: [
    due(1, "W.M. Sunil Perera", {}, 2, 1),
    due(4, "Vertex Software", { payType: "CREDIT", payTypeLabel: "Credit", creditLimit: 1000, balance: 900 }),
    due(3, "Sampath Grocery", {
      entered: true,
      listed: false,
      enteredBills: [
        { id: 9, billNo: "B000009", paperBillNo: "41000", kind: "EXCHANGE", kindLabel: "Exchange" },
      ],
    }),
    due(39, "Anoma Wickramaratne", {}, 0),
  ],
};

function detail(d: DueCustomer): CustomerDetail {
  return {
    id: d.id,
    code: d.code,
    name: d.name,
    address: "1, Road",
    areaId: 1,
    areaName: "Nugegoda",
    phone: d.phone,
    customerTypeId: 1,
    typeName: "Household",
    deliveryDay: 1,
    cycleWeeks: 1,
    nextDeliveryDate: d.nextDeliveryDate,
    usualQty: { B20: 2 },
    payType: d.payType,
    creditLimit: d.creditLimit,
    termsDays: d.payType === "CASH" ? 0 : 30,
    registeredOn: "2026-01-01",
    status: "ACTIVE",
    statusDate: null,
    statusReason: null,
    notes: null,
    whatsappOptIn: false,
    whatsappNumber: null,
    bottles: d.bottles.map((b) => ({
      bottleTypeCode: b.bottleTypeCode,
      label: b.label,
      held: b.held,
      toCollect: b.toCollect,
      owed: b.owed,
    })),
    outstanding: d.balance,
    overLimit: false,
    overdue: false,
    prices: d.bottles.map((b) => ({
      bottleTypeCode: b.bottleTypeCode,
      label: b.label,
      price: b.price,
      agreed: b.agreedPrice,
      standard: b.standardPrice,
      next: null,
      bottlesPerMonth: 9,
      fillingCharge: null,
    })),
    version: 0,
  } as CustomerDetail;
}

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

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let posts: Record<string, unknown>[] = [];
let billNo = 100;
/** Vertex Software (id 4) is over its credit limit unless the Admin approves. */
let limitOn = true;

function server(): ReturnType<typeof vi.fn<Fetch>> {
  return vi.fn<Fetch>(async (url, init) => {
    const path = url.split("?")[0];
    const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
    if (path === "/api/bottle-types") return json(BOTTLES);
    if (path === "/api/delivery/due") return json(LIST);
    if (path === "/api/sales/paper-bill/next") return json({ next: "1001", highest: "1000" });
    if (path.startsWith("/api/sales/paper-bill/")) {
      const no = decodeURIComponent(path.split("/").pop() ?? "");
      return no === "41000"
        ? json({
            paperBillNo: no,
            entered: true,
            txn: { date: TODAY, customerName: "Sampath Grocery" },
            message: "already",
          })
        : json({ paperBillNo: no, entered: false, txn: null, message: "not yet" });
    }
    const cust = /^\/api\/customers\/(\d+)$/.exec(path);
    if (cust) {
      const d = detail(LIST.items.find((x) => x.id === Number(cust[1]))!);
      // Before TODAY the customer had a price Rs. 50 lower (BR-08: a back-dated row uses the price of its date).
      const on = new URL("http://x" + url).searchParams.get("date");
      return json(
        on && on < TODAY ? { ...d, prices: d.prices.map((x) => ({ ...x, price: Number(x.price) - 50 })) } : d,
      );
    }
    if (path === "/api/customers/lookup") {
      const q = new URL("http://x" + url).searchParams.get("q") ?? "";
      return json(
        LIST.items
          .filter(
            (x) => x.code.endsWith(q.padStart(4, "0")) || x.name.toLowerCase().includes(q.toLowerCase()),
          )
          .map((x) => ({
            id: x.id,
            code: x.code,
            name: x.name,
            phone: x.phone,
            areaName: "Nugegoda",
            status: "ACTIVE",
          })),
      );
    }
    if (path === "/api/sales/bills" && init?.method === "POST") {
      const body = JSON.parse(String(init.body));
      posts.push(body);
      if (limitOn && body.customerId === 4 && !body.approveOverLimit)
        return json(
          {
            code: "CREDIT_LIMIT",
            message:
              "Vertex Software would exceed the credit limit (Rs. 1,000.00). Balance after this bill: Rs. 1,500.00. Admin approval is required.",
            details: { limit: 1000, balance: 900, after: 1500 },
          },
          422,
        );
      billNo++;
      const amount = body.customerId === 4 ? 600 : 700;
      return json(
        {
          txn: { id: billNo, billNo: "B000" + billNo, date: body.date, amount, paid: body.paid },
          schedule: { before: TODAY, after: "2099-01-01" },
        },
        201,
      );
    }
    return json({ code: "NOT_FOUND", message: "Not found " + path }, 404);
  });
}

function renderPage(children: ReactNode = <EnterBills />) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: ME, viewOnly: false }}>{children}</AccessProvider>
    </QueryClientProvider>,
  );
}

const rowOf = (n: number) => screen.getByTestId(`result-${n}`).closest("tr") as HTMLElement;

beforeEach(() => {
  search = "";
  posts = [];
  limitOn = true;
  vi.stubGlobal("fetch", server());
});
afterEach(() => vi.unstubAllGlobals());

describe("Enter Bills", () => {
  it("lists today's due customers with their usual quantities and the day info line", async () => {
    renderPage();
    const info = await screen.findByTestId("day-info");
    await waitFor(() =>
      expect(info).toHaveTextContent(
        `${DAY} ${formatDate(TODAY)}: 4 customer(s) due – 2 listed below with their usual quantities · 1 already entered ` +
          "(Sampath Grocery) · 1 new customer(s) without bottles – use First purchase: Anoma Wickramaratne",
      ),
    );
    const r1 = rowOf(1);
    expect(within(r1).getByLabelText("Customer row 1")).toHaveValue(
      "C0001 – W.M. Sunil Perera – 077 000 0001",
    );
    expect(within(r1).getByLabelText("20L filled row 1")).toHaveValue(2);
    // Empties = usual 2 + 1 left at the door last trip.
    expect(within(r1).getByLabelText("20L empties row 1")).toHaveValue(3);
    expect(within(r1).getByLabelText("Cash paid row 1")).toHaveValue(700);
    expect(screen.getByTestId("result-1")).toHaveTextContent("Rs. 700.00collects 1 left last trip");
    // Credit customer: no cash filled in; agreed price shown.
    expect(within(rowOf(2)).getByLabelText("Cash paid row 2")).toHaveValue(null);
    expect(screen.getByTestId("result-2")).toHaveTextContent("Rs. 600.00 → Credit20L @ Rs. 300.00 agreed");
    expect(screen.getByTestId("result-2")).toHaveTextContent("Over credit limit");
    expect(screen.getByTestId("grid-totals")).toHaveTextContent("2 bill(s) · 0 saved");
  });

  it("keyboard only: paper numbers suggested, Ctrl+S saves each row; over-limit row is approved by the Admin", async () => {
    const user = userEvent.setup();
    renderPage();
    const paper1 = await screen.findByLabelText("Paper bill no. row 1");
    await waitFor(() => expect(paper1).toHaveFocus());
    await waitFor(() => expect(paper1).toHaveValue("1001"));
    // Enter walks through the fields of row 1, then to row 2's paper bill number (suggested 1002).
    for (let i = 0; i < 8; i++) await user.keyboard("{Enter}");
    const paper2 = screen.getByLabelText("Paper bill no. row 2");
    expect(paper2).toHaveFocus();
    await waitFor(() => expect(paper2).toHaveValue("1002"));

    await user.keyboard("{Control>}s{/Control}");
    expect(await screen.findByText("✓ Saved as B000101")).toBeInTheDocument();
    expect(rowOf(1)).toHaveClass("posted");
    expect(within(rowOf(1)).getByLabelText("Paper bill no. row 1")).toBeDisabled();
    expect(posts[0]).toMatchObject({
      customerId: 1,
      date: TODAY,
      paperBillNo: "1001",
      kind: "EXCHANGE",
      lines: [{ bottleTypeCode: "B20", filled: 2, empties: 3 }],
      paid: 700,
      approveOverLimit: false,
    });
    expect(await screen.findByText(/would exceed the credit limit/)).toBeInTheDocument();
    expect(rowOf(2)).toHaveClass("err");

    await user.click(screen.getByRole("button", { name: "Approve & save" }));
    expect(await screen.findByText("✓ Saved as B000102")).toBeInTheDocument();
    expect(posts.at(-1)).toMatchObject({ customerId: 4, paperBillNo: "1002", approveOverLimit: true });
    expect(screen.getByTestId("grid-totals")).toHaveTextContent("2 bill(s) · 2 saved");
  });

  it("Save all: every row ends saved, also the last one; the empty row at the end is reused", async () => {
    const user = userEvent.setup();
    limitOn = false;
    renderPage();
    const paper1 = await screen.findByLabelText("Paper bill no. row 1");
    await waitFor(() => expect(paper1).toHaveValue("1001"));
    for (let i = 0; i < 16; i++) await user.keyboard("{Enter}");
    // Enter on the last field of row 2 opened an empty row 3.
    expect(screen.getByLabelText("Paper bill no. row 3")).toHaveFocus();
    await user.keyboard("{Control>}s{/Control}");
    await waitFor(() => expect(rowOf(2)).toHaveClass("posted"));
    expect(rowOf(1)).toHaveClass("posted");
    expect(screen.getByTestId("result-2")).toHaveTextContent(/Saved as B000\d+/);
    expect(screen.queryByLabelText("Paper bill no. row 4")).not.toBeInTheDocument();
    expect(screen.getByTestId("grid-totals")).toHaveTextContent("2 bill(s) · 2 saved");
    expect(posts.map((p) => p.paperBillNo)).toEqual(["1001", "1002"]);
  });

  it("checks paper bill numbers against the database and the grid; left at door; Ctrl+Del", async () => {
    const user = userEvent.setup();
    renderPage();
    const paper1 = await screen.findByLabelText("Paper bill no. row 1");
    // The page opens with row 1's paper bill number focused and the suggestion selected: typing replaces it.
    await waitFor(() => expect(paper1).toHaveFocus());
    await waitFor(() => expect(paper1).toHaveValue("1001"));
    await user.keyboard("41000");
    expect(paper1).toHaveValue("41000");
    expect(
      await within(screen.getByTestId("result-1")).findByText(
        `Paper bill 41000 already entered (${formatDate(TODAY)}, Sampath Grocery)`,
      ),
    ).toBeInTheDocument();
    await user.clear(paper1);
    await user.type(paper1, "77");
    const paper2 = screen.getByLabelText("Paper bill no. row 2");
    await user.clear(paper2);
    await user.type(paper2, "77");
    expect(
      await within(screen.getByTestId("result-2")).findByText("Same bill no. twice"),
    ).toBeInTheDocument();
    expect(paper2).toHaveClass("invalid");

    // L on the type field: Left at door – empties cleared and closed, phone ✓ open, note needed.
    const kind = screen.getByLabelText("Type row 1");
    kind.focus();
    await user.keyboard("l");
    expect(kind).toHaveValue("LEFT_AT_DOOR");
    expect(screen.getByLabelText("20L empties row 1")).toBeDisabled();
    expect(screen.getByLabelText("Phone agreement row 1")).toBeEnabled();
    expect(screen.getByLabelText("Cash paid row 1")).toHaveValue(null);
    expect(screen.getByTestId("result-1")).toHaveTextContent(
      "2 20L to collectTick phone agreementNote needed",
    );

    // Ctrl+Del removes an unsaved row.
    screen.getByLabelText("Note row 2").focus();
    await user.keyboard("{Control>}{Delete}{/Control}");
    expect(screen.queryByLabelText("Paper bill no. row 2")).not.toBeInTheDocument();
  });

  it("?cust= opens one row for that customer", async () => {
    search = "cust=C0001";
    renderPage();
    const info = await screen.findByTestId("day-info");
    await waitFor(() =>
      expect(info).toHaveTextContent(
        "Entering a bill for W.M. Sunil Perera. Change the bill date to list all customers due that day.",
      ),
    );
    expect(screen.getByLabelText("Customer row 1")).toHaveValue("C0001 – W.M. Sunil Perera – 077 000 0001");
    expect(screen.queryByLabelText("Customer row 2")).not.toBeInTheDocument();
  });

  it("a customer typed by code fills the usual quantities", async () => {
    const user = userEvent.setup();
    search = "cust=C0001";
    renderPage();
    await screen.findByLabelText("Customer row 1");
    await user.click(screen.getByRole("button", { name: "+ Add row" }));
    const cust = await screen.findByLabelText("Customer row 2");
    await user.type(cust, "4");
    await waitFor(() => expect(cust).toHaveValue("C0004 – Vertex Software – 077 000 0004"));
    expect(screen.getByLabelText("20L filled row 2")).toHaveValue(2);
    expect(screen.getByLabelText("20L empties row 2")).toHaveValue(2);
  });

  it("a back-dated row shows the price in force on its date (B-05)", async () => {
    const user = userEvent.setup();
    search = "cust=C0004";
    renderPage();
    const cust = await screen.findByLabelText("Customer row 1");
    await waitFor(() => expect(cust).toHaveValue("C0004 – Vertex Software – 077 000 0004"));
    await waitFor(() => expect(screen.getByTestId("result-1")).toHaveTextContent("Rs. 600.00"));
    const date = screen.getByLabelText("Date row 1");
    await user.clear(date);
    await user.type(date, formatDate(addDaysIso(TODAY, -5)));
    await waitFor(() => expect(screen.getByTestId("result-1")).toHaveTextContent("Rs. 500.00"));
  });
});
