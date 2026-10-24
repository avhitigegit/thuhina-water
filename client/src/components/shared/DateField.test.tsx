import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { DateField } from "./DateField";

const TODAY = "2026-10-09";

function Harness({
  initial = null,
  onValue,
}: {
  initial?: string | null;
  onValue?: (v: string | null) => void;
}) {
  const [value, setValue] = useState<string | null>(initial);
  return (
    <>
      <label htmlFor="d">Date</label>
      <DateField
        id="d"
        value={value}
        today={TODAY}
        onChange={(v) => {
          setValue(v);
          onValue?.(v);
        }}
      />
      <span data-testid="value">{value ?? "(empty)"}</span>
      <button type="button">Other</button>
    </>
  );
}

const field = () => screen.getByLabelText("Date") as HTMLInputElement;
const value = () => screen.getByTestId("value").textContent;

describe("DateField", () => {
  it("shows the value as DD/MM/YYYY", () => {
    render(<Harness initial="2026-10-05" />);
    expect(field()).toHaveValue("05/10/2026");
  });

  it("adds the slashes while typing and gives an ISO value", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(field(), "05102026");
    expect(field()).toHaveValue("05/10/2026");
    expect(value()).toBe("2026-10-05");
  });

  it("marks a date that does not exist as invalid and gives no value", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.type(field(), "31022026");
    await user.click(screen.getByText("Other"));
    expect(field()).toHaveValue("31/02/2026");
    expect(field()).toHaveClass("invalid");
    expect(value()).toBe("(empty)");
  });

  it("opens the calendar on the shown month, Monday first, and picks a day", async () => {
    const user = userEvent.setup();
    render(<Harness initial="2026-10-05" />);
    await user.click(field());
    const calendar = screen.getByRole("dialog", { name: "Choose a date" });
    expect(calendar).toHaveTextContent("October 2026");
    expect(calendar.querySelector(".dp-dow")).toHaveTextContent("Mo");
    // 1 October 2026 is a Thursday → 3 blank cells before it (Mo, Tu, We).
    expect(calendar.querySelectorAll(".dp-grid > span:not(.dp-dow)")).toHaveLength(3);
    expect(screen.getByRole("button", { name: "05/10/2026" })).toHaveClass("sel");
    expect(screen.getByRole("button", { name: "09/10/2026" })).toHaveClass("today");

    await user.click(screen.getByRole("button", { name: "21/10/2026" }));
    expect(field()).toHaveValue("21/10/2026");
    expect(value()).toBe("2026-10-21");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("moves between months", async () => {
    const user = userEvent.setup();
    render(<Harness initial="2026-12-15" />);
    await user.click(field());
    await user.click(screen.getByRole("button", { name: "Next month" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("January 2027");
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    expect(screen.getByRole("dialog")).toHaveTextContent("November 2026");
  });

  it("Today picks the business date and Clear empties the field", async () => {
    const user = userEvent.setup();
    const onValue = vi.fn();
    render(<Harness initial="2026-01-01" onValue={onValue} />);
    await user.click(field());
    await user.click(screen.getByRole("button", { name: "Today" }));
    expect(field()).toHaveValue("09/10/2026");
    expect(onValue).toHaveBeenLastCalledWith("2026-10-09");

    await user.click(field());
    await user.click(screen.getByRole("button", { name: "Clear" }));
    expect(field()).toHaveValue("");
    expect(onValue).toHaveBeenLastCalledWith(null);
  });

  it("closes the calendar with Escape and keeps the typed value", async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(field());
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("does not open when disabled", async () => {
    const user = userEvent.setup();
    render(<DateField value="2026-10-05" onChange={() => undefined} disabled aria-label="Locked" />);
    await user.click(screen.getByLabelText("Locked"));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
