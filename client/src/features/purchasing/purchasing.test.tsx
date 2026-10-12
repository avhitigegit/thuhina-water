import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Toaster } from "@/components/shared/Toaster";
import { AccessProvider } from "@/features/auth/access";
import type {
  MeResponse,
  PoDetail,
  PoRow,
  QrDetail,
  QrRow,
  QuoteColumn,
  Supplier,
  SupplierBalance,
} from "@/lib/api/types";
import { addDaysIso, formatDate, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { Purchasing } from "./Purchasing";

const TODAY = todayIso();

function me(role: "ADMIN" | "ACCOUNTANT"): MeResponse {
  return {
    user: { id: 1, username: "u", fullName: "U", role, roleName: role },
    mustChangePassword: false,
    landingPage: "/dashboard",
    permissions:
      role === "ADMIN"
        ? [P.PURCHASING_VIEW, P.PURCHASING_EDIT, P.PARTNERS_VIEW, P.MASTERDATA_VIEW]
        : [P.PURCHASING_VIEW, P.PARTNERS_VIEW],
    menu: [],
  } as MeResponse;
}

function supplier(id: number, name: string, items: [string, string][]): Supplier {
  return {
    id,
    code: "S0" + id,
    name,
    contact: null,
    phone: "011 000 0000",
    email: null,
    address: null,
    termsDays: 30,
    termsLabel: "30 days",
    items: items.map(([code, n]) => ({ code, type: code.startsWith("B") ? "BOTTLE" : "PRODUCT", name: n })),
    owed: 0,
    active: true,
    version: 0,
  };
}

const SUPPLIERS = [
  supplier(1, "Lanka Polymer", [["B20", "Empty 20L Bottle"]]),
  supplier(2, "Ceylon PET", [["B20", "Empty 20L Bottle"]]),
  supplier(4, "HomeCool", [["P01", "Dispenser"]]),
];

const ROWS: QrRow[] = [
  {
    id: 13,
    qrNo: "QR-2026-013",
    date: "2026-09-22",
    requiredBy: "2026-10-15",
    note: "Kottawa route",
    status: "QUOTES_RECEIVED",
    statusLabel: "Quotes received",
    items: [{ itemCode: "B20", itemType: "BOTTLE", name: "Empty 20L Bottle", qty: 600 }],
    suppliersAsked: 3,
    quotations: 2,
    chosen: null,
    po: null,
  },
];

function quote(
  id: number,
  sup: Supplier,
  price: number | null,
  over: Partial<QuoteColumn> = {},
): QuoteColumn {
  return {
    id,
    quoteNo: "SQ-000" + id,
    supplier: { id: sup.id, code: sup.code, name: sup.name },
    supplierRef: "REF-" + id,
    date: "2026-09-24",
    validUntil: addDaysIso(TODAY, 10),
    expired: false,
    deliveryDays: 7,
    notes: null,
    attachmentName: null,
    attachmentFile: false,
    prices: [
      { itemCode: "B20", unitPrice: price, lineTotal: price === null ? null : price * 600, lowest: false },
    ],
    total: price === null ? 0 : price * 600,
    complete: price !== null,
    selected: false,
    selectionReason: null,
    selectedByName: null,
    selectedOn: null,
    ...over,
  };
}

let detail: QrDetail;
let po: PoDetail;

function freshDetail(): QrDetail {
  return {
    id: 13,
    qrNo: "QR-2026-013",
    date: "2026-09-22",
    requiredBy: "2026-10-15",
    note: "Kottawa route",
    status: "QUOTES_RECEIVED",
    statusLabel: "Quotes received",
    createdByName: "Nimal Perera",
    items: [{ itemCode: "B20", itemType: "BOTTLE", name: "Empty 20L Bottle", qty: 600 }],
    suppliers: [
      { id: 1, code: "S01", name: "Lanka Polymer", quoted: true },
      { id: 2, code: "S02", name: "Ceylon PET", quoted: true },
      { id: 3, code: "S03", name: "Kelani", quoted: false },
    ],
    quotations: [
      quote(7, SUPPLIERS[0], 615, {
        prices: [{ itemCode: "B20", unitPrice: 615, lineTotal: 369000, lowest: true }],
        attachmentName: "LPC.pdf",
        attachmentFile: true,
      }),
      quote(8, SUPPLIERS[1], 640, { expired: true, validUntil: addDaysIso(TODAY, -1) }),
    ],
    po: null,
    canAddQuotation: true,
    canChoose: true,
    canCreatePo: false,
    version: 0,
  };
}

function freshPo(status: string, over: Partial<PoDetail> = {}): PoDetail {
  const labels: Record<string, string> = {
    DRAFT: "Draft",
    APPROVED: "Approved",
    SENT: "Sent",
    PARTLY_RECEIVED: "Partly received",
  };
  return {
    id: 42,
    poNo: "PO-2026-0042",
    date: TODAY,
    expectedDate: addDaysIso(TODAY, 12),
    status,
    statusLabel: labels[status],
    nextStep: null,
    supplier: {
      id: 1,
      code: "S01",
      name: "Lanka Polymer",
      contact: null,
      phone: "011",
      address: null,
      termsDays: 30,
      termsLabel: "30 days",
    },
    qrId: 13,
    qrNo: "QR-2026-013",
    quotationId: 7,
    quoteNo: "SQ-0007",
    supplierRef: "LPC-Q-2509",
    notes: null,
    lines: [
      {
        id: 100,
        itemType: "BOTTLE",
        itemCode: "B20",
        name: "Empty 20L Bottle",
        qty: 300,
        unitPrice: 615,
        amount: 184500,
        receivedQty: 0,
        damagedQty: 0,
        stillDue: 300,
      },
    ],
    total: 184500,
    receivedValue: 0,
    createdByName: "Nimal Perera",
    approvedByName: null,
    approvedOn: null,
    sentOn: null,
    cancelReason: null,
    history: [
      {
        status: "DRAFT",
        statusLabel: "Draft",
        ts: TODAY + "T04:30:00Z",
        username: "nimal.admin",
        userName: "Nimal Perera",
        note: null,
      },
    ],
    receipts: [],
    editable: status === "DRAFT",
    canCancel: status === "DRAFT" || status === "APPROVED",
    canReceive: status === "SENT" || status === "PARTLY_RECEIVED",
    version: 1,
    ...over,
  };
}

const PO_ROW: PoRow = {
  id: 42,
  poNo: "PO-2026-0042",
  date: TODAY,
  expectedDate: addDaysIso(TODAY, 12),
  supplier: { id: 1, code: "S01", name: "Lanka Polymer" },
  qrNo: "QR-2026-013",
  items: [{ itemCode: "B20", itemType: "BOTTLE", name: "Empty 20L Bottle", qty: 300 }],
  total: 184500,
  received: 0,
  ordered: 300,
  status: "SENT",
  statusLabel: "Sent",
  nextStep: "Receive goods",
};

const BALANCES: SupplierBalance[] = [
  {
    supplierId: 4,
    code: "S04",
    name: "HomeCool",
    termsDays: 45,
    termsLabel: "45 days",
    received: 256950,
    paid: 150000,
    owed: 106950,
    oldestUnpaidDate: "2026-09-10",
    oldestUnpaidDays: 32,
    overdue: false,
  },
];

type Fetch = (url: string, init?: RequestInit) => Promise<Response>;
let calls: { method: string; path: string; body: unknown }[] = [];

function server() {
  return vi.fn<Fetch>(async (url, init) => {
    const [path] = url.split("?");
    const method = init?.method ?? "GET";
    const body = typeof init?.body === "string" ? JSON.parse(init.body) : init?.body;
    calls.push({ method, path, body });
    const json = (b: unknown, status = 200) => new Response(JSON.stringify(b), { status });
    if (method === "GET") {
      if (path === "/api/quotation-requests") return json(ROWS);
      if (path === "/api/quotation-requests/13") return json(detail);
      if (path === "/api/suppliers") return json(SUPPLIERS);
      if (path === "/api/bottle-types")
        return json([
          { code: "B20", name: "20L Bottle", litres: 20, active: true, deposit: 1000, version: 0 },
        ]);
      if (path === "/api/products") return json([{ code: "P01", name: "Dispenser", active: true }]);
      if (path === "/api/purchase-orders") return json([PO_ROW]);
      if (path === "/api/purchase-orders/42") return json(po);
      if (path === "/api/supplier-balances") return json(BALANCES);
      if (path === "/api/supplier-payments") return json([]);
    }
    if (path === "/api/quotation-requests") return json({ ...freshDetail(), qrNo: "QR-2026-015" }, 201);
    if (path === "/api/quotations/8/choose")
      return json(
        {
          code: "QUOTATION_EXPIRED",
          message: `This quotation expired on ${formatDate(addDaysIso(TODAY, -1))}. Ask the supplier to re-quote.`,
        },
        422,
      );
    if (path === "/api/quotations/7/choose") {
      detail = {
        ...detail,
        status: "SELECTED",
        statusLabel: "Selected",
        canChoose: false,
        canCreatePo: true,
        quotations: detail.quotations.map((q) =>
          q.id === 7
            ? { ...q, selected: true, selectionReason: "Lowest", selectedByName: "Nimal Perera" }
            : q,
        ),
      };
      return json(detail);
    }
    if (path === "/api/quotations/7/create-po") {
      po = freshPo("DRAFT");
      return json(po, 201);
    }
    if (path === "/api/purchase-orders/42" && method === "PUT") return json(po);
    if (path === "/api/purchase-orders/42/approve") {
      po = freshPo("APPROVED");
      return json(po);
    }
    if (path === "/api/purchase-orders/42/receipts") {
      po = freshPo("PARTLY_RECEIVED");
      return json(
        {
          receipt: {
            id: 3,
            grnNo: "GRN-0003",
            date: TODAY,
            deliveryNote: null,
            note: null,
            value: 0,
            createdByName: "N",
            lines: [],
          },
          po,
        },
        201,
      );
    }
    if (path === "/api/supplier-payments") return json({ id: 3, spNo: "SP-0003" }, 201);
    return json({ code: "NOT_FOUND", message: "Not found " + method + " " + path }, 404);
  });
}

function renderPage(
  role: "ADMIN" | "ACCOUNTANT" = "ADMIN",
  children: ReactNode = <Purchasing today={TODAY} />,
) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: me(role), viewOnly: role !== "ADMIN" }}>
        {children}
        <Toaster />
      </AccessProvider>
    </QueryClientProvider>,
  );
}

