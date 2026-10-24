import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import SalesBillsPage from "@/app/(app)/sales/bills/page";
import { AccessProvider } from "@/features/auth/access";
import type { MeResponse, TxnRow } from "@/lib/api/types";
import { P } from "@/lib/permissions";

vi.mock("next/navigation", () => ({ useSearchParams: () => new URLSearchParams("bill=41275") }));

const ROW: TxnRow = {
  id: 11,
  billNo: "B000011",
  paperBillNo: "41275",
  kind: "EXCHANGE",
  kindLabel: "Exchange",
  date: "2026-10-11",
  customerId: 3,
  customerCode: "C0003",
  customerName: "Dilshan Fernando",
  walkIn: false,
  filled: 4,
  empties: 3,
  amount: 1320,
  paid: 500,
  reversed: true,
  reversalOfBillNo: null,
};

const ME = {
  user: { id: 1, username: "dilini.admin", fullName: "Dilini Admin", role: "ADMIN", roleName: "Admin" },
  mustChangePassword: false,
  landingPage: "/dashboard",
  permissions: [P.SALES_VIEW, P.SALES_EDIT],
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

describe("Sales & Bills page", () => {
  it("?bill= searches all bills, shows the paper bill banner and opens the bill", async () => {
    const fetch = vi.fn<(url: string, init?: RequestInit) => Promise<Response>>(async (url) => {
      const path = url.split("?")[0];
      const body =
        path === "/api/sales/txns"
          ? { items: [ROW], page: 0, size: 50, total: 1 }
          : path === "/api/sales/paper-bill/41275"
            ? {
                paperBillNo: "41275",
                entered: true,
                txn: ROW,
                message:
                  "Bill 41275 is already entered – Exchange, Dilshan Fernando, 11/10/2026. Do not enter it again.",
              }
            : path === "/api/sales/txns/11"
              ? {
                  ...ROW,
                  lines: [],
                  effects: [],
                  createdBy: "dilini.admin",
                  createdAt: "2026-10-11T04:30:00Z",
                }
              : [];
      return new Response(JSON.stringify(body), { status: 200 });
    });
    vi.stubGlobal("fetch", fetch);
    render(wrap(<SalesBillsPage />));

    expect(
      await screen.findByText(
        "Bill 41275 is already entered – Exchange, Dilshan Fernando, 11/10/2026. Do not enter it again.",
      ),
    ).toHaveClass("banner", "warn");
    // A bill number ignores the other filters (dates).
    const listCall = fetch.mock.calls.map(([u]) => u).find((u) => u.startsWith("/api/sales/txns?"));
    expect(listCall).toContain("q=41275");
    expect(listCall).not.toContain("from=");

    const row = screen.getByText("B000011").closest("tr");
    expect(row).toHaveClass("row-muted");
    expect(within(row as HTMLElement).getByText("reversed")).toBeInTheDocument();
    expect(within(row as HTMLElement).getByText("4/3")).toBeInTheDocument();
    expect(await screen.findByRole("dialog", { name: "Bill B000011" })).toBeInTheDocument();
  });
});
