"use client";

/*
 * Credit limit exceeded (BR-09, prototype UI.withCreditApproval): the server's CREDIT_LIMIT message with the limit,
 * the balance now and after this bill. An Admin may "Approve and save" (the bill is sent again with
 * approveOverLimit = true and the approval goes to the audit log); other users are told only an Admin can approve.
 */
import { FormDialog } from "@/components/shared/FormDialog";
import { useCan } from "@/features/auth/access";
import type { ApiError } from "@/lib/api/client";
import { formatMoney } from "@/lib/format";
import { P } from "@/lib/permissions";

export const CREDIT_LIMIT = "CREDIT_LIMIT";

export function CreditApprovalDialog({
  error,
  busy,
  onApprove,
  onClose,
}: {
  error: ApiError;
  busy?: boolean;
  onApprove: () => void;
  onClose: () => void;
}) {
  const canApprove = useCan(P.SALES_APPROVE_OVER_LIMIT);
  const d = error.details as { limit?: number; balance?: number; after?: number };
  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Credit limit exceeded"
      onSubmit={canApprove ? onApprove : undefined}
      submitLabel="Approve and save"
      submitting={busy}
    >
      <div className="banner bad">{error.message}</div>
      <div className="summary">
        <span>Credit limit</span>
        <b className="num">{formatMoney(d.limit)}</b>
        <span>Current balance</span>
        <b className="num">{formatMoney(d.balance)}</b>
        <span>Balance after this entry</span>
        <b className="num">{formatMoney(d.after)}</b>
      </div>
      {canApprove ? (
        <p className="small muted" style={{ marginTop: 10 }}>
          As Admin you can approve this sale. The approval is recorded in the audit log.
        </p>
      ) : (
        <p className="small muted" style={{ marginTop: 10 }}>
          Only an Admin can approve a sale over the credit limit.
        </p>
      )}
    </FormDialog>
  );
}
