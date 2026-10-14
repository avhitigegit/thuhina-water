import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PageActionsSlotContext } from "@/components/layout/PageActions";
import { Toaster } from "@/components/shared/Toaster";
import { AccessProvider } from "@/features/auth/access";
import type { BatchView, DispatchView, FactoryMonth, MeResponse, ProductionSummary } from "@/lib/api/types";
import { todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { Production } from "./Production";

let search = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));

const TODAY = todayIso();

function me(role: "ADMIN" | "ACCOUNTANT"): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role, roleName: role },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions:
      role === "ADMIN"
        ? [P.PRODUCTION_VIEW, P.PRODUCTION_EDIT, P.PARTNERS_VIEW]
        : [P.PRODUCTION_VIEW, P.PARTNERS_VIEW],
    menu: [],
  } as MeResponse;
}

const SUMMARY: ProductionSummary = {
  bottles: [
    { bottleTypeCode: "B20", label: "20L", name: "20L Bottle", empty: 48, atFactory: 66 },
    { bottleTypeCode: "B10", label: "10L", name: "10L Bottle", empty: 10, atFactory: 8 },
  ],
  factories: [
    { id: 1, code: "F01", name: "AquaSeal", atFactory: 74, owed: 72480, active: true },
    { id: 2, code: "F02", name: "Pure Lanka", atFactory: 0, owed: 12376, active: true },
  ],
};

function batch(id: number, over: Partial<BatchView> = {}): BatchView {
  return {
    id,
    batchNo: "FB-00" + id,
    dispatchId: id,
    dispatchNo: "FD-00" + id,
    date: "2026-09-26",
    factory: { id: 1, code: "F01", name: "AquaSeal" },
    bottleTypeCode: "B20",
    bottleLabel: "20L",
    bottleName: "20L Bottle",
    qtySent: 300,
    filled: 290,
    rejected: 4,
    atFactory: 6,
    charge: 60,
    cost: 17400,
    status: "PARTLY_RETURNED",
    statusLabel: "Partly returned",
    vehicleNo: "LH-4521",
    note: null,
    returns: [],
    ...over,
  };
}

const BATCHES = [
  batch(17),
  batch(18, {
    factory: { id: 2, code: "F02", name: "Pure Lanka" },
    bottleTypeCode: "B10",
    bottleLabel: "10L",
    qtySent: 40,
    filled: 40,
    rejected: 0,
    atFactory: 0,
    charge: 34,
    cost: 1360,
    status: "RETURNED",
    statusLabel: "Returned",
  }),
];

const MONTHS: FactoryMonth[] = [
  {
    factoryId: 1,
    factoryName: "AquaSeal",
    period: "2026-09",
    periodLabel: "September 2026",
    filled: 1208,
    rejected: 16,
    cost: 72480,
    paid: 0,
    due: 72480,
    status: "UNPAID",
    statusLabel: "Unpaid",
  },
  {
    factoryId: 1,
    factoryName: "AquaSeal",
    period: "2026-08",
    periodLabel: "August 2026",
    filled: 1462,
    rejected: 18,
    cost: 87720,
    paid: 87720,
    due: 0,
    status: "PAID",
    statusLabel: "Paid",
  },
];

const FACTORIES = [
  {
    id: 1,
    code: "F01",
    name: "AquaSeal",
    active: true,
    charges: [
      { bottleTypeCode: "B20", bottleLabel: "20L", charge: 60, effectiveFrom: "2026-01-01" },
      { bottleTypeCode: "B10", bottleLabel: "10L", charge: 35, effectiveFrom: "2026-01-01" },
    ],
  },
  {
    id: 2,
    code: "F02",
    name: "Pure Lanka",
    active: true,
    charges: [{ bottleTypeCode: "B20", bottleLabel: "20L", charge: 62, effectiveFrom: "2026-01-01" }],
  },
];

