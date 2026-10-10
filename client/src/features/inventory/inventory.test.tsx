import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccessProvider } from "@/features/auth/access";
import type { MeResponse, StockOverview } from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { adjustmentSchema, damageSchema } from "./schemas";
import { AdjustmentDialog } from "./StockDialogs";
import { StockTab } from "./StockTab";

const STOCK: StockOverview = {
  bottles: [
    {
      code: "B20",
      name: "20L Bottle",
      label: "20L",
      empty: 30,
      factory: 66,
      filled: 117,
      customers: 410,
      writtenOff: 46,
      inCirculation: 623,
      minFilled: 150,
      low: true,
    },
    {
      code: "B10",
      name: "10L Bottle",
      label: "10L",
      empty: 18,
      factory: 0,
      filled: 30,
      customers: 0,
      writtenOff: 7,
      inCirculation: 48,
      minFilled: 25,
      low: false,
    },
  ],
  products: [
    {
      id: 4,
      code: "P04",
      name: "Manual Bottle Pump",
      stockQty: 2,
      costPrice: 750,
      costValue: 1500,
      active: true,
    },
  ],
  alerts: [],
};

const ME = {
  user: { id: 1, username: "nimal.admin", fullName: "Nimal Perera", role: "ADMIN", roleName: "Admin" },
  mustChangePassword: false,
  landingPage: "/dashboard",
  permissions: [P.STOCK_VIEW, P.STOCK_EDIT],
  menu: [],
} as MeResponse;

function wrap(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: ME, viewOnly: false }}>{children}</AccessProvider>
    </QueryClientProvider>
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("stock form rules", () => {
  const today = "2026-10-11";
  it("damage: date not after today, qty ≥ 1, place and reason", () => {
    const r = damageSchema(today).safeParse({
      date: "2026-10-12",
      bottleTypeCode: "B20",
      qty: "0",
      location: "",
      reason: " ",
    });
    expect(Object.fromEntries((r.error?.issues ?? []).map((i) => [String(i.path[0]), i.message]))).toEqual({
      date: "Date cannot be after today.",
      qty: "Enter the number of damaged bottles.",
      location: "Select where the damage happened.",
      reason: "Enter the reason.",
    });
  });

  it("adjustment: count needs the counted quantity, lost needs the number lost", () => {
    const base = {
      date: today,
      itemCode: "B20",
      isBottle: true,
      bucket: "FILLED",
      counted: "",
      qty: "",
      reason: "Month-end",
    };
    const s = adjustmentSchema(today);
    expect(s.safeParse({ ...base, mode: "COUNT" }).error?.issues[0].message).toBe(
      "Enter the counted quantity.",
    );
    expect(s.safeParse({ ...base, mode: "LOST" }).error?.issues[0].message).toBe(
      "Enter the number of bottles lost.",
    );
    expect(s.safeParse({ ...base, mode: "COUNT", counted: "0" }).success).toBe(true);
    // Products: always a count, no status.
    expect(
      s.safeParse({ ...base, itemCode: "P04", isBottle: false, bucket: "", mode: "COUNT", counted: "5" })
        .success,
    ).toBe(true);
  });
});

describe("StockTab", () => {
  it("shows filled in red below the minimum and saves a new minimum when it changes", async () => {
    const user = userEvent.setup();
    const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(
      async () => new Response(JSON.stringify({ minFilled: 160 }), { status: 200 }),
    );
    vi.stubGlobal("fetch", fetch);
    render(wrap(<StockTab stock={STOCK} />));

    expect(screen.getByText("117").tagName).toBe("B");
    expect(screen.getByText("623")).toBeInTheDocument();
    expect(screen.getByText("Rs. 1,500.00")).toBeInTheDocument();

    const min = screen.getByLabelText("Minimum filled 20L");
    await user.clear(min);
    await user.type(min, "160{Enter}");
    await vi.waitFor(() => expect(fetch).toHaveBeenCalled());
    const [url, init] = fetch.mock.calls[0];
    expect(url).toBe("/api/stock/min-levels/B20");
    expect(init?.method).toBe("PUT");
    expect(JSON.parse(String(init?.body))).toEqual({ minFilled: 160 });
  });
});

describe("AdjustmentDialog", () => {
  it("shows the system quantity and switches to bottles lost", async () => {
    const user = userEvent.setup();
    render(wrap(<AdjustmentDialog stock={STOCK} onClose={vi.fn()} />));
    const dialog = screen.getByRole("dialog", { name: "Stock adjustment" });
    expect(within(dialog).getByLabelText("In system")).toHaveValue("117");
    await user.selectOptions(within(dialog).getByLabelText("Status"), "EMPTY");
    expect(within(dialog).getByLabelText("In system")).toHaveValue("30");

    await user.click(within(dialog).getByLabelText("Lost bottles"));
    expect(within(dialog).getByLabelText("Bottles lost")).toBeInTheDocument();
    expect(within(dialog).queryByLabelText("Counted")).not.toBeInTheDocument();

    // A product has no status and is always counted.
    await user.selectOptions(within(dialog).getByLabelText("Item"), "P04");
    expect(within(dialog).queryByLabelText("Status")).not.toBeInTheDocument();
    expect(within(dialog).getByLabelText("In system")).toHaveValue("2");
    expect(within(dialog).getByLabelText("Counted")).toBeInTheDocument();
    expect(within(dialog).getByText("Recorded by Nimal Perera.")).toBeInTheDocument();
  });
});
