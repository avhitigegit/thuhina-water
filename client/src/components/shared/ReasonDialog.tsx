"use client";

/*
 * Ask for a reason before an action that is kept in the audit log – reversals, cancellations,
 * deactivation (prototype UI.prompt). The reason is required.
 */
import { useEffect, useId, useState, type ReactNode } from "react";
import { FormDialog } from "./FormDialog";

export function ReasonDialog({
  open,
  onOpenChange,
  title,
  label = "Reason",
  confirmLabel = "OK",
  danger,
  busy,
  children,
  onConfirm,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  label?: string;
  confirmLabel?: string;
  danger?: boolean;
  busy?: boolean;
  /** Text shown above the reason box. */
  children?: ReactNode;
  onConfirm: (reason: string) => void;
}) {
  const id = useId();
  const [reason, setReason] = useState("");
  const [missing, setMissing] = useState(false);

  useEffect(() => {
    if (open) {
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setReason("");
      setMissing(false);
    }
  }, [open]);

  function submit() {
    const text = reason.trim();
    if (!text) {
      setMissing(true);
      return;
    }
    onConfirm(text);
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={title}
      onSubmit={submit}
      submitLabel={confirmLabel}
      submitting={busy}
      danger={danger}
    >
      {children}
      <div className="field req">
        <label htmlFor={id}>{label}</label>
        <textarea
          id={id}
          rows={3}
          autoFocus
          className={missing ? "invalid" : undefined}
          value={reason}
          onChange={(e) => {
            setReason(e.target.value);
            if (e.target.value.trim()) setMissing(false);
          }}
        />
        {missing && <span className="error">Enter a reason.</span>}
      </div>
    </FormDialog>
  );
}
