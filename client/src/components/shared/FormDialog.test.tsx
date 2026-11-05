import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { DateField } from "./DateField";
import { FormDialog } from "./FormDialog";

function renderDialog(onSubmit = vi.fn(), onOpenChange = vi.fn()) {
  render(
    <FormDialog open onOpenChange={onOpenChange} title="New customer" onSubmit={onSubmit}>
      <label htmlFor="a">Name</label>
      <input id="a" />
      <label htmlFor="b">Phone</label>
      <input id="b" />
    </FormDialog>,
  );
  return { onSubmit, onOpenChange };
}

describe("FormDialog", () => {
  it("Escape in a date field closes only the calendar; the next Escape closes the pop-up (N-02)", async () => {
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    render(
      <FormDialog open onOpenChange={onOpenChange} title="New expense" onSubmit={vi.fn()}>
        <label htmlFor="d">Date paid</label>
        <DateField id="d" value={null} onChange={vi.fn()} today="2026-10-09" />
      </FormDialog>,
    );
    await user.click(screen.getByLabelText("Date paid"));
    expect(screen.getByRole("dialog", { name: "Choose a date" })).toBeInTheDocument();
    await user.keyboard("{Escape}");
    expect(screen.queryByRole("dialog", { name: "Choose a date" })).not.toBeInTheDocument();
    expect(onOpenChange).not.toHaveBeenCalled();
    await user.keyboard("{Escape}");
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("a day in the calendar can be clicked inside a pop-up, and the pop-up stays open", async () => {
    // A modal pop-up sets pointer-events: none on the body, and the calendar is rendered on the body.
    const user = userEvent.setup();
    const onOpenChange = vi.fn();
    function Form() {
      const [date, setDate] = useState<string | null>("2026-11-05");
      return (
        <FormDialog open onOpenChange={onOpenChange} title="New customer" onSubmit={vi.fn()}>
          <label htmlFor="d">Registration date</label>
          <DateField id="d" value={date} onChange={setDate} today="2026-11-05" />
        </FormDialog>
      );
    }
    render(<Form />);
    await user.click(screen.getByLabelText("Registration date"));
    await user.click(screen.getByRole("button", { name: "Previous month" }));
    expect(screen.getByText("October 2026")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "29/10/2026" }));
    expect(screen.getByLabelText("Registration date")).toHaveValue("29/10/2026");
    expect(onOpenChange).not.toHaveBeenCalled();
  });

  it("Enter moves to the next field and does not save", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderDialog();
    await user.click(screen.getByLabelText("Name"));
    await user.keyboard("Kamal{Enter}");
    expect(screen.getByLabelText("Phone")).toHaveFocus();
    await user.keyboard("0771234567{Enter}");
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it("saves with the Save button or Ctrl+Enter", async () => {
    const user = userEvent.setup();
    const { onSubmit } = renderDialog();
    await user.click(screen.getByRole("button", { name: "Save" }));
    expect(onSubmit).toHaveBeenCalledTimes(1);
    await user.click(screen.getByLabelText("Name"));
    await user.keyboard("{Control>}{Enter}{/Control}");
    expect(onSubmit).toHaveBeenCalledTimes(2);
  });

  it("closes with Cancel", async () => {
    const user = userEvent.setup();
    const { onOpenChange } = renderDialog();
    expect(screen.getByRole("dialog", { name: "New customer" })).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });
});
