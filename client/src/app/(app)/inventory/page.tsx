"use client";

/*
 * Stock (M04, prototype inventory/inventory.html): Stock · Damage & adjustments · Movements, with Record damage and
 * Stock adjustment, and the low-stock banner (FR-31). Admin only.
 */
import { useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { Can } from "@/features/auth/access";
import { useStock } from "@/features/inventory/api";
import { DamagesTab, MovementsTab } from "@/features/inventory/HistoryTabs";
import { AdjustmentDialog, DamageDialog } from "@/features/inventory/StockDialogs";
import { StockTab } from "@/features/inventory/StockTab";
import { P } from "@/lib/permissions";

const TABS = ["stock", "damage", "moves"] as const;

export default function StockPage() {
  const [tab, setTab] = useHashTab(TABS);
  const stock = useStock();
  const [dialog, setDialog] = useState<"damage" | "adjust" | null>(null);

  return (
    <>
      <Can permission={P.STOCK_EDIT}>
        <PageActions>
          <button className="btn" type="button" disabled={!stock.data} onClick={() => setDialog("damage")}>
            Record damage
          </button>
          <button className="btn" type="button" disabled={!stock.data} onClick={() => setDialog("adjust")}>
            Stock adjustment
          </button>
        </PageActions>
      </Can>

      {stock.isError && <div className="banner bad">{stock.error.message}</div>}
      {stock.data?.alerts.map((a) => (
        <div key={a.bottleTypeCode} className="banner bad">
          <b>Low stock:</b> {a.name} – {a.filled} filled in store, minimum {a.minFilled}. {a.atFactory}{" "}
          {a.atFactory === 1 ? "is" : "are"} at the factory.{" "}
          <span className="muted" title="Comes with Filling Factory (M10)">
            Send empties to the factory
          </span>
        </div>
      ))}

      <Tabs
        tabs={[
          { id: "stock", label: "Stock" },
          { id: "damage", label: "Damage & adjustments" },
          { id: "moves", label: "Movements" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "stock" && <StockTab stock={stock.data} />}
      {tab === "damage" && <DamagesTab />}
      {tab === "moves" && <MovementsTab stock={stock.data} />}

      {dialog === "damage" && stock.data && (
        <DamageDialog stock={stock.data} onClose={() => setDialog(null)} />
      )}
      {dialog === "adjust" && stock.data && (
        <AdjustmentDialog stock={stock.data} onClose={() => setDialog(null)} />
      )}
    </>
  );
}
