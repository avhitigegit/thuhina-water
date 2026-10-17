"use client";

/*
 * Side panel sliding in from the right (prototype UI.drawer) – e.g. customer details. Not modal: a click on the page
 * closes the panel AND does what was clicked (e.g. "+ New customer" or another row) – a modal panel ate that first click.
 * The dimmed back is only for looks (pointer-events: none).
 */
import { Dialog } from "radix-ui";
import type { ReactNode } from "react";

export function SidePanel({
  open,
  onOpenChange,
  title,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange} modal={false}>
      <Dialog.Portal>
        <div className="drawer-back" style={{ pointerEvents: "none" }} aria-hidden="true" />
        <Dialog.Content className="drawer" aria-describedby={undefined}>
          <header>
            <Dialog.Title asChild>
              <h2>{title}</h2>
            </Dialog.Title>
            <Dialog.Close className="x" aria-label="Close">
              ×
            </Dialog.Close>
          </header>
          <div className="body">{children}</div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
