import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState, type ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PageActionsSlotContext } from "@/components/layout/PageActions";
import { Toaster } from "@/components/shared/Toaster";
import { AccessProvider } from "@/features/auth/access";
import type { CqItemOption, CqListResponse, CqView, MeResponse } from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { Quotations } from "./Quotations";

let search = "";
vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams(search) }));

const TODAY = "2026-10-14";

function me(role: "ADMIN" | "ACCOUNTANT"): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role, roleName: role },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions:
      role === "ADMIN"
        ? [P.QUOTATIONS_VIEW, P.QUOTATIONS_EDIT, P.CUSTOMERS_VIEW, P.CUSTOMERS_EDIT, P.CUSTOMER_PRICES_EDIT]
        : [P.QUOTATIONS_VIEW, P.CUSTOMERS_VIEW],
    menu: [],
  } as MeResponse;
}

const LIST: CqListResponse = {
  kpis: {
    waiting: 1,
    waitingTotal: 93000,
    drafts: 1,
    accepted: 1,
    acceptedTotal: 96400,
    expiredOrRejected: 2,
  },
  items: [
    {
      id: 5,
      cqNo: "QT-2026-005",
      quoteDate: "2026-10-01",
      toName: "Vertex Software (Pvt) Ltd",
      customerId: 4,
      customerCode: "C0004",
      contact: "Ms. Hiruni Peris",
      designation: "Office Manager",
      subject: "Dispensers for the new 4th floor",
      lineCount: 3,
      customerTypeName: "Office",
      total: 108400,
      validUntil: "2026-10-31",
      status: "DRAFT",
      shown: "DRAFT",
      shownLabel: "Draft",
      sentViaText: null,
      nextStep: "Send to the customer",
    },
    {
      id: 3,
      cqNo: "QT-2026-003",
      quoteDate: "2026-08-20",
      toName: "Royal Seaside Hotel",
      customerId: null,
      customerCode: null,
      contact: "Mr. Imran Cassim",
      designation: "Purchasing Officer",
      subject: "Drinking water for staff areas",
      lineCount: 2,
      customerTypeName: "Shop",
      total: 13920,
      validUntil: "2026-09-19",
      status: "SENT",
      shown: "EXPIRED",
      shownLabel: "Expired",
      sentViaText: "by email",
      nextStep: "Record the answer or make a new copy",
    },
  ],
};

function view(over: Partial<CqView>): CqView {
  return {
    id: 1,
    cqNo: "QT-2026-001",
    customerId: null,
    customerCode: null,
    orgName: "Divisional Secretariat – Maharagama",
    contact: "Mrs. W.A. Dilrukshi",
    designation: "Administrative Officer",
    address: "High Level Road, Maharagama",
    areaId: 2,
    areaName: "Maharagama",
    phone: "011 285 0153",
    email: "ds@gov.lk",
    customerTypeId: 3,
    customerTypeName: "Office",
    quoteDate: "2026-09-08",
    validUntil: "2026-10-08",
    subject: "Supply of drinking water",
    deliveryText: "Every Monday",
    termsDays: 30,
    termsLabel: "30 days",
    conditions: "Prices include delivery.",
    status: "DRAFT",
    shown: "DRAFT",
    shownLabel: "Draft",
    sentVia: null,
    sentViaText: null,
    sentOn: null,
    acceptedOn: null,
    rejectReason: null,
    pricesAppliedOn: null,
    copyOfNo: null,
    lines: [
      {
        lineNo: 1,
        kind: "WATER",
        bottleTypeCode: "B20",
        productCode: null,
        description: "20L Bottle – drinking water (refill, delivered)",
        qty: 40,
        unit: "per month",
        unitPrice: 300,
        amount: 12000,
        currentPrice: 320,
        currentAgreed: false,
        belowCurrent: true,
      },
    ],
    total: 12000,
    history: [
      { at: "2026-09-08T04:30:00Z", event: "CREATED", label: "Draft", note: null, byName: "Nimal Perera" },
    ],
    hasWater: true,
    canEdit: true,
    canSend: true,
    canAnswer: false,
    canRegister: false,
    canApplyPrices: false,
    emailSubject: "Quotation QT-2026-001 – Thuhina Water",
    emailBody: "Dear Mrs. W.A. Dilrukshi,",
    agreedPrices: [],
    registerNotes: "From quotation QT-2026-001",
    createdByName: "Nimal Perera",
    version: 0,
    ...over,
  };
}