const posted = (path: string) => calls.filter((c) => c.method !== "GET" && c.path === path);
let opened: string[] = [];

beforeEach(() => {
  calls = [];
  opened = [];
  detail = freshDetail();
  po = freshPo("SENT");
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

describe("Purchasing – quotations", () => {
  it("lists requests and ticks the suppliers of the chosen items on a new request", async () => {
    const user = userEvent.setup();
    renderPage();
    const row = (await screen.findByText("QR-2026-013")).closest("tr") as HTMLElement;
    expect(row).toHaveTextContent("600 × Empty 20L Bottle");
    expect(row).toHaveTextContent("2 of 3 suppliers");
    expect(row).toHaveTextContent("Quotes received");

    await user.click(screen.getByRole("button", { name: "+ New request" }));
    const dialog = await screen.findByRole("dialog", { name: "New quotation request" });
    const tick = (name: string) => within(dialog).getByText(name).closest("label")!.querySelector("input")!;
    await waitFor(() => expect(within(dialog).getByLabelText("Item 1")).toHaveValue("B20"));
    expect(tick("Lanka Polymer")).toBeChecked();
    expect(tick("Ceylon PET")).toBeChecked();
    expect(tick("HomeCool")).not.toBeChecked();

    await user.selectOptions(within(dialog).getByLabelText("Item 1"), "P01");
    expect(tick("HomeCool")).toBeChecked();
    expect(tick("Lanka Polymer")).not.toBeChecked();
    await user.click(tick("Ceylon PET"));
    await user.type(within(dialog).getByLabelText("Quantity 1"), "6");
    await user.click(within(dialog).getByRole("button", { name: "Create request" }));

    await waitFor(() => expect(posted("/api/quotation-requests")).toHaveLength(1));
    expect(posted("/api/quotation-requests")[0].body).toMatchObject({
      date: TODAY,
      items: [{ itemCode: "P01", qty: 6 }],
      supplierIds: [4, 2],
    });
  });

  it("compares quotations side by side; an expired one is refused; the chosen one becomes a PO", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(await screen.findByRole("button", { name: "Open" }));
    const dialog = await screen.findByRole("dialog", { name: /QR-2026-013/ });
    expect(await within(dialog).findByText("lowest")).toBeInTheDocument();
    expect(within(dialog).getByText("expired")).toBeInTheDocument();
    expect(within(dialog).getByRole("link", { name: "LPC.pdf" })).toHaveAttribute(
      "href",
      "/api/quotations/7/attachment",
    );
    expect(within(dialog).getByText(/the system does not choose/)).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "+ Add quotation" })).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Create purchase order" })).not.toBeInTheDocument();

    // The expired quotation: the server refuses and says why.
    await user.click(within(dialog).getAllByRole("button", { name: "Choose" })[1]);
    const ask = await screen.findByRole("dialog", { name: "Choose Ceylon PET?" });
    await user.type(within(ask).getByLabelText("Reason for choosing"), "Cheaper");
    await user.click(within(ask).getByRole("button", { name: "Choose" }));
    expect(await screen.findByText(/This quotation expired on/)).toBeInTheDocument();

    const again = await screen.findByRole("dialog", { name: /QR-2026-013/ });
    await user.click(within(again).getAllByRole("button", { name: "Choose" })[0]);
    const ask2 = await screen.findByRole("dialog", { name: "Choose Lanka Polymer?" });
    await user.type(within(ask2).getByLabelText("Reason for choosing"), "Lowest");
    await user.click(within(ask2).getByRole("button", { name: "Choose" }));
    await waitFor(() => expect(posted("/api/quotations/7/choose")[0]?.body).toEqual({ reason: "Lowest" }));

    const chosen = await screen.findByRole("dialog", { name: /QR-2026-013/ });
    expect(await within(chosen).findByText(/Chosen:/)).toHaveTextContent(
      "Lanka Polymer by Nimal Perera – “Lowest”",
    );
    await user.click(within(chosen).getByRole("button", { name: "Create purchase order" }));

    // The new PO opens on the Purchase orders tab, Draft, with only the next step.
    const poDialog = await screen.findByRole("dialog", { name: "Purchase order PO-2026-0042" });
    expect(screen.getByRole("tab", { name: "Purchase orders", hidden: true })).toHaveAttribute(
      "aria-selected",
      "true",
    );
    expect(within(poDialog).getByRole("button", { name: "Next: Approve" })).toBeInTheDocument();
    expect(within(poDialog).queryByRole("button", { name: /Mark as sent/ })).not.toBeInTheDocument();
    expect(within(poDialog).queryByRole("button", { name: /Receive goods/ })).not.toBeInTheDocument();
  });
});

