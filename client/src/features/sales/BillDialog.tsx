"use client";

/*
 * Bill details (prototype sales-bills.html showBill, BR-14): bill no. (+ paper), type, date, customer, the lines in
 * words, amount, paid, note, entered by; banners for an approved over-limit sale, a reversed bill (with the reason)
 * and a reversal entry; the stock and bottle changes. Buttons: Reverse this bill (reason required), Print bill
 * (A5 PDF in a new tab), Close.
 */
import Link from "next/link";
import { useState } from "react";
import { FormDialog } from "@/components/shared/FormDialog";
import { ReasonDialog } from "@/components/shared/ReasonDialog";
import { Badge } from "@/components/shared/StatusBadge";
import { useCan } from "@/features/auth/access";
import { ApiError } from "@/lib/api/client";
import type { TxnDetail } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney } from "@/lib/format";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { billPdfUrl, useBill, useReverseBill } from "./api";
import { kindBadge, lineText, payMethodLabel } from "./labels";

export function openBillPdf(id: number) {
  window.open(billPdfUrl(id), "_blank", "noopener");
}

function canReverse(b: TxnDetail) {
  return !b.reversedBy && b.kind !== "REVERSAL" && b.kind !== "OPENING_BALANCE";
}

export function BillDialog({
  billId,
  onClose,
  onOpenBill,
}: {
  billId: number;
  onClose: () => void;
  /** Opens another bill (the reversal of this bill, or the bill a reversal cancels). */
  onOpenBill?: (id: number) => void;
}) {
  const bill = useBill(billId);
  const canEdit = useCan(P.SALES_EDIT);
  const reverse = useReverseBill();
  const [asking, setAsking] = useState(false);
  const b = bill.data;

  function doReverse(reason: string) {
    if (!b) return;
    reverse.mutate(
      { id: b.id, body: { reason } },
      {
        onSuccess: (r) => {
          setAsking(false);
          toast(`Bill reversed – stock, bottles and balance corrected (${r.txn.billNo})`, "ok");
          onClose();
        },
        onError: (err) => {
          setAsking(false);
          toast(err instanceof ApiError ? err.message : "The bill could not be reversed.", "bad");
        },
      },
    );
  }

  const link = (l: { id: number; billNo: string }) =>
    onOpenBill ? (
      <button type="button" className="btn link" onClick={() => onOpenBill(l.id)}>
        {l.billNo}
      </button>
    ) : (
      <b className="mono">{l.billNo}</b>
    );

  return (
    <>
      <FormDialog
        open={!asking}
        wide
        onOpenChange={(o) => !o && onClose()}
        title={b ? `Bill ${b.billNo}` : "Bill"}
        extraButtons={
          b && (
            <>
              {canEdit && canReverse(b) && (
                <button type="button" className="btn danger" onClick={() => setAsking(true)}>
                  Reverse this bill
                </button>
              )}
              <button type="button" className="btn" onClick={() => openBillPdf(b.id)}>
                Print bill
              </button>
            </>
          )
        }
      >
        {bill.isError ? (
          <div className="banner bad">{bill.error.message}</div>
        ) : !b ? (
          <p className="muted">Loading…</p>
        ) : (
          <>
            <div className="summary">
              <span className="muted">Bill no.</span>
              <b className="mono">
                {b.billNo}
                {b.paperBillNo && <span className="muted"> (paper {b.paperBillNo})</span>}
              </b>
              <span className="muted">Type</span>
              <b>
                <Badge kind={kindBadge(b.kind)}>{b.kindLabel}</Badge>
              </b>
              <span className="muted">Date</span>
              <b>{formatDate(b.date)}</b>
              <span className="muted">Customer</span>
              <b>
                {b.customerCode ? (
                  <Link href={`/master-data/customers?id=${b.customerCode}`}>{b.customerName}</Link>
                ) : (
                  <>
                    {b.customerName} <span className="muted">(walk-in)</span>
                  </>
                )}
              </b>
              <span className="muted">Details</span>
              <b>
                {b.lines.length ? b.lines.map((l, i) => <div key={i}>{lineText(l)}</div>) : "–"}
                {b.damageMode && (
                  <div className="small muted">
                    {b.damageMode === "REDUCE" ? "Not replaced – holding reduced" : "New bottle bought"}
                  </div>
                )}
                {b.settleMethod && (
                  <div className="small muted">
                    {b.settleMethod === "DEPOSIT"
                      ? "Deposit paid – customer keeps the bottles"
                      : "Bottles returned"}
                  </div>
                )}
              </b>
              <span className="muted">Amount</span>
              <b className="num">{formatMoney(b.amount)}</b>
              <span className="muted">Paid on bill</span>
              <b className="num">
                {formatMoney(b.paid)}
                {b.payMethod && Number(b.paid) !== 0 && (
                  <span className="muted small"> ({payMethodLabel(b.payMethod)})</span>
                )}
              </b>
              {b.note && (
                <>
                  <span className="muted">Note</span>
                  <b>{b.note}</b>
                </>
              )}
              {b.phoneConfirmed && (
                <>
                  <span className="muted">Phone</span>
                  <b>Customer agreed by phone</b>
                </>
              )}
              <span className="muted">Entered by</span>
              <b>
                {b.createdByName ?? b.createdBy} · {formatDateTime(b.createdAt)}
              </b>
            </div>
            {b.creditOverrideBy && (
              <div className="banner warn" style={{ marginTop: 10 }}>
                Over the credit limit – approved by {b.creditOverrideByName ?? b.creditOverrideBy}
              </div>
            )}
            {b.reversedBy && (
              <div className="banner bad" style={{ marginTop: 10 }}>
                Reversed by {link(b.reversedBy)} on {formatDate(b.reversedBy.date)}: {b.reversedBy.note}
              </div>
            )}
            {b.reversalOf && (
              <div className="banner warn" style={{ marginTop: 10 }}>
                This entry cancels an earlier bill: {link(b.reversalOf)} of {formatDate(b.reversalOf.date)}.
              </div>
            )}
            {b.effects.length > 0 && (
              <>
                <div className="section-title">Stock and bottle changes</div>
                <div className="small">
                  {b.effects.map((e, i) => (
                    <div key={i}>
                      {e.item} · {e.place}{" "}
                      <b style={e.change < 0 ? { color: "var(--bad)" } : undefined}>
                        {e.change > 0 ? "+" : ""}
                        {e.change}
                      </b>
                    </div>
                  ))}
                </div>
              </>
            )}
            {canReverse(b) && (
              <p className="small muted" style={{ marginTop: 10 }}>
                Saved bills are never deleted. To correct a mistake, reverse the bill and enter it again.
              </p>
            )}
          </>
        )}
      </FormDialog>
      {b && (
        <ReasonDialog
          open={asking}
          onOpenChange={(o) => !o && setAsking(false)}
          title={`Reverse bill ${b.billNo}?`}
          label="Reason for the correction"
          confirmLabel="Reverse"
          danger
          busy={reverse.isPending}
          onConfirm={doReverse}
        >
          <p className="small muted">
            A reversal entry cancels the stock, bottles and money of this bill. The paper bill number can then
            be entered again.
          </p>
        </ReasonDialog>
      )}
    </>
  );
}
