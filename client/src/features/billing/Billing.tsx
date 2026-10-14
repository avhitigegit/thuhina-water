"use client";

/*
 * Billing & Payments (M11, prototype billing/billing.html, FR-47 – FR-52): action + Receive payment (billing.edit);
 * tabs Customer accounts · Monthly invoices. Links from other screens: ?pay=C0012 (or ?pay=new) opens Receive payment,
 * ?statement=C0012 the Statement pop-up, ?invoice=<id> the invoice. Admin and Accountant have full access.
 */
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { Can, useCan } from "@/features/auth/access";
import { CustomerPanel } from "@/features/customers/CustomerPanel";
import { findMatch } from "@/features/customers/CustomerPicker";
import { api } from "@/lib/api/client";
import type { CustomerLookupRow, PaymentSaved } from "@/lib/api/types";
import { todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { AccountsTab } from "./AccountsTab";
import { InvoiceDialog, InvoicesTab } from "./InvoicesTab";
import { PaymentSavedDialog, ReceivePaymentDialog, StatementDialog } from "./PaymentDialogs";

const TABS = ["accounts", "invoices"] as const;

type Dialog =
  | { kind: "pay"; customer: CustomerLookupRow | null }
  | { kind: "saved"; saved: PaymentSaved }
  | { kind: "statement"; customer: { id: number; name: string } }
  | { kind: "invoice"; id: number }
  | null;

/** The customer a link means (?pay=C0012): the code, phone or name lookup's match. */
async function lookup(text: string): Promise<CustomerLookupRow | null> {
  const rows = await api<CustomerLookupRow[]>("/customers/lookup", { query: { q: text } }).catch(() => []);
  return findMatch(text, rows) ?? rows.find((r) => r.code === text.toUpperCase()) ?? null;
}

/** Removes the link's ?pay= / ?statement= / ?invoice= so the same link works again later. */
function clearLinkParams() {
  try {
    window.history.replaceState(window.history.state, "", window.location.pathname + window.location.hash);
  } catch {
    /* ignore */
  }
}

export function Billing({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const canPay = useCan(P.BILLING_EDIT);
  const [tab, setTab] = useHashTab(TABS);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [panelId, setPanelId] = useState<number | null>(null);

  const payParam = params.get("pay");
  const statementParam = params.get("statement");
  const invoiceParam = params.get("invoice");
  useEffect(() => {
    let cancelled = false;
    const open = (d: Dialog) => {
      if (cancelled || !d) return;
      setPanelId(null);
      setDialog(d);
    };
    if (payParam && canPay) {
      void (payParam === "new" ? Promise.resolve(null) : lookup(payParam)).then((c) =>
        open({ kind: "pay", customer: c }),
      );
    } else if (statementParam) {
      void lookup(statementParam).then((c) => c && open({ kind: "statement", customer: c }));
    } else if (invoiceParam && /^\d+$/.test(invoiceParam)) {
      void Promise.resolve().then(() => open({ kind: "invoice", id: Number(invoiceParam) }));
    }
    return () => {
      cancelled = true;
    };
  }, [payParam, statementParam, invoiceParam, canPay]);

  const close = () => {
    setDialog(null);
    clearLinkParams();
  };

  return (
    <>
      <Can permission={P.BILLING_EDIT}>
        <PageActions>
          <button
            className="btn primary"
            type="button"
            onClick={() => setDialog({ kind: "pay", customer: null })}
          >
            + Receive payment
          </button>
        </PageActions>
      </Can>

      <Tabs
        tabs={[
          { id: "accounts", label: "Customer accounts" },
          { id: "invoices", label: "Monthly invoices" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "accounts" && (
        <AccountsTab
          onOpen={setPanelId}
          onReceive={(a) =>
            setDialog({
              kind: "pay",
              customer: {
                id: a.customerId,
                code: a.code,
                name: a.name,
                phone: a.phone,
                areaName: "",
                status: a.status,
              },
            })
          }
          onStatement={(a) => setDialog({ kind: "statement", customer: { id: a.customerId, name: a.name } })}
        />
      )}
      {tab === "invoices" && (
        <InvoicesTab onOpen={setPanelId} onView={(id) => setDialog({ kind: "invoice", id })} />
      )}

      <CustomerPanel customerId={panelId} onClose={() => setPanelId(null)} />
      {dialog?.kind === "pay" && (
        <ReceivePaymentDialog
          initialCustomer={dialog.customer}
          today={today}
          onClose={close}
          onSaved={(saved) => setDialog({ kind: "saved", saved })}
        />
      )}
      {dialog?.kind === "saved" && <PaymentSavedDialog saved={dialog.saved} onClose={close} />}
      {dialog?.kind === "statement" && (
        <StatementDialog customer={dialog.customer} today={today} onClose={close} />
      )}
      {dialog?.kind === "invoice" && <InvoiceDialog invoiceId={dialog.id} onClose={close} />}
    </>
  );
}
