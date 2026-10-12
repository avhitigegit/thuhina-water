import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PageActionsSlotContext } from "@/components/layout/PageActions";
import { AccessProvider } from "@/features/auth/access";
import type {
  DueCustomer,
  DueList,
  MeResponse,
  Planning as PlanningData,
  PlanRow,
  Requirement,
} from "@/lib/api/types";
import { addDaysIso, formatDate, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { DailyList } from "./DailyList";
import { Planning } from "./Planning";

vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams("") }));

const TODAY = todayIso();
const TOMORROW = addDaysIso(TODAY, 1);

function me(role: "ADMIN" | "DELIVERY_STAFF"): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role, roleName: role },
    mustChangePassword: false,
    landingPage: "/delivery/daily-list",
    permissions:
      role === "ADMIN"
        ? [P.SALES_VIEW, P.SALES_EDIT, P.DELIVERY_PLANNING_VIEW, P.DELIVERY_LIST_VIEW, P.CUSTOMERS_VIEW]
        : [P.DELIVERY_LIST_VIEW],
    menu: [],
  } as MeResponse;
}

function due(id: number, name: string, over: Partial<DueCustomer> = {}, held = 2): DueCustomer {
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
    noBottles: held === 0,
    listed: held > 0,
    enteredBills: [],
    bottles: [
      {
        bottleTypeCode: "B20",
        label: "20L",
        usual: 2,
        held,
        toCollect: 0,
        owed: 0,
        filled: 2,
        empties: 2,
        price: null,
        agreedPrice: false,
        standardPrice: null,
      },
    ],
    ...over,
  };
}

const B20 = (qty: number) => ({ bottleTypeCode: "B20", label: "20L", qty });

const LIST: DueList = {
  date: TODAY,
  dayName: "Monday",
  today: true,
  counts: { due: 4, listed: 2, entered: 1, noBottles: 1, overdue: 1 },
  totals: { load: [B20(8)], toCollect: 2, owed: 1, moneyToCollect: 1400 },
  items: [
    due(1, "W.M. Sunil Perera", {
      notes: "Ring the bell twice",
      balance: 1400,
      bottles: [{ ...due(0, "").bottles[0], toCollect: 2, owed: 1 }],
    }),
    due(2, "Late Lanka", { overdue: true, due: false, nextDeliveryDate: addDaysIso(TODAY, -3) }),
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

const REQ: Requirement = {
  bottleTypeCode: "B20",
  label: "20L",
  name: "20L Bottle",
  filledInStore: 5,
  atFactory: 2,
  emptyInStore: 30,
  days: 14,
  runsOutOn: addDaysIso(TODAY, 7),
  rows: Array.from({ length: 14 }, (_, i) => ({
    date: addDaysIso(TODAY, i),
    dayName: "Monday",
    customers: i % 7 === 0 ? 2 : 0,
    needed: i % 7 === 0 ? 4 : 0,
    runningTotal: i < 7 ? 4 : 8,
    filledLeft: i < 7 ? 1 : -3,
    withFactory: i < 7 ? 3 : -1,
  })),
};

function planRow(id: number, name: string, over: Partial<PlanRow> = {}): PlanRow {
  return {
    id,
    code: "C" + String(id).padStart(4, "0"),
    name,
    address: id + ", Lake Road",
    phone: "071 000 00" + String(id).padStart(2, "0"),
    payType: "CASH",
    payTypeLabel: "Cash",
    deliveryDay: 2,
    dayName: "Tuesday",
    cycleWeeks: 1,
    nextDeliveryDate: TOMORROW,
    last: null,
    due: true,
    overdue: false,
    entered: false,
    enteredBills: [],
    thisDate: [B20(2)],
    held: [B20(2)],
    next: addDaysIso(TOMORROW, 7),
    weekAfter: addDaysIso(TOMORROW, 14),
    toCollect: 0,
    owed: 0,
    balance: 0,
    status: "DUE",
    statusLabel: "Due",
    overLimit: false,
    ...over,
  };
}

const PLAN: PlanningData = {
  date: TOMORROW,
  dayName: "Tuesday",
  today: false,
  show: "due",
  customers: 3,
  kpis: { due: 3, entered: 0, missed: 0, needed: [B20(6)], toCollect: 2, owed: 0, moneyToCollect: 2500 },
  ready: [
    {
      bottleTypeCode: "B20",
      label: "20L",
      name: "20L Bottle",
      needed: 6,
      neededBefore: 4,
      filledInStore: 8,
      atFactory: 5,
      shortBy: 2,
      ready: false,
      factoryCovers: true,
    },
    {
      bottleTypeCode: "B10",
      label: "10L",
      name: "10L Bottle",
      needed: 1,
      neededBefore: 0,
      filledInStore: 9,
      atFactory: 0,
      shortBy: 0,
      ready: true,
      factoryCovers: false,
    },
  ],
  routes: [
    {
      areaId: 1,
      areaName: "Kottawa",
      due: 2,
      toLoad: 2,
      load: [B20(4)],
      toCollect: 2,
      rows: [
        planRow(1, "Kamal Stores", {
          last: { date: TODAY, filled: [B20(2)], leftAtDoor: true },
          toCollect: 2,
        }),
        planRow(2, "Vertex Software", {
          payType: "CREDIT",
          payTypeLabel: "Credit",
          balance: 2500,
          overLimit: true,
          cycleWeeks: 2,
          next: addDaysIso(TOMORROW, 14),
          weekAfter: addDaysIso(TOMORROW, 28),
        }),
      ],
    },
    {
      areaId: 2,
      areaName: "Maharagama",
      due: 1,
      toLoad: 1,
      load: [B20(2)],
      toCollect: 0,
      rows: [planRow(5, "New Home", { held: [], status: "FIRST_DELIVERY", statusLabel: "First delivery" })],
    },
  ],
};

const ALL: PlanningData = {
  ...PLAN,
  show: "all",
  customers: 4,
  routes: [
    ...PLAN.routes,
    {
      areaId: 3,
      areaName: "Pannipitiya",
      due: 0,
      toLoad: 0,
      load: [],
      toCollect: 0,
      rows: [
        planRow(7, "Not Today", { due: false, thisDate: [], status: "NOT_DUE", statusLabel: "Not due" }),
      ],
    },
  ],
};

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let calls: string[] = [];

function server() {
  return vi.fn<Fetch>(async (url) => {
    calls.push(url);
    const [path, qs] = url.split("?");
    const q = new URLSearchParams(qs);
    const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), { status });
    if (path === "/api/areas")
      return json([
        { id: 1, name: "Kottawa", active: true, version: 0 },
        { id: 2, name: "Maharagama", active: true, version: 0 },
      ]);
    if (path === "/api/bottle-types")
      return json([{ code: "B20", name: "20L Bottle", litres: 20, active: true, deposit: 1000, version: 0 }]);
    if (path === "/api/delivery/due") return json({ ...LIST, date: q.get("date") ?? TODAY });
    if (path === "/api/delivery/requirement") return json(REQ);
    if (path === "/api/delivery/planning") return json(q.get("show") === "all" ? ALL : PLAN);
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

function renderPage(children: ReactNode, role: "ADMIN" | "DELIVERY_STAFF" = "ADMIN") {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(role), viewOnly: role !== "ADMIN" }}>
        <Shell>{children}</Shell>
      </AccessProvider>
    </QueryClientProvider>,
  );
}

