"use client";

/*
 * Pop-up with a form (design P5 – add / edit / record in a pop-up, prototype UI.modal).
 * Enter in a field moves to the next field instead of saving by accident (NFR-04);
 * Ctrl+Enter or the save button saves. Escape, × and Cancel close it.
 */
import { Dialog } from "radix-ui";
import type { FormEvent, KeyboardEvent, ReactNode } from "react";
import { cn } from "@/lib/utils";

export interface FormDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  children: ReactNode;
  /** Called on save; omit for a read-only pop-up (only Close). */
  onSubmit?: () => void;
  submitLabel?: string;
  /** Disables the buttons while saving. */
  submitting?: boolean;
  cancelLabel?: string;
  /** Extra footer buttons, shown before Cancel. */
  extraButtons?: ReactNode;
  danger?: boolean;
  wide?: boolean;
}

const FIELDS =
  "input:not([type=hidden]):not([readonly]):not(:disabled), select:not(:disabled), textarea:not(:disabled)";

/** Enter → next field (the last field does nothing); Ctrl+Enter → save. */
export function handleEnterToNext(e: KeyboardEvent<HTMLFormElement>) {
  if (e.key !== "Enter") return;
  const target = e.target as HTMLElement;
  if (e.ctrlKey || e.metaKey) {
    e.preventDefault();
    e.currentTarget.requestSubmit();
    return;
  }
  if (target.tagName === "TEXTAREA" || target.tagName === "BUTTON") return;
  // Skip fields in hidden sections (screens mostly leave hidden parts out instead).
  const fields = Array.from(e.currentTarget.querySelectorAll<HTMLElement>(FIELDS)).filter(
    (el) => el === target || !el.closest("[hidden], .hidden"),
  );
  const i = fields.indexOf(target);
  if (i < 0) return;
  e.preventDefault();
  const next = fields[i + 1];
  if (next) {
    next.focus();
    if (next instanceof HTMLInputElement) next.select();
  }
}

export function FormDialog({
  open,
  onOpenChange,
  title,
  children,
  onSubmit,
  submitLabel = "Save",
  submitting = false,
  cancelLabel,
  extraButtons,
  danger,
  wide,
}: FormDialogProps) {
  function submit(e: FormEvent) {
    e.preventDefault();
    if (!submitting) onSubmit?.();
  }

  return (
    <Dialog.Root open={open} onOpenChange={(o) => !submitting && onOpenChange(o)}>
      <Dialog.Portal>
        <Dialog.Overlay className="modal-back">
          <Dialog.Content className={cn("modal", wide && "wide")} aria-describedby={undefined}>
            <header>
              <Dialog.Title asChild>
                <h2>{title}</h2>
              </Dialog.Title>
              <Dialog.Close className="x" aria-label="Close">
                ×
              </Dialog.Close>
            </header>
            <form noValidate onSubmit={submit} onKeyDown={handleEnterToNext}>
              <div className="body">{children}</div>
              <footer>
                {extraButtons}
                <Dialog.Close asChild>
                  <button type="button" className="btn" disabled={submitting}>
                    {cancelLabel ?? (onSubmit ? "Cancel" : "Close")}
                  </button>
                </Dialog.Close>
                {onSubmit && (
                  <button
                    type="submit"
                    className={cn("btn", danger ? "danger" : "primary")}
                    disabled={submitting}
                  >
                    {submitting ? "Saving…" : submitLabel}
                  </button>
                )}
              </footer>
            </form>
          </Dialog.Content>
        </Dialog.Overlay>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
