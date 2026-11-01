import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import type { WhatsAppMessagePage, WhatsAppSettings, WhatsAppTemplateRow } from "@/lib/api/types";
import { WhatsAppTab } from "./WhatsAppTab";

const SETTINGS: WhatsAppSettings = {
  enabled: false,
  phoneNumberId: null,
  businessAccountId: null,
  accessTokenSaved: true,
  appSecretSaved: false,
  verifyTokenSaved: false,
  welcome: true,
  quotation: true,
  deliveryBill: true,
  billCancelled: true,
  paymentReceipt: true,
  monthlyInvoice: true,
  dueSoon: true,
  overdue: true,
  daysBefore: 3,
  daysAfter: 3,
  reminderHour: 9,
  language: "en",
  mode: "TEST",
  modeText:
    "Test mode – messages are only written to the log below (no phone number ID / access token saved).",
  encryptionReady: true,
  webhookPath: "/api/whatsapp/webhook",
};

const LOG: WhatsAppMessagePage = {
  items: [
    {
      id: 7,
      createdAt: "2026-11-02T03:30:00Z",
      customerId: 41,
      customerCode: "C0041",
      customerName: "UAT Test Customer",
      type: "DELIVERY_BILL",
      typeLabel: "Delivery bill",
      toNumber: "+94 77 123 4567",
      status: "SENT",
      statusLabel: "Sent",
      testMode: true,
      params: ["UAT Test Customer", "2 × 20L", "02/11/2026", "09/11/2026"],
      attempts: 1,
      sentAt: "2026-11-02T03:31:00Z",
      statusAt: "2026-11-02T03:31:00Z",
      error: null,
      sourceType: "SALES_TXN",
      sourceId: 12,
    },
  ],
  page: 0,
  size: 50,
  total: 1,
  thisMonth: { month: "2026-11", sent: 0, failed: 0, skipped: 0, test: 1 },
};

const TEMPLATES: WhatsAppTemplateRow[] = [
  {
    type: "DELIVERY_BILL",
    label: "Delivery bill",
    name: "delivery_bill_v1",
    category: "UTILITY",
    document: true,
    body: "Dear {{1}}, {{2}}.",
    params: ["Customer name", "Opening line"],
    switchedOn: true,
  },
  {
    type: "PAYMENT_OVERDUE",
    label: "Payment overdue",
    name: "payment_overdue_v1",
    category: "UTILITY",
    document: false,
    body: "Dear {{1}}, your payment of {{2}} was due on {{3}}.",
    params: ["Customer name", "Amount", "Due date"],
    switchedOn: false,
  },
];

let puts: unknown[] = [];

beforeEach(() => {
  puts = [];
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string, init?: RequestInit) => {
      const path = url.split("?")[0];
      if (path === "/api/whatsapp/settings" && init?.method === "PUT") {
        const body = JSON.parse(String(init.body));
        puts.push(body);
        return new Response(JSON.stringify({ ...SETTINGS, ...body, accessToken: undefined }));
      }
      if (path === "/api/whatsapp/settings") return new Response(JSON.stringify(SETTINGS));
      if (path === "/api/whatsapp/messages") return new Response(JSON.stringify(LOG));
      if (path === "/api/whatsapp/templates") return new Response(JSON.stringify(TEMPLATES));
      return new Response("{}");
    }),
  );
});

function renderTab() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(
    <QueryClientProvider client={qc}>
      <WhatsAppTab />
    </QueryClientProvider>,
  );
}

describe("WhatsAppTab", () => {
  it("shows test mode, saved secrets without their value, and the message log", async () => {
    renderTab();
    expect(await screen.findByTestId("wa-mode")).toHaveTextContent("Test mode");
    const token = screen.getByLabelText(/Access token/) as HTMLInputElement;
    expect(token.value).toBe("");
    expect(token.placeholder).toMatch(/Saved/);
    expect(await screen.findByText("C0041 – UAT Test Customer")).toBeInTheDocument();
    expect(screen.getByTestId("wa-month")).toHaveTextContent("1 in test mode");
  });

  it("sends only the changed fields when saving", async () => {
    renderTab();
    fireEvent.click(await screen.findByLabelText(/Send WhatsApp messages/));
    fireEvent.change(screen.getByLabelText("Phone number ID"), { target: { value: "123456789012345" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(puts).toHaveLength(1));
    expect(puts[0]).toEqual({ enabled: true, phoneNumberId: "123456789012345" });
  });

  it("has a switch per message, the days before / after, and lists the templates for Meta", async () => {
    renderTab();
    fireEvent.click(await screen.findByLabelText(/Payment receipt/));
    fireEvent.change(screen.getByLabelText("Due soon: days before the due date"), { target: { value: "5" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));
    await waitFor(() => expect(puts).toHaveLength(1));
    expect(puts[0]).toEqual({ paymentReceipt: false, daysBefore: 5 });

    const bill = await screen.findByTestId("wa-template-delivery_bill_v1");
    expect(bill).toHaveTextContent("Delivery bill – delivery_bill_v1 · UTILITY · header: Document (PDF)");
    expect(bill).toHaveTextContent("{{1}} Customer name · {{2}} Opening line");
    expect(screen.getByTestId("wa-template-payment_overdue_v1")).toHaveTextContent("Off");
    expect(screen.getByRole("option", { name: "Payment due soon" })).toBeInTheDocument();
  });
});