const rowOf = (name: string) => screen.getByText(name).closest("tr") as HTMLElement;
let opened: string[] = [];

beforeEach(() => {
  calls = [];
  opened = [];
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

describe("Daily Delivery List", () => {
  it("shows today's customers with KPIs, balances and status; the Admin can enter bills", async () => {
    renderPage(<DailyList today={TODAY} />);
    expect(await screen.findByText(`Monday ${formatDate(TODAY)} – 4 customers`)).toBeInTheDocument();
    expect(screen.getByText("Load 20L filled").nextSibling).toHaveTextContent("8");
    expect(screen.getByText("1 done · 1 overdue")).toBeInTheDocument();
    expect(screen.getByText("Money to collect").nextSibling).toHaveTextContent("Rs. 1,400.00");

    const sunil = rowOf("W.M. Sunil Perera");
    expect(sunil).toHaveTextContent("1, Temple Road, Nugegoda");
    expect(sunil).toHaveTextContent("Ring the bell twice");
    expect(sunil).toHaveTextContent("Rs. 1,400.00");
    expect(within(sunil).getByRole("link", { name: "Enter bill" })).toHaveAttribute(
      "href",
      "/sales/enter-bills?cust=C0001",
    );
    expect(rowOf("Late Lanka")).toHaveTextContent(`Missed ${formatDate(addDaysIso(TODAY, -3))}`);
    expect(rowOf("Late Lanka")).toHaveClass("row-bad");
    const entered = rowOf("Sampath Grocery");
    expect(entered).toHaveClass("row-ok");
    expect(entered).toHaveTextContent(`bill B000009 · next ${formatDate(TODAY)}`);
    expect(within(entered).queryByRole("link", { name: "Enter bill" })).toBeNull();
    expect(rowOf("Anoma Wickramaratne")).toHaveTextContent("New – deposit");
    expect(screen.getByRole("link", { name: "Enter bills" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Upcoming bottle requirement" })).toBeInTheDocument();
  });

  it("moves day by day; no Enter bill for a future date; prints the sheet for the date and area", async () => {
    const user = userEvent.setup();
    renderPage(<DailyList today={TODAY} />);
    await screen.findByText(`Monday ${formatDate(TODAY)} – 4 customers`);
    await user.click(screen.getByRole("button", { name: "Next day" }));
    await screen.findByText(`Monday ${formatDate(TOMORROW)} – 4 customers`);
    expect(calls.some((c) => c.includes("/api/delivery/due") && c.includes("date=" + TOMORROW))).toBe(true);
    expect(screen.queryByRole("link", { name: "Enter bill" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Enter bills" })).toBeNull();

    await user.selectOptions(await screen.findByLabelText("Area"), "2");
    await waitFor(() => expect(calls.some((c) => c.includes("area=2"))).toBe(true));
    await user.click(screen.getByRole("button", { name: "Print delivery sheet" }));
    expect(opened).toEqual([`/api/print/delivery-sheet?date=${TOMORROW}&area=2`]);
  });

  it("Delivery Staff only views and prints: no Enter bill links, no requirement tab", async () => {
    renderPage(<DailyList today={TODAY} />, "DELIVERY_STAFF");
    await screen.findByText(`Monday ${formatDate(TODAY)} – 4 customers`);
    expect(screen.queryByRole("link", { name: "Enter bill" })).toBeNull();
    expect(screen.queryByRole("link", { name: "Enter bills" })).toBeNull();
    expect(screen.queryByRole("tab")).toBeNull();
    expect(screen.getByRole("button", { name: "Print delivery sheet" })).toBeInTheDocument();
    expect(calls.some((c) => c.includes("/api/delivery/requirement"))).toBe(false);
  });

  it("the requirement tab shows when filled bottles run out", async () => {
    const user = userEvent.setup();
    renderPage(<DailyList today={TODAY} />);
    await user.click(await screen.findByRole("tab", { name: "Upcoming bottle requirement" }));
    expect(
      await screen.findByText(
        (_, el) =>
          el?.textContent === "20L stock now: 5 filled in store, 2 at the factory, 30 empties in store.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByText(/Filled bottles run out on/)).toHaveTextContent(
      `Filled bottles run out on Monday ${formatDate(addDaysIso(TODAY, 7))}. Send more empties to the factory before then.`,
    );
    const rows = screen.getAllByRole("row");
    expect(rows).toHaveLength(15);
    expect(rows[8]).toHaveTextContent("-3");
  });
});

describe("Delivery Planning", () => {
  it("opens tomorrow with KPIs, ready check and customers grouped by route", async () => {
    renderPage(<Planning today={TODAY} />);
    expect(await screen.findByText(`Tuesday ${formatDate(TOMORROW)} – 3 customers due`)).toBeInTheDocument();
    expect(calls.some((c) => c.includes("/api/delivery/planning") && c.includes("date=" + TOMORROW))).toBe(
      true,
    );
    expect(screen.getByText("0 already entered · 0 missed earlier")).toBeInTheDocument();
    expect(screen.getByText("20L bottles needed").nextSibling).toHaveTextContent("6");

    const short = screen.getByText(/Short by 2/).closest(".banner") as HTMLElement;
    expect(short).toHaveClass("bad");
    expect(short).toHaveTextContent(
      `✖ 20L Bottle: 6 needed on ${formatDate(TOMORROW)} · 8 filled in store now (4 needed before then) · 5 at the factory. ` +
        "Short by 2. Bottles at the factory will cover it if they come back in time.",
    );
    expect(screen.getByText(/10L Bottle:/).closest(".banner")).toHaveTextContent("Ready.");

    expect(screen.getByText("Kottawa – 2 due · load 4×20L")).toBeInTheDocument();
    expect(screen.getByText("Maharagama – 1 due · load 2×20L")).toBeInTheDocument();
    const kamal = rowOf("Kamal Stores");
    expect(kamal).toHaveTextContent(`${formatDate(TODAY)}2×20L (left at door)`);
    expect(kamal).toHaveTextContent("1, Lake Road · Tue, weekly");
    expect(kamal).toHaveTextContent(formatDate(addDaysIso(TOMORROW, 7)));
    const vertex = rowOf("Vertex Software");
    expect(vertex).toHaveTextContent("every 2 wks");
    expect(vertex).toHaveTextContent("Over limit");
    expect(vertex).toHaveTextContent(formatDate(addDaysIso(TOMORROW, 28)));
    expect(rowOf("New Home")).toHaveTextContent("First delivery");
  });

  it("switches to all customers (not due muted) and prints the load sheet", async () => {
    const user = userEvent.setup();
    renderPage(<Planning today={TODAY} />);
    await screen.findByText(`Tuesday ${formatDate(TOMORROW)} – 3 customers due`);
    await user.selectOptions(screen.getByLabelText("Show"), "all");
    expect(
      await screen.findByText(`Tuesday ${formatDate(TOMORROW)} – 4 customers (3 due on this date)`),
    ).toBeInTheDocument();
    const notDue = rowOf("Not Today");
    expect(notDue).toHaveClass("row-muted");
    expect(notDue).toHaveTextContent("Not due");

    await user.click(screen.getByRole("button", { name: "Next week" }));
    await waitFor(() => expect(calls.some((c) => c.includes("date=" + addDaysIso(TODAY, 7)))).toBe(true));
    await user.click(screen.getByRole("button", { name: "Print load sheet" }));
    expect(opened).toEqual([`/api/print/load-sheet?date=${addDaysIso(TODAY, 7)}`]);
  });
});
