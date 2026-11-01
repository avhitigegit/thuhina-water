"use client";

/*
 * One quotation (prototype quotations.html "openQ", FR-65, FR-66): steps bar, banners (expired, not accepted, accepted with
 * the customer and prices), details, lines with the price now beside a changed price, history; buttons by step and role:
 * Print / PDF, Email (mailto with the server's text), Make a copy, Edit and Next: Mark as sent (draft), Not accepted (reason)
 * and Next: Accepted (sent, also expired), Apply prices to customer (from a date; warns which planned prices it cancels,
 * N-14) and Register as customer (accepted). Send on WhatsApp (draft or sent): the PDF to the customer's WhatsApp number or
 * the phone; a new organisation must have agreed.
 */
import Link from "next/link";
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { ReasonDialog } from "@/components/shared/ReasonDialog";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { StepBar } from "@/components/shared/StepBar";
import { useCan } from "@/features/auth/access";
import { useCustomerPrices } from "@/features/customers/api";
import { ApiError } from "@/lib/api/client";
import type { CqView } from "@/lib/api/types";
import { formatDate, formatDateTime, formatMoney } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import {
  quotationPdfUrl,
  useQuotation,
  useQuotationStep,
  useQuotationWhatsApp,
  useSendQuotationWhatsApp,
  type Step,
} from "./api";
import { applyWarnings } from "./applyWarnings";

export const SENT_VIA = [
  { value: "EMAIL", label: "Email" },
  { value: "BY_HAND", label: "By hand" },
  { value: "POST", label: "Post" },
  { value: "WHATSAPP", label: "WhatsApp" },
] as const;

export function openQuotationPdf(id: number) {
  window.open(quotationPdfUrl(id), "_blank", "noopener");
}

/** The email program with the quotation's message ready (the user attaches the PDF). */
export function emailHref(q: CqView): string {
  return `mailto:${encodeURIComponent(q.email ?? "")}?subject=${encodeURIComponent(q.emailSubject)}&body=${encodeURIComponent(q.emailBody)}`;
}

type Sub = "send" | "whatsapp" | "reject" | "apply" | null;

