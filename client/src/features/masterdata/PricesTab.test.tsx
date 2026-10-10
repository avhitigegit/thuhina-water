import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AccessProvider } from "@/features/auth/access";
import type { MeResponse, PriceMatrix } from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { PricesTab } from "./PricesTab";

const ME = {
  user: { id: 1, username: "nimal.admin", fullName: "Nimal Perera", role: "ADMIN", roleName: "Admin" },
  mustChangePassword: false,
  landingPage: "/dashboard",
  permissions: [P.MASTERDATA_VIEW, P.MASTERDATA_EDIT],
  menu: [],
} as MeResponse;

const MATRIX: PriceMatrix = {
  date: "2026-10-11",
  bottleTypes: [{ code: "B10", name: "10L Bottle" }],
  customerTypes: [
    { id: 1, name: "Household" },
    { id: 4, name: "Factory" },
  ],
  water: [
    {
      key: "WATER|B10|Household",
      kind: "WATER",
      bottleTypeCode: "B10",
      customerTypeId: 1,
      current: { entryId: 1, price: 200, effectiveFrom: "2026-07-01" },
      next: null,
      confirmed: false,
    },
    {
      key: "WATER|B10|Factory",
      kind: "WATER",
      bottleTypeCode: "B10",
      customerTypeId: 4,
      current: { entryId: 2, price: 175, effectiveFrom: "2026-07-01" },
      next: { entryId: 3, price: 180, effectiveFrom: "2026-11-01" },
      confirmed: false,
    },
  ],
  deposits: [
    {
      key: "DEPOSIT|B10",
      kind: "DEPOSIT",
      bottleTypeCode: "B10",
      customerTypeId: null,
      current: { entryId: 4, price: 600, effectiveFrom: "2026-01-01" },
      next: null,
      confirmed: true,
    },
  ],
};

function wrap(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <QueryClientProvider client={qc}>
      <AccessProvider value={{ me: ME, viewOnly: false }}>{children}</AccessProvider>
    </QueryClientProvider>
  );
}

afterEach(() => vi.unstubAllGlobals());

describe("PricesTab", () => {
  it("shows current prices, the scheduled change and example / confirmed tags", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(MATRIX), { status: 200 })),
    );
    render(wrap(<PricesTab />));

    expect(await screen.findByRole("button", { name: "Rs. 175.00" })).toBeInTheDocument();
    expect(screen.getByText(/from 01\/11\/2026/)).toHaveTextContent("→ Rs. 180.00 from 01/11/2026");
    expect(screen.getAllByText("example")).toHaveLength(2 + 1); // two water cells + the banner
    expect(screen.getByText("confirmed")).toBeInTheDocument();
  });

  it("clicking a price opens the change pop-up with today's price and tomorrow as the date", async () => {
    const user = userEvent.setup();
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify(MATRIX), { status: 200 })),
    );
    render(wrap(<PricesTab />));
    await user.click(await screen.findByRole("button", { name: "Rs. 175.00" }));

    const dialog = screen.getByRole("dialog", { name: "Water price – 10L Bottle / Factory" });
    expect(within(dialog).getByText(/Now:/)).toHaveTextContent(
      "Now: Rs. 175.00 (from 01/07/2026) · scheduled Rs. 180.00 from 01/11/2026",
    );
    expect(within(dialog).getByLabelText("New price (Rs.)")).toHaveFocus();
    await user.click(within(dialog).getByRole("button", { name: "Save" }));
    expect(await within(dialog).findByText("Enter a price greater than zero.")).toBeInTheDocument();
    expect(within(dialog).getByText("Enter the reason for the change.")).toBeInTheDocument();
  });
});