describe("Purchasing – purchase orders", () => {
  it("edits a Draft and approves it with the changes saved first", async () => {
    const user = userEvent.setup();
    po = freshPo("DRAFT");
    renderPage();
    await user.click(screen.getByRole("tab", { name: "Purchase orders" }));
    await user.click(await screen.findByRole("button", { name: "Open" }));
    const dialog = await screen.findByRole("dialog", { name: "Purchase order PO-2026-0042" });
    expect(within(dialog).getByText("1. Draft")).toHaveAttribute("aria-current", "step");
    const qty = await within(dialog).findByLabelText("Quantity Empty 20L Bottle");
    await user.clear(qty);
    await user.type(qty, "200");
    expect(within(dialog).getByText("Total").closest("tr")).toHaveTextContent("Rs. 123,000.00");
    await user.click(within(dialog).getByRole("button", { name: "Next: Approve" }));

    await waitFor(() => expect(posted("/api/purchase-orders/42/approve")).toHaveLength(1));
    expect(posted("/api/purchase-orders/42")[0].body).toMatchObject({
      lines: [{ itemCode: "B20", qty: 200, unitPrice: 615 }],
      version: 1,
    });
    expect(
      await within(screen.getByRole("dialog")).findByRole("button", {
        name: "Next: Mark as sent to supplier",
      }),
    ).toBeInTheDocument();
  });

  it("receives goods: 300 with 3 damaged adds 297; prints the PO", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("tab", { name: "Purchase orders" }));
    const row = (await screen.findByText("PO-2026-0042")).closest("tr") as HTMLElement;
    expect(row).toHaveTextContent("0 / 300");
    expect(row).toHaveTextContent("Receive goods");
    await user.click(within(row).getByRole("button", { name: "Open" }));
    const dialog = await screen.findByRole("dialog", { name: "Purchase order PO-2026-0042" });
    expect(within(dialog).queryByRole("button", { name: "Cancel PO" })).not.toBeInTheDocument();
    await user.click(within(dialog).getByRole("button", { name: "Print / PDF" }));
    expect(opened).toEqual(["/api/print/purchase-order/42"]);

    await user.click(within(dialog).getByRole("button", { name: "Next: Receive goods" }));
    const receive = await screen.findByRole("dialog", { name: "Receive goods – PO-2026-0042" });
    expect(within(receive).getByLabelText("Received Empty 20L Bottle")).toHaveValue(300);
    const damaged = within(receive).getByLabelText("Damaged Empty 20L Bottle");
    await user.clear(damaged);
    await user.type(damaged, "3");
    expect(within(receive).getByTestId("good-B20")).toHaveTextContent("297");
    await user.type(within(receive).getByLabelText("Delivery note no."), "DN-1");
    await user.click(within(receive).getByRole("button", { name: "Save receipt" }));

    await waitFor(() => expect(posted("/api/purchase-orders/42/receipts")).toHaveLength(1));
    expect(posted("/api/purchase-orders/42/receipts")[0].body).toMatchObject({
      date: TODAY,
      deliveryNote: "DN-1",
      lines: [{ poLineId: 100, received: 300, damaged: 3 }],
    });
    expect(
      await screen.findByText("GRN-0003 saved – stock updated, PO is Partly received"),
    ).toBeInTheDocument();
  });
});