const ITEMS = (type: string | null, customer: string | null): CqItemOption[] => {
  const water = customer ? 300 : type === "4" ? 285 : 320;
  return [
    {
      key: "WATER|B20",
      kind: "WATER",
      bottleTypeCode: "B20",
      productCode: null,
      label: "20L water (refill, delivered)",
      description: "20L Bottle – drinking water (refill, delivered)",
      unit: "per month",
      currentPrice: water,
      agreed: !!customer,
    },
    {
      key: "DEPOSIT|B20",
      kind: "DEPOSIT",
      bottleTypeCode: "B20",
      productCode: null,
      label: "20L bottle deposit",
      description: "20L Bottle – bottle deposit (one time)",
      unit: "bottles",
      currentPrice: 1000,
      agreed: false,
    },
    {
      key: "PRODUCT|P01",
      kind: "PRODUCT",
      bottleTypeCode: null,
      productCode: "P01",
      label: "Hot & Cold Water Dispenser (Floor)",
      description: "Hot & Cold Water Dispenser (Floor)",
      unit: "units",
      currentPrice: 38500,
      agreed: false,
    },
    {
      key: "OTHER",
      kind: "OTHER",
      bottleTypeCode: null,
      productCode: null,
      label: "Other (type a description)",
      description: "",
      unit: "",
      currentPrice: null,
      agreed: false,
    },
  ];
};

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let calls: { method: string; url: string; path: string; body: unknown }[] = [];
let opened: string[] = [];
let current: CqView = view({});

function server() {
  return vi.fn<Fetch>(async (url, init) => {
    const [path, qs] = url.split("?");
    const p = new URLSearchParams(qs);
    const method = init?.method ?? "GET";
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : undefined;
    calls.push({ method, url, path, body });
    const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status });
    if (method === "GET") {
      if (path === "/api/customer-quotations") return json(LIST);
      if (path === "/api/customer-quotations/items")
        return json(ITEMS(p.get("customerTypeId"), p.get("customerId")));
      if (/^\/api\/customer-quotations\/\d+$/.test(path)) return json(current);
      if (path === "/api/customer-types")
        return json([
          { id: 1, name: "Household", description: null, active: true, activeCustomers: 1, version: 0 },
          { id: 3, name: "Office", description: null, active: true, activeCustomers: 1, version: 0 },
          { id: 4, name: "Factory", description: null, active: true, activeCustomers: 1, version: 0 },
        ]);
      if (path === "/api/areas")
        return json([{ id: 2, name: "Maharagama", active: true, activeCustomers: 1, version: 0 }]);
      if (path === "/api/customers/lookup")
        return json([
          {
            id: 4,
            code: "C0004",
            name: "Vertex Software (Pvt) Ltd",
            phone: "011 452 9031",
            areaName: "Rajagiriya",
            status: "ACTIVE",
          },
        ]);
      if (path === "/api/customers/4")
        return json({ id: 4, code: "C0004", customerTypeId: 3, name: "Vertex Software (Pvt) Ltd" });
    }
    if (path === "/api/customer-quotations" && method === "POST")
      return json(
        { quotation: view({ id: 6, cqNo: "QT-2026-006" }), message: "QT-2026-006 saved as Draft" },
        201,
      );
    const step = /^\/api\/customer-quotations\/\d+\/(send|accept|reject|apply-prices)$/.exec(path);
    if (step) return json({ quotation: current, message: "Done " + step[1] });
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
          <Quotations today={TODAY} />
        </Shell>
        <Toaster />
      </AccessProvider>
    </QueryClientProvider>,
  );
}

const posted = (re: RegExp) => calls.filter((c) => c.method === "POST" && re.test(c.path));