export function QuotationView({
  id,
  today,
  onClose,
  onEdit,
  onCopy,
}: {
  id: number;
  today: string;
  onClose: () => void;
  onEdit: (q: CqView) => void;
  onCopy: (q: CqView) => void;
}) {
  const canEdit = useCan(P.QUOTATIONS_EDIT);
  const canPrices = useCan(P.CUSTOMER_PRICES_EDIT);
  const canRegister = useCan(P.CUSTOMERS_EDIT);
  const query = useQuotation(id);
  const step = useQuotationStep();
  const [sub, setSub] = useState<Sub>(null);
  const q = query.data;

  function run(s: Step, body: object, after?: () => void) {
    step.mutate(
      { id, step: s, body },
      {
        onSuccess: (saved) => {
          toast(saved.message, "ok");
          setSub(null);
          after?.();
        },
        onError: (err) => toast(errorText(err), "bad"),
      },
    );
  }

  if (!q) {
    return (
      <FormDialog open wide onOpenChange={(o) => !o && onClose()} title="Quotation">
        {query.isError ? (
          <div className="banner bad">{query.error.message}</div>
        ) : (
          <p className="muted">Loading…</p>
        )}
      </FormDialog>
    );
  }

  const rejected = q.status === "REJECTED";
  const steps = ["Draft", "Sent", rejected ? "Not accepted" : "Accepted"];
  const current = q.status === "DRAFT" ? "Draft" : q.status === "SENT" ? "Sent" : steps[2];

  const buttons = (
    <>
      <button type="button" className="btn" onClick={() => openQuotationPdf(q.id)}>
        Print / PDF
      </button>
      {q.email && (
        <a className="btn" href={emailHref(q)}>
          Email
        </a>
      )}
      {canEdit && (
        <button type="button" className="btn" onClick={() => onCopy(q)}>
          Make a copy
        </button>
      )}
      {canEdit && (q.status === "DRAFT" || q.status === "SENT") && (
        <button type="button" className="btn" onClick={() => setSub("whatsapp")}>
          Send on WhatsApp
        </button>
      )}
      {canEdit && q.canEdit && (
        <>
          <button type="button" className="btn" onClick={() => onEdit(q)}>
            Edit
          </button>
          <button type="button" className="btn primary" onClick={() => setSub("send")}>
            Next: Mark as sent
          </button>
        </>
      )}
      {canEdit && q.canAnswer && (
        <>
          <button type="button" className="btn danger" onClick={() => setSub("reject")}>
            Not accepted
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={step.isPending}
            onClick={() => run("accept", {})}
          >
            Next: Accepted
          </button>
        </>
      )}
      {canEdit && canPrices && q.canApplyPrices && (
        <button type="button" className="btn primary" onClick={() => setSub("apply")}>
          Apply prices to customer
        </button>
      )}
      {canEdit && canRegister && q.canRegister && (
        <Link className="btn primary" href={`/master-data/customers?fromQuote=${q.id}`}>
          Register as customer
        </Link>
      )}
    </>
  );

  return (
    <>
      <FormDialog
        open={sub === null}
        wide
        onOpenChange={(o) => !o && onClose()}
        title={`Quotation ${q.cqNo}`}
        extraButtons={buttons}
      >
        <StepBar steps={steps} current={current} />
        {q.shown === "EXPIRED" && (
          <div className="banner warn">
            Valid until {formatDate(q.validUntil)} – this quotation has expired. Record the answer, or make a
            copy with new dates.
          </div>
        )}
        {rejected && <div className="banner bad">Not accepted: {q.rejectReason}</div>}
        {q.status === "ACCEPTED" && (
          <div className="banner ok">
            Accepted on {formatDate(q.acceptedOn)}.{" "}
            {q.customerId ? (
              <>
                Customer{" "}
                <Link href={`/master-data/customers?id=${q.customerCode}`}>
                  {q.customerCode} – {q.orgName}
                </Link>
                .{" "}
                {q.pricesAppliedOn ? (
                  `Water prices applied as agreed prices from ${formatDate(q.pricesAppliedOn)}.`
                ) : q.hasWater ? (
                  <b>Quoted water prices are not applied to the customer yet.</b>
                ) : null}
              </>
            ) : (
              "Not yet registered as a customer – registering applies the quoted water prices."
            )}
          </div>
        )}
        <div className="summary" style={{ marginBottom: 10 }}>
          <span className="muted">To</span>
          <b>
            {q.orgName}
            {q.customerCode && ` (${q.customerCode})`}
          </b>
          <span className="muted">Contact</span>
          <b>{[q.contact, q.designation].filter(Boolean).join(", ") || "–"}</b>
          <span className="muted">Address</span>
          <b>{q.address || "–"}</b>
          <span className="muted">Phone / email</span>
          <b>{[q.phone, q.email].filter(Boolean).join(" · ") || "–"}</b>
          <span className="muted">Subject</span>
          <b>{q.subject || "–"}</b>
          <span className="muted">Date · valid until</span>
          <b>
            {formatDate(q.quoteDate)} · {formatDate(q.validUntil)}
          </b>
          <span className="muted">Delivery</span>
          <b>{q.deliveryText || "–"}</b>
          <span className="muted">Payment terms</span>
          <b>{q.termsLabel}</b>
          <span className="muted">Prices</span>
          <b>{q.customerTypeName} price list</b>
          <span className="muted">Status</span>
          <b>
            <StatusBadge status={q.shownLabel} />
            {q.sentViaText && (
              <span className="small muted">
                {" "}
                sent {formatDate(q.sentOn)} {q.sentViaText}
              </span>
            )}
          </b>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>#</th>
                <th>Description</th>
                <th className="num">Qty</th>
                <th>Unit</th>
                <th className="num">Unit price</th>
                <th className="num">Amount</th>
              </tr>
            </thead>
            <tbody>
              {q.lines.map((l) => (
                <tr key={l.lineNo}>
                  <td>{l.lineNo}</td>
                  <td>{l.description}</td>
                  <td className="num">{l.qty.toLocaleString("en-US")}</td>
                  <td>{l.unit}</td>
                  <td className="num">
                    {formatMoney(l.unitPrice)}
                    {l.currentPrice != null && Number(l.currentPrice) !== Number(l.unitPrice) && (
                      <div
                        className="small muted"
                        style={l.belowCurrent ? { color: "var(--warn)" } : undefined}
                      >
                        {l.currentAgreed ? "agreed" : "standard"} {formatMoney(l.currentPrice)}
                      </div>
                    )}
                  </td>
                  <td className="num">{formatMoney(l.amount)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={5} className="num">
                  Total
                </td>
                <td className="num">{formatMoney(q.total)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
        <div className="section-title">History</div>
        <div className="small" data-testid="cq-history">
          {q.history.map((h, i) => (
            <div key={i}>
              {formatDateTime(h.at)} – {h.label} by {h.byName}
              {h.note && ` (${h.note})`}
            </div>
          ))}
        </div>
      </FormDialog>

      {sub === "send" && (
        <SendDialog
          q={q}
          today={today}
          busy={step.isPending}
          onCancel={() => setSub(null)}
          onSend={(b) => run("send", b)}
        />
      )}
      {sub === "whatsapp" && <WhatsAppDialog q={q} onClose={() => setSub(null)} />}
      {sub === "reject" && (
        <ReasonDialog
          open
          onOpenChange={(o) => !o && setSub(null)}
          title={`${q.cqNo} not accepted`}
          label="Reason (e.g. price too high, chose another supplier)"
          confirmLabel="Save"
          danger
          busy={step.isPending}
          onConfirm={(reason) => run("reject", { reason })}
        />
      )}
      {sub === "apply" && (
        <ApplyDialog
          q={q}
          today={today}
          busy={step.isPending}
          onCancel={() => setSub(null)}
          onApply={(from) => run("apply-prices", { from })}
        />
      )}
    </>
  );
}

/** Send on WhatsApp: the number (customer's WhatsApp number or the phone) and, for a new organisation, their agreement. */
function WhatsAppDialog({ q, onClose }: { q: CqView; onClose: () => void }) {
  const status = useQuotationWhatsApp(q.id);
  const send = useSendQuotationWhatsApp();
  const [to, setTo] = useState<string | null>(null);
  const [agreed, setAgreed] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const number = to ?? status.data?.toNumber ?? "";
  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Send ${q.cqNo} on WhatsApp`}
      submitLabel="Send on WhatsApp"
      submitting={send.isPending}
      onSubmit={() => {
        setErrors({});
        send.mutate(
          { id: q.id, to: number, agreed },
          {
            onSuccess: (s) => {
              toast(s.text, "ok");
              onClose();
            },
            onError: (err) => {
              const fields = err instanceof ApiError ? err.fieldErrors : {};
              setErrors(fields);
              if (Object.keys(fields).length === 0) toast(errorText(err), "bad");
            },
          },
        );
      }}
    >
      {status.data && status.data.state !== "NONE" && (
        <div className="banner info" data-testid="cq-whatsapp-status">
          {status.data.text}
        </div>
      )}
      {status.data && !status.data.canSend && status.data.state === "NONE" && (
        <div className="banner warn">{status.data.text}</div>
      )}
      <p className="small muted">
        The quotation PDF goes from the business WhatsApp number.
        {q.status === "DRAFT" && " The quotation is marked as sent by WhatsApp."}
      </p>
      <div className="form-grid" style={{ gridTemplateColumns: "1fr" }}>
        <div className="field req">
          <label htmlFor="cq-wa-to">WhatsApp number</label>
          <input
            id="cq-wa-to"
            value={number}
            placeholder="077 123 4567"
            onChange={(e) => setTo(e.target.value)}
            className={errors.to ? "invalid" : undefined}
          />
          {errors.to && <span className="error">{errors.to}</span>}
        </div>
        {!q.customerId && (
          <div className="field">
            <label>
              <input type="checkbox" checked={agreed} onChange={(e) => setAgreed(e.target.checked)} />{" "}
              {q.orgName} agreed to get WhatsApp messages from us
            </label>
            {errors.agreed && <span className="error">{errors.agreed}</span>}
          </div>
        )}
      </div>
    </FormDialog>
  );
}

function SendDialog({
  q,
  today,
  busy,
  onCancel,
  onSend,
}: {
  q: CqView;
  today: string;
  busy: boolean;
  onCancel: () => void;
  onSend: (body: { via: string; date: string | null }) => void;
}) {
  const [via, setVia] = useState<string>(q.email ? "EMAIL" : "BY_HAND");
  const [date, setDate] = useState<string | null>(today);
  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onCancel()}
      title={`Mark ${q.cqNo} as sent`}
      onSubmit={() => onSend({ via, date })}
      submitLabel="Mark as sent"
      submitting={busy}
      extraButtons={
        <>
          <button type="button" className="btn" onClick={() => openQuotationPdf(q.id)}>
            Print / PDF
          </button>
          {q.email && (
            <a className="btn" href={emailHref(q)}>
              Email
            </a>
          )}
        </>
      }
    >
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req">
          <label htmlFor="cq-via">Sent by</label>
          <select id="cq-via" value={via} onChange={(e) => setVia(e.target.value)}>
            {SENT_VIA.map((v) => (
              <option key={v.value} value={v.value}>
                {v.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field req">
          <label htmlFor="cq-sent">Date sent</label>
          <DateField id="cq-sent" value={date} onChange={setDate} today={today} />
        </div>
      </div>
      <p className="small muted">
        Print it or save it as PDF first. For email, the Email button opens your email program with the
        message ready – attach the PDF.
      </p>
    </FormDialog>
  );
}

function ApplyDialog({
  q,
  today,
  busy,
  onCancel,
  onApply,
}: {
  q: CqView;
  today: string;
  busy: boolean;
  onCancel: () => void;
  onApply: (from: string) => void;
}) {
  const [from, setFrom] = useState<string | null>(today);
  const prices = useCustomerPrices(q.customerId ?? 0, q.customerId != null);
  const warnings = from && prices.data ? applyWarnings(q.lines, prices.data, from) : [];
  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onCancel()}
      title="Apply quoted prices?"
      onSubmit={() => from && onApply(from)}
      submitLabel="Apply prices"
      submitting={busy}
    >
      <p>
        The water prices in {q.cqNo} become the agreed prices of <b>{q.orgName}</b> from the date below. Bills
        already saved keep their price.
      </p>
      <div className="field req" style={{ maxWidth: 220 }}>
        <label htmlFor="cq-from">From</label>
        <DateField id="cq-from" value={from} onChange={setFrom} today={today} />
      </div>
      {warnings.length > 0 && (
        <div className="banner warn" role="alert">
          This also changes planned prices:
          <ul style={{ margin: "4px 0 0", paddingLeft: 18 }}>
            {warnings.map((w) => (
              <li key={w}>{w}</li>
            ))}
          </ul>
        </div>
      )}
    </FormDialog>
  );
}