describe("Purchasing – supplier payments and roles", () => {
  it("shows the owed amounts and records a payment", async () => {
    const user = userEvent.setup();
    renderPage();
    await user.click(screen.getByRole("tab", { name: "Supplier payments" }));
    const row = (await screen.findByText("HomeCool")).closest("tr") as HTMLElement;
    expect(row).toHaveTextContent("Rs. 106,950.00");
    expect(row).toHaveTextContent(`${formatDate("2026-09-10")} (32 days)`);
    await user.click(within(row).getByRole("button", { name: "Pay" }));
    const dialog = await screen.findByRole("dialog", { name: "Record supplier payment" });
    await waitFor(() => expect(within(dialog).getByLabelText("Supplier")).toHaveValue("4"));
    expect(within(dialog).getByLabelText("Amount (Rs.)")).toHaveValue(106950);
    await user.selectOptions(within(dialog).getByLabelText("Method"), "CHEQUE");
    await user.type(within(dialog).getByLabelText("Cheque no. / bank ref."), "Cheque 004601");
    await user.click(within(dialog).getByRole("button", { name: "Save payment" }));
    await waitFor(() => expect(posted("/api/supplier-payments")).toHaveLength(1));
    expect(posted("/api/supplier-payments")[0].body).toMatchObject({
      supplierId: 4,
      amount: 106950,
      method: "CHEQUE",
      reference: "Cheque 004601",
    });
  });

  it("the Accountant can view everything but has no buttons to change anything", async () => {
    const user = userEvent.setup();
    renderPage("ACCOUNTANT");
    await user.click(await screen.findByRole("button", { name: "Open" }));
    const dialog = await screen.findByRole("dialog", { name: /QR-2026-013/ });
    expect(await within(dialog).findByText("lowest")).toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "Choose" })).not.toBeInTheDocument();
    expect(within(dialog).queryByRole("button", { name: "+ Add quotation" })).not.toBeInTheDocument();
    await user.click(within(dialog).getByText("Close"));
    expect(screen.queryByRole("button", { name: "+ New request" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Purchase orders" }));
    await user.click(await screen.findByRole("button", { name: "Open" }));
    const poDialog = await screen.findByRole("dialog", { name: "Purchase order PO-2026-0042" });
    expect(within(poDialog).getByRole("button", { name: "Print / PDF" })).toBeInTheDocument();
    expect(within(poDialog).queryByRole("button", { name: /Next:/ })).not.toBeInTheDocument();
    await user.click(within(poDialog).getByText("Close"));

    await user.click(screen.getByRole("tab", { name: "Supplier payments" }));
    expect(await screen.findByText("HomeCool")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "+ Record payment" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Pay" })).not.toBeInTheDocument();
  });
});
