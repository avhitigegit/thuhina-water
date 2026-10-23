import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { AccessProvider } from "@/features/auth/access";
import type { MeResponse, UserResponse } from "@/lib/api/types";
import { P } from "@/lib/permissions";
import { UsersTab } from "./UsersTab";

const ME = {
  user: { id: 1, username: "nimal.admin", fullName: "Nimal Perera", role: "ADMIN", roleName: "Admin" },
  mustChangePassword: false,
  landingPage: "/dashboard",
  permissions: [P.ADMIN_MANAGE],
  menu: [],
} as MeResponse;

const user = (over: Partial<UserResponse>): UserResponse => ({
  id: 1,
  username: "nimal.admin",
  fullName: "Nimal Perera",
  phone: null,
  role: "ADMIN",
  roleName: "Admin",
  active: true,
  mustChangePassword: false,
  lastLoginAt: null,
  version: 0,
  ...over,
});

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(async (url: string) =>
      url.split("?")[0] === "/api/users"
        ? new Response(
            JSON.stringify([user({}), user({ id: 2, username: "shanika.acc", fullName: "Shanika Silva" })]),
          )
        : new Response("[]"),
    ),
  );
});

describe("UsersTab", () => {
  it("has no Deactivate button on your own row (N-12)", async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <AccessProvider value={{ me: ME, viewOnly: false }}>
          <UsersTab />
        </AccessProvider>
      </QueryClientProvider>,
    );
    const mine = (await screen.findByText("nimal.admin")).closest("tr") as HTMLElement;
    const other = screen.getByText("shanika.acc").closest("tr") as HTMLElement;
    expect(within(mine).queryByRole("button", { name: "Deactivate" })).not.toBeInTheDocument();
    expect(within(other).getByRole("button", { name: "Deactivate" })).toBeInTheDocument();
  });
});
