"use client";

/*
 * Billing (M11, prototype billing/billing.html, FR-48 – FR-52): tabs Customer accounts · Monthly invoices. Receiving a
 * payment has one home – the Payments page (?pay=C0012 there). Links from other screens: ?statement=C0012 opens the
 * Statement pop-up, ?invoice=<id> the invoice. Admin and Accountant have full access.
 */
import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { CustomerPanel } from "@/features/customers/CustomerPanel";
import { todayIso } from "@/lib/format";
import { AccountsTab } from "./AccountsTab";
import { InvoiceDialog, InvoicesTab } from "./InvoicesTab";
import { clearLinkParams, lookup } from "./links";
import { StatementDialog } from "./PaymentDialogs";

const TABS = ["accounts", "invoices"] as const;

type Dialog =
  { kind: "statement"; customer: { id: number; name: string } } | { kind: "invoice"; id: number } | null;

export function Billing({ today = todayIso() }: { today?: string }) {
  const params = useSearchParams();
  const router = useRouter();
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
    if (payParam) {
      // Old links (?pay=…): receiving a payment is on the Payments page.
      router.replace(`/payments?pay=${encodeURIComponent(payParam)}`);
    } else if (statementParam) {
      void lookup(statementParam).then((c) => c && open({ kind: "statement", customer: c }));
    } else if (invoiceParam && /^\d+$/.test(invoiceParam)) {
      void Promise.resolve().then(() => open({ kind: "invoice", id: Number(invoiceParam) }));
    }
    return () => {
      cancelled = true;
    };
  }, [payParam, statementParam, invoiceParam, router]);

  const close = () => {
    setDialog(null);
    clearLinkParams();
  };

  return (
    <>
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
          onStatement={(a) => setDialog({ kind: "statement", customer: { id: a.customerId, name: a.name } })}
        />
      )}
      {tab === "invoices" && (
        <InvoicesTab onOpen={setPanelId} onView={(id) => setDialog({ kind: "invoice", id })} today={today} />
      )}

      <CustomerPanel customerId={panelId} onClose={() => setPanelId(null)} />
      {dialog?.kind === "statement" && (
        <StatementDialog customer={dialog.customer} today={today} onClose={close} />
      )}
      {dialog?.kind === "invoice" && <InvoiceDialog invoiceId={dialog.id} onClose={close} />}
    </>
  );
}
