"use client";

/* Side panel sliding in from the right (prototype UI.drawer) – e.g. customer details. */
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
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="drawer-back" />
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
