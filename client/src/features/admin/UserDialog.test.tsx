import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { UserResponse } from "@/lib/api/types";
import { UserDialog } from "./UserDialog";

function wrap(children: ReactNode) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

function mockFetch(status: number, body: unknown) {
  const fn = vi.fn(async () => new Response(JSON.stringify(body), { status }));
  vi.stubGlobal("fetch", fn);
  return fn;
}

afterEach(() => vi.unstubAllGlobals());

const ME: UserResponse = {
  id: 1,
  username: "nimal.admin",
  fullName: "Nimal Perera",
  phone: null,
  role: "ADMIN",
  roleName: "Admin",
  active: true,
  mustChangePassword: false,
  lastLoginAt: null,
  version: 3,
};

describe("UserDialog", () => {
  it("checks the form before sending and shows the prototype messages", async () => {
    const user = userEvent.setup();
    const fetch = mockFetch(201, {});
    render(wrap(<UserDialog open onOpenChange={vi.fn()} user={null} isSelf={false} />));

    await user.type(screen.getByLabelText("Full name"), "Saman");
    await user.type(screen.getByLabelText("Username"), "ab");
    await user.type(screen.getByLabelText("Temporary password"), "short");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(
      await screen.findByText("Username: at least 3 lowercase letters, numbers, dots or underscores."),
    ).toBeInTheDocument();
    expect(screen.getByText("Temporary password must be at least 8 characters.")).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("shows a server field error under the field", async () => {
    const user = userEvent.setup();
    const fetch = mockFetch(400, {
      code: "VALIDATION",
      message: "Please check the highlighted fields.",
      details: { fields: { username: "Username already taken." } },
    });
    const onOpenChange = vi.fn();
    render(wrap(<UserDialog open onOpenChange={onOpenChange} user={null} isSelf={false} />));

    await user.type(screen.getByLabelText("Full name"), "Saman Dissanayake");
    await user.type(screen.getByLabelText("Username"), "Kasun.D");
    await user.selectOptions(screen.getByLabelText("Role"), "ACCOUNTANT");
    await user.type(screen.getByLabelText("Temporary password"), "Start-123");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("Username already taken.")).toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalledWith(false);
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/users");
    expect(JSON.parse(String(init.body))).toEqual({
      fullName: "Saman Dissanayake",
      username: "kasun.d",
      role: "ACCOUNTANT",
      phone: "",
      temporaryPassword: "Start-123",
    });
  });

  it("editing your own account keeps the role and sends the version", async () => {
    const user = userEvent.setup();
    const fetch = mockFetch(200, ME);
    const onOpenChange = vi.fn();
    render(wrap(<UserDialog open onOpenChange={onOpenChange} user={ME} isSelf />));

    expect(screen.getByLabelText("Role")).toBeDisabled();
    expect(screen.getByText("You cannot change your own role.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Temporary password")).not.toBeInTheDocument();

    await user.clear(screen.getByLabelText("Phone"));
    await user.type(screen.getByLabelText("Phone"), "077 100 2201");
    await user.click(screen.getByRole("button", { name: "Save" }));

    await vi.waitFor(() => expect(onOpenChange).toHaveBeenCalledWith(false));
    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe("/api/users/1");
    expect(init.method).toBe("PUT");
    expect(JSON.parse(String(init.body))).toEqual({
      fullName: "Nimal Perera",
      username: "nimal.admin",
      role: "ADMIN",
      phone: "077 100 2201",
      version: 3,
    });
  });
});