const SENT: DispatchView = {
  id: 20,
  dispatchNo: "FD-0020",
  date: TODAY,
  factory: { id: 1, code: "F01", name: "AquaSeal" },
  factoryAddress: null,
  factoryContact: null,
  factoryPhone: null,
  vehicleNo: null,
  note: null,
  createdByName: "N",
  totalBottles: 40,
  batches: [batch(21, { batchNo: "FB-0021", qtySent: 40, filled: 0, rejected: 0, atFactory: 40 })],
};

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let calls: { method: string; path: string; body: unknown }[] = [];
let opened: string[] = [];

function server() {
  return vi.fn<Fetch>(async (url, init) => {
    const [path] = url.split("?");
    const method = init?.method ?? "GET";
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    calls.push({ method, path, body });
    const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status });
    if (method === "GET") {
      if (path === "/api/production/summary") return json(SUMMARY);
      if (path === "/api/factory-batches") return json(BATCHES);
      if (path === "/api/factory-balances") return json(MONTHS);
      if (path === "/api/factory-payments") return json([]);
      if (path === "/api/factories") return json(FACTORIES);
    }
    if (path === "/api/factory-dispatches") return json(SENT, 201);
    if (path === "/api/factory-batches/17/returns")
      return json(
        {
          saved: { cost: 240, returnNo: "FR-0019" },
          batch: { ...batch(17), atFactory: 0 },
          damageNo: "DMG-0010",
        },
        201,
      );
    if (path === "/api/factory-payments") return json({ id: 2, fpNo: "FP-0002" }, 201);
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

function renderPage(role: "ADMIN" | "ACCOUNTANT" = "ADMIN") {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(role), viewOnly: role !== "ADMIN" }}>
        <Shell>
          <Production today={TODAY} />
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

