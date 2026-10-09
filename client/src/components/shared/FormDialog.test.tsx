import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
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
