"use client";

/* "Are you sure?" pop-up (prototype UI.confirm). */
import type { ReactNode } from "react";
import { FormDialog } from "./FormDialog";

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  children,
  confirmLabel = "Confirm",
  danger,
  busy,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
  confirmLabel?: string;
  danger?: boolean;
  busy?: boolean;
  onConfirm: () => void;
}) {
  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      onSubmit={onConfirm}
      submitLabel={confirmLabel}
      submitting={busy}
      danger={danger}
    >
      {children}
    </FormDialog>
  );
}
