"use client";

/*
 * Suppliers & Factories (M03, prototype master-data/suppliers.html): Suppliers · Filling factories.
 * Admin full; Accountant view only (the shell shows the view-only banner, New / Edit are hidden).
 */
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { FactoriesTab } from "@/features/partners/FactoriesTab";
import { SuppliersTab } from "@/features/partners/SuppliersTab";

const TABS = ["suppliers", "factories"] as const;

export default function SuppliersFactoriesPage() {
  const [tab, setTab] = useHashTab(TABS);
  return (
    <>
      <Tabs
        tabs={[
          { id: "suppliers", label: "Suppliers" },
          { id: "factories", label: "Filling factories" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "suppliers" && <SuppliersTab />}
      {tab === "factories" && <FactoriesTab />}
    </>
  );
}
