"use client";

/*
 * Purchasing page (M09, prototype purchasing/purchasing.html, FR-15 – FR-21): tabs Quotations · Purchase orders ·
 * Supplier payments. A purchase order created from a quotation opens on the Purchase orders tab.
 * Admin full; Accountant view only (no buttons).
 */
import { useState } from "react";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { todayIso } from "@/lib/format";
import { PoDialog, PurchaseOrdersTab } from "./PurchaseOrdersTab";
import { QuotationsTab } from "./QuotationsTab";
import { SupplierPaymentsTab } from "./SupplierPaymentsTab";

const TABS = ["quotes", "pos", "pay"] as const;

export function Purchasing({ today = todayIso() }: { today?: string }) {
  const [tab, setTab] = useHashTab(TABS);
  const [po, setPo] = useState<number | null>(null);
  return (
    <>
      <Tabs
        tabs={[
          { id: "quotes", label: "Quotations" },
          { id: "pos", label: "Purchase orders" },
          { id: "pay", label: "Supplier payments" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "quotes" && (
        <QuotationsTab
          today={today}
          onOpenPo={(id) => {
            setTab("pos");
            setPo(id);
          }}
        />
      )}
      {tab === "pos" && <PurchaseOrdersTab onOpen={setPo} />}
      {tab === "pay" && <SupplierPaymentsTab today={today} />}
      {po !== null && <PoDialog id={po} today={today} onClose={() => setPo(null)} />}
    </>
  );
}
