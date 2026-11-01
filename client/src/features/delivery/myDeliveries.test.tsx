import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { DueCustomer, DueList, MyDay } from "@/lib/api/types";
import { addDaysIso, todayIso } from "@/lib/format";
import { DaySummary, MyDeliveries } from "./MyDeliveries";

const TODAY = todayIso();

function due(id: number, name: string, over: Partial<DueCustomer> = {}): DueCustomer {
  return {
    id,
    code: "C" + String(id).padStart(4, "0"),
    name,
    phone: "077 000 00" + String(id).padStart(2, "0"),
    address: id + ", Temple Road",
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
    nextAfterBill: addDaysIso(TODAY, 7),
    due: true,
    overdue: false,
    daysLate: 0,
    entered: false,
    noBottles: false,
    listed: true,
    enteredBills: [],
    bottles: [
      {
        bottleTypeCode: "B20",
        label: "20L",
        usual: 2,
        held: 2,
        toCollect: 0,
        owed: 0,
        filled: 2,
        empties: 2,
        price: 350,
        agreedPrice: false,
        standardPrice: 350,
      },
    ],
    ...over,
  };
}

const LIST: DueList = {
  date: TODAY,
  dayName: "Monday",
  today: true,
  counts: { due: 2, listed: 1, entered: 1, noBottles: 0, overdue: 0 },
  totals: { load: [], toCollect: 0, owed: 0, moneyToCollect: 0 },
  items: [
    due(1, "W.M. Sunil Perera", { balance: 1400 }),
    due(3, "Sampath Grocery", {
      entered: true,
      listed: false,
      enteredBills: [
        {
          id: 9,
          billNo: "B000009",
          paperBillNo: null,
          kind: "EXCHANGE",
          kindLabel: "Exchange",
          onPhone: true,
          enteredBy: "Kasun Silva",
        },
      ],
    }),
  ],
};

let posts: { path: string; body: Record<string, unknown> }[] = [];

beforeEach(() => {
  posts = [];
  window.location.hash = "";
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const path = url.split("?")[0];
      const body = init?.body ? JSON.parse(String(init.body)) : {};
      if (path === "/api/delivery/due") return new Response(JSON.stringify(LIST));
      if (path === "/api/areas") return new Response(JSON.stringify([]));
      if (path === "/api/delivery/mobile/customers") {
        return new Response(JSON.stringify([due(1, "W.M. Sunil Perera"), due(77, "Other Route Stores")]));
      }
      if (path === "/api/delivery/mobile/bills/preview") {
        const filled = (body.lines as { filled: number }[]).reduce((n, l) => n + l.filled, 0);
        return new Response(
          JSON.stringify({
            amount: filled * 350,
            paid: body.paid ?? 0,
            problems: [],
            warnings: [],
            credit: null,
          }),
        );
      }
      if (path === "/api/delivery/mobile/bills") {
        posts.push({ path, body });
        return new Response(
          JSON.stringify({
            txn: { billNo: "B000101", amount: 700, paid: body.paid },
            schedule: { before: TODAY, after: addDaysIso(TODAY, 7) },
          }),
          { status: 201 },
        );
      }
      if (path === "/api/delivery/mobile/payments") {
        posts.push({ path, body });
        return new Response(
          JSON.stringify({
            payment: { receiptNo: "RC-00099" },
            message: "Payment saved – Rs. 500.00 received.",
          }),
          { status: 201 },
        );
      }
      return new Response("{}");
    }),
  );
});

function renderPage() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <MyDeliveries today={TODAY} />
    </QueryClientProvider>,
  );
}