describe("Filling Factory", () => {
  it("shows the KPIs and the batches with what is still at the factory", async () => {
    const user = userEvent.setup();
    renderPage();
    expect((await screen.findByText("Empty 20L in store")).nextSibling).toHaveTextContent("48");
    expect(screen.getByText("20L at factory").nextSibling).toHaveTextContent("66");
    expect(screen.getByText("Owed to AquaSeal").nextSibling).toHaveTextContent("Rs. 72,480.00");
    const row = (await screen.findByText("FB-0017")).closest("tr") as HTMLElement;
    expect(row).toHaveTextContent("FD-0017");
    expect(row).toHaveTextContent("Partly returned");
    expect(row).toHaveTextContent("Rs. 17,400.00");
    expect(within(row).getByRole("button", { name: "Receive" })).toBeInTheDocument();
    const returned = screen.getByText("FB-0018").closest("tr") as HTMLElement;
    expect(within(returned).queryByRole("button", { name: "Receive" })).not.toBeInTheDocument();
    await user.click(within(returned).getByRole("button", { name: "Print" }));
    expect(opened).toEqual(["/api/print/dispatch-note/FD-0018"]);
  });

  it("sends empties of every bottle type in one dispatch and offers the dispatch note", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Send empties" }));
    const dialog = await screen.findByRole("dialog", { name: "Send empties to the factory" });
    const b20 = within(dialog).getByLabelText("Sent now 20L");
    expect(b20).toHaveValue(48);
    await waitFor(() => expect(within(dialog).getByLabelText("Filling factory")).toHaveValue("1"));
    expect(within(dialog).getByTestId("send-total")).toHaveTextContent("58");

    await user.clear(b20);
    await user.type(b20, "50");
    expect(within(dialog).getByText(/Only 48 empty 20L in store/)).toBeInTheDocument();
    // Pure Lanka has no charge for 10L.
    await user.selectOptions(within(dialog).getByLabelText("Filling factory"), "2");
    expect(within(dialog).getByText(/Pure Lanka has no filling charge for 10L/)).toBeInTheDocument();
    await user.selectOptions(within(dialog).getByLabelText("Filling factory"), "1");
    await user.clear(b20);
    await user.type(b20, "40");
    await user.clear(within(dialog).getByLabelText("Sent now 10L"));
    await user.type(within(dialog).getByLabelText("Sent now 10L"), "0");
    expect(within(dialog).getByText(/40 empties go out in one dispatch/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Send" }));

    await waitFor(() => expect(posted("/api/factory-dispatches")).toHaveLength(1));
    expect(posted("/api/factory-dispatches")[0].body).toMatchObject({
      factoryId: 1,
      date: TODAY,
      lines: [
        { bottleTypeCode: "B20", qty: 40 },
        { bottleTypeCode: "B10", qty: 0 },
      ],
    });
    const sent = await screen.findByRole("dialog", { name: "Dispatch FD-0020 sent" });
    expect(sent).toHaveTextContent("40 × 20L – batch FB-0021");
    await user.click(within(sent).getByRole("button", { name: "Print dispatch note" }));
    expect(opened).toEqual(["/api/print/dispatch-note/FD-0020"]);
  });

  it("receives filled bottles with rejects and shows the cost", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = (await screen.findByText("FB-0017")).closest("tr") as HTMLElement;
    await user.click(within(row).getByRole("button", { name: "Receive" }));
    const dialog = await screen.findByRole("dialog", { name: "Receive filled bottles" });
    expect(within(dialog).getByLabelText("Batch")).toHaveValue("17");
    expect(within(dialog).getByLabelText("Filled bottles received")).toHaveValue(6);
    const filled = within(dialog).getByLabelText("Filled bottles received");
    await user.clear(filled);
    await user.type(filled, "4");
    const rej = within(dialog).getByLabelText("Rejected / damaged");
    await user.clear(rej);
    await user.type(rej, "1");
    expect(within(dialog).getByTestId("left-after")).toHaveTextContent("1");
    expect(within(dialog).getByTestId("return-cost")).toHaveTextContent("Rs. 240.00");
    await user.type(within(dialog).getByLabelText("Reject reason"), "Failed seal test");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await waitFor(() => expect(posted("/api/factory-batches/17/returns")).toHaveLength(1));
    expect(posted("/api/factory-batches/17/returns")[0].body).toEqual({
      date: TODAY,
      filled: 4,
      rejected: 1,
      rejectReason: "Failed seal test",
    });
    expect(await screen.findByText("Received – filling cost Rs. 240.00")).toBeInTheDocument();
  });

  it("shows the filling cost by month and pays an unpaid month", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("tab", { name: "Factory payments" }));
    const sep = (await screen.findByText("September 2026")).closest("tr") as HTMLElement;
    expect(sep).toHaveTextContent("Unpaid");
    expect(screen.getByText("August 2026").closest("tr") as HTMLElement).toHaveTextContent("Paid");
    await user.click(screen.getByRole("button", { name: "+ Record payment" }));
    const dialog = await screen.findByRole("dialog", { name: "Record factory payment" });
    expect(within(dialog).getByLabelText("Factory and month")).toHaveDisplayValue(
      "AquaSeal – September 2026 – Rs. 72,480.00 due",
    );
    expect(within(dialog).getByLabelText("Amount (Rs.)")).toHaveValue(72480);
    await user.type(within(dialog).getByLabelText("Cheque no. / ref."), "COM-TRF-1");
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    await waitFor(() => expect(posted("/api/factory-payments")).toHaveLength(1));
    expect(posted("/api/factory-payments")[0].body).toMatchObject({
      factoryId: 1,
      period: "2026-09",
      amount: 72480,
      method: "BANK_TRANSFER",
      reference: "COM-TRF-1",
    });
  });

  it("opens Send empties from a link (?send=1)", async () => {
    search = "send=1";
    renderPage();
    expect(await screen.findByRole("dialog", { name: "Send empties to the factory" })).toBeInTheDocument();
  });

  it("the Accountant sees everything but cannot send, receive or pay", async () => {
    const user = userEvent.setup();
    search = "send=1";
    renderPage("ACCOUNTANT");
    const row = (await screen.findByText("FB-0017")).closest("tr") as HTMLElement;
    expect(within(row).queryByRole("button", { name: "Receive" })).not.toBeInTheDocument();
    expect(within(row).getByRole("button", { name: "Print" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Send empties" })).not.toBeInTheDocument();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "Factory payments" }));
    expect(await screen.findByText("September 2026")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ Record payment" })).not.toBeInTheDocument();
  });
});