beforeEach(() => {
  calls = [];
  opened = [];
  search = "";
  current = view({});
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

// The pop-ups take many typed steps; under a full parallel run they need more than the default 5 s.
describe("Customer Quotations", () => {
  it("lists the quotations with KPIs, status, next step and a status filter", async () => {
    const user = userEvent.setup();
    renderPage();
    expect((await screen.findByText("Waiting for an answer")).parentElement).toHaveTextContent(
      "1Rs. 93,000.00",
    );
    const hotel = (await screen.findByText("Royal Seaside Hotel")).closest("tr") as HTMLElement;
    expect(hotel).toHaveTextContent("Expired");
    expect(hotel).toHaveTextContent("New");
    expect(hotel).toHaveTextContent("Record the answer or make a new copy");
    const vertex = screen.getByText("Vertex Software (Pvt) Ltd").closest("tr") as HTMLElement;
    expect(vertex).toHaveTextContent("C0004");
    expect(vertex).toHaveTextContent("3 lines · Office prices");
    await user.selectOptions(screen.getByLabelText("Status"), "EXPIRED");
    await waitFor(() => expect(calls.some((c) => /status=EXPIRED/.test(c.url))).toBe(true));
  });

  it("new quotation: prices start at the type's standard price, follow the type until changed by hand", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "+ New quotation" }));
    const dialog = await screen.findByRole("dialog", { name: "New quotation" });
    // Office (default for a new organisation): water 320, deposits 1,000.
    await waitFor(() => expect(within(dialog).getByLabelText("Unit price 1")).toHaveValue(320));
    expect(within(dialog).getByLabelText("Qty 1")).toHaveValue(40);
    expect(within(dialog).getByLabelText("Unit price 2")).toHaveValue(1000);
    expect(within(dialog).getByTestId("now-1")).toHaveTextContent("standard price");
    expect(within(dialog).getByTestId("cq-total")).toHaveTextContent("Rs. 22,800.00");
    expect(
      (within(dialog).getByLabelText("Terms and conditions (one per line)") as HTMLTextAreaElement).value,
    ).toContain("Prices include delivery within our delivery areas.");

    // The deposit price changed by hand stays; the water price follows the customer type.
    const dep = within(dialog).getByLabelText("Unit price 2");
    await user.clear(dep);
    await user.type(dep, "900");
    await user.selectOptions(within(dialog).getByLabelText("Customer type (for prices)"), "4");
    await waitFor(() => expect(within(dialog).getByLabelText("Unit price 1")).toHaveValue(285));
    expect(dep).toHaveValue(900);
    expect(within(dialog).getByTestId("now-2")).toHaveTextContent("standard Rs. 1,000.00");
    expect(within(dialog).getByTestId("cq-note")).toHaveTextContent(
      "Prices start at the standard Factory price list.",
    );
    expect(within(dialog).getByTestId("cq-note")).toHaveTextContent("1 line is below the current price.");

    // A free-text line.
    await user.click(within(dialog).getByRole("button", { name: "+ Add line" }));
    await user.selectOptions(within(dialog).getByLabelText("Item 3"), "OTHER");
    await user.type(within(dialog).getByLabelText("Description 3"), "Installation");
    await user.type(within(dialog).getByLabelText("Qty 3"), "1");
    await user.type(within(dialog).getByLabelText("Unit price 3"), "2500");

    await user.type(
      within(dialog).getByLabelText("Organisation / name"),
      "Divisional Secretariat – Maharagama",
    );
    await user.type(within(dialog).getByLabelText("Email"), "ds@gov.lk");
    await user.click(within(dialog).getByRole("button", { name: "Save as draft" }));
    await waitFor(() => expect(posted(/^\/api\/customer-quotations$/)).toHaveLength(1));
    expect(posted(/^\/api\/customer-quotations$/)[0].body).toMatchObject({
      orgName: "Divisional Secretariat – Maharagama",
      customerTypeId: 4,
      quoteDate: TODAY,
      validUntil: "2026-11-13",
      termsDays: 30,
      lines: [
        { kind: "WATER", bottleTypeCode: "B20", qty: 40, unitPrice: 285 },
        { kind: "DEPOSIT", bottleTypeCode: "B20", qty: 10, unitPrice: 900 },
        { kind: "OTHER", description: "Installation", qty: 1, unitPrice: 2500 },
      ],
    });
    expect(await screen.findByText("QT-2026-006 saved as Draft")).toBeInTheDocument();
    expect(await screen.findByRole("dialog", { name: "Quotation QT-2026-001" })).toBeInTheDocument();
  }, 15000);

  it("a draft is printed, emailed and marked as sent", async () => {
    const user = userEvent.setup();
    search = "id=1";
    renderPage();
    const dialog = await screen.findByRole("dialog", { name: "Quotation QT-2026-001" });
    expect(dialog).toHaveTextContent("standard Rs. 320.00");
    expect(within(dialog).getByTestId("cq-history")).toHaveTextContent("Draft by Nimal Perera");
    expect(within(dialog).getByRole("link", { name: "Email" }).getAttribute("href")).toMatch(
      /^mailto:ds%40gov\.lk\?subject=Quotation%20QT-2026-001/,
    );
    await user.click(within(dialog).getByRole("button", { name: "Print / PDF" }));
    expect(opened).toEqual(["/api/print/customer-quotation/1"]);
    await user.click(within(dialog).getByRole("button", { name: "Next: Mark as sent" }));
    const send = await screen.findByRole("dialog", { name: "Mark QT-2026-001 as sent" });
    expect(within(send).getByLabelText("Sent by")).toHaveValue("EMAIL");
    await user.click(within(send).getByRole("button", { name: "Mark as sent" }));
    await waitFor(() => expect(posted(/\/send$/)).toHaveLength(1));
    expect(posted(/\/send$/)[0].body).toEqual({ via: "EMAIL", date: TODAY });
  }, 15000);

  it("a sent (expired) quotation is answered: not accepted needs a reason", async () => {
    const user = userEvent.setup();
    current = view({
      status: "SENT",
      shown: "EXPIRED",
      shownLabel: "Expired",
      canEdit: false,
      canSend: false,
      canAnswer: true,
      sentVia: "EMAIL",
      sentViaText: "by email",
      sentOn: "2026-09-09",
      validUntil: "2026-10-08",
    });
    search = "id=1";
    renderPage();
    const dialog = await screen.findByRole("dialog", { name: "Quotation QT-2026-001" });
    expect(dialog).toHaveTextContent("this quotation has expired");
    expect(within(dialog).queryByRole("button", { name: "Edit" })).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Not accepted" }));
    const reject = await screen.findByRole("dialog", { name: "QT-2026-001 not accepted" });
    await user.click(within(reject).getByRole("button", { name: "Save" }));
    expect(within(reject).getByText("Enter a reason.")).toBeInTheDocument();
    await user.type(within(reject).getByRole("textbox"), "Price too high");
    await user.click(within(reject).getByRole("button", { name: "Save" }));
    await waitFor(() => expect(posted(/\/reject$/)).toHaveLength(1));
    expect(posted(/\/reject$/)[0].body).toEqual({ reason: "Price too high" });
  });

  it("accepted: a new organisation is registered from it, an existing customer gets the prices from a date", async () => {
    const user = userEvent.setup();
    current = view({
      status: "ACCEPTED",
      shown: "ACCEPTED",
      shownLabel: "Accepted",
      acceptedOn: "2026-09-24",
      canEdit: false,
      canRegister: true,
    });
    search = "id=1";
    const r = renderPage();
    let dialog = await screen.findByRole("dialog", { name: "Quotation QT-2026-001" });
    expect(dialog).toHaveTextContent("Not yet registered as a customer");
    expect(within(dialog).getByRole("link", { name: "Register as customer" })).toHaveAttribute(
      "href",
      "/master-data/customers?fromQuote=1",
    );
    r.unmount();

    current = view({
      status: "ACCEPTED",
      shown: "ACCEPTED",
      shownLabel: "Accepted",
      acceptedOn: "2026-09-24",
      canEdit: false,
      customerId: 4,
      customerCode: "C0004",
      canApplyPrices: true,
    });
    renderPage();
    dialog = await screen.findByRole("dialog", { name: "Quotation QT-2026-001" });
    expect(dialog).toHaveTextContent("Quoted water prices are not applied to the customer yet.");
    await user.click(within(dialog).getByRole("button", { name: "Apply prices to customer" }));
    const apply = await screen.findByRole("dialog", { name: "Apply quoted prices?" });
    expect(within(apply).getByLabelText("From")).toHaveValue("14/10/2026");
    await user.click(within(apply).getByRole("button", { name: "Apply prices" }));
    await waitFor(() => expect(posted(/\/apply-prices$/)).toHaveLength(1));
    expect(posted(/\/apply-prices$/)[0].body).toEqual({ from: TODAY });
  });

  it("starts a quotation for a customer from the customer panel link (?new=1&cust=C0004)", async () => {
    search = "new=1&cust=C0004";
    renderPage();
    const dialog = await screen.findByRole("dialog", { name: "New quotation" });
    expect(within(dialog).getByLabelText("Customer")).toHaveValue(
      "C0004 – Vertex Software (Pvt) Ltd – 011 452 9031",
    );
    await waitFor(() => expect(within(dialog).getByLabelText("Unit price 1")).toHaveValue(300));
    expect(within(dialog).getByTestId("now-1")).toHaveTextContent("agreed price now");
    expect(within(dialog).getByTestId("cq-note")).toHaveTextContent(
      "Prices start at what Vertex Software (Pvt) Ltd pays now.",
    );
  });

  it("the Accountant views and prints, nothing else", async () => {
    search = "id=1";
    renderPage("ACCOUNTANT");
    const dialog = await screen.findByRole("dialog", { name: "Quotation QT-2026-001" });
    expect(within(dialog).getByRole("button", { name: "Print / PDF" })).toBeInTheDocument();
    for (const name of ["Make a copy", "Edit", "Next: Mark as sent"]) {
      expect(within(dialog).queryByRole("button", { name })).not.toBeInTheDocument();
    }
    expect(screen.queryByRole("button", { name: "+ New quotation" })).not.toBeInTheDocument();
  });
});