describe("My Deliveries", () => {
  it("lists today's customers, marks the ones done and finds customers on other routes", async () => {
    const user = userEvent.setup();
    renderPage();
    expect(await screen.findByText("W.M. Sunil Perera")).toBeInTheDocument();
    expect(screen.getByTestId("mob-count")).toHaveTextContent("2 customer(s) to visit · 1 done");
    expect(screen.getByText("Done")).toBeInTheDocument();
    expect(screen.getByText("Balance due Rs. 1,400.00")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Find a customer"), "stores");
    expect(await screen.findByText("Other Route Stores")).toBeInTheDocument();
    expect(screen.getByText("Other customers")).toBeInTheDocument();
    // Today's list is filtered; the customer already on it is not repeated under "Other customers".
    expect(screen.queryByText("W.M. Sunil Perera")).not.toBeInTheDocument();
  });

  it("saves the bottles left, empties collected and a bank transfer with its reference", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText("W.M. Sunil Perera"));
    expect(screen.getByLabelText("Filled left – 20L")).toHaveValue(2);
    expect(screen.getByLabelText("Empties collected – 20L")).toHaveValue(2);

    await user.click(screen.getByRole("button", { name: "Filled left – 20L – more" }));
    await waitFor(() => expect(screen.getByTestId("mob-sum")).toHaveTextContent("Rs. 1,050.00"));
    await user.click(screen.getByRole("button", { name: /This bill Rs. 1,050.00/ }));
    await user.click(screen.getByRole("radio", { name: "Bank transfer" }));
    await user.type(screen.getByLabelText("Transfer reference"), "TRF-77");
    await user.click(screen.getByRole("button", { name: "Save delivery" }));

    await waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0]).toEqual({
      path: "/api/delivery/mobile/bills",
      body: expect.objectContaining({
        customerId: 1,
        kind: "EXCHANGE",
        lines: [{ bottleTypeCode: "B20", filled: 3, empties: 2 }],
        paid: 1050,
        payMethod: "BANK_TRANSFER",
        payReference: "TRF-77",
      }),
    });
    expect(await screen.findByTestId("mob-saved")).toHaveTextContent("Bill B000101 saved");
  });

  it("left at door collects no empties and needs a note", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText("W.M. Sunil Perera"));
    await user.click(screen.getByRole("radio", { name: "Nobody home – left at door" }));
    expect(screen.getByLabelText("Empties collected – 20L")).toBeDisabled();
    await user.type(screen.getByLabelText("Note"), "Left at the gate");
    await user.click(screen.getByRole("button", { name: "Save delivery" }));
    await waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0].body).toEqual(
      expect.objectContaining({
        kind: "LEFT_AT_DOOR",
        lines: [{ bottleTypeCode: "B20", filled: 2, empties: 0 }],
        note: "Left at the gate",
        phoneConfirmed: true,
      }),
    );
  });

  it("with no bottles the money is saved as a payment", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText("W.M. Sunil Perera"));
    await user.clear(screen.getByLabelText("Filled left – 20L"));
    await user.clear(screen.getByLabelText("Empties collected – 20L"));
    expect(screen.getByRole("button", { name: "Save payment" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("Money received"), "500");
    await user.click(screen.getByRole("button", { name: "Save payment" }));
    await waitFor(() => expect(posts).toHaveLength(1));
    expect(posts[0]).toEqual({
      path: "/api/delivery/mobile/payments",
      body: { customerId: 1, amount: 500, method: "CASH" },
    });
    expect(await screen.findByTestId("mob-saved")).toHaveTextContent("RC-00099");
  });

  it("a second bill for a customer already done today needs the tick", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByText("Sampath Grocery"));
    expect(screen.getByText(/Already entered today: B000009 \(Exchange\)/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Save delivery" }));
    expect(screen.getByText(/to save a second bill/)).toBeInTheDocument();
    expect(posts).toHaveLength(0);
    await user.click(screen.getByLabelText(/This is another/));
    await user.click(screen.getByRole("button", { name: "Save delivery" }));
    await waitFor(() => expect(posts).toHaveLength(1));
  });
});

describe("My day", () => {
  it("shows the money by method, the bottles and cancelled bills", () => {
    const day: MyDay = {
      date: TODAY,
      bills: 1,
      payments: 1,
      bottles: [{ bottleTypeCode: "B20", label: "20L", filled: 3, empties: 2 }],
      money: { cash: 500, bankTransfer: 1050, cheque: 0, total: 1550 },
      entries: [
        {
          entry: "PAYMENT",
          id: 5,
          number: "RC-00099",
          kindLabel: "Payment",
          at: new Date().toISOString(),
          customerId: 1,
          customerCode: "C0001",
          customerName: "W.M. Sunil Perera",
          filled: 0,
          empties: 0,
          amount: null,
          received: 500,
          method: "Cash",
          reference: null,
          cancelled: false,
        },
        {
          entry: "BILL",
          id: 7,
          number: "B000100",
          kindLabel: "Exchange",
          at: new Date().toISOString(),
          customerId: 3,
          customerCode: "C0003",
          customerName: "Sampath Grocery",
          filled: 1,
          empties: 1,
          amount: 350,
          received: 350,
          method: "Bank transfer",
          reference: "TRF-1",
          cancelled: true,
        },
      ],
    };
    render(<DaySummary day={day} />);
    expect(screen.getByText("Cash to hand over").parentElement).toHaveTextContent("Rs. 500.00");
    expect(screen.getByText("Total received").parentElement).toHaveTextContent("Rs. 1,550.00");
    expect(screen.getByTestId("mob-day-bottles")).toHaveTextContent(
      "20L: 3 filled left · 2 empties collected",
    );
    expect(screen.getByText("Cancelled by office")).toBeInTheDocument();
    expect(screen.getByText(/Bank transfer \(TRF-1\)/)).toBeInTheDocument();
  });
});
