"use client";

/*
 * Bottles & Products (M02, prototype master-data/bottles-products.html):
 * Bottle types · Other products · Prices & deposits · Accepted old bottles. Admin only.
 */
import { Tabs, useHashTab } from "@/components/shared/Tabs";
import { BottleTypesTab } from "@/features/masterdata/BottleTypesTab";
import { BrandsTab } from "@/features/masterdata/BrandsTab";
import { PricesTab } from "@/features/masterdata/PricesTab";
import { ProductsTab } from "@/features/masterdata/ProductsTab";

const TABS = ["bottles", "products", "prices", "brands"] as const;

export default function BottlesProductsPage() {
  const [tab, setTab] = useHashTab(TABS);
  return (
    <>
      <Tabs
        tabs={[
          { id: "bottles", label: "Bottle types" },
          { id: "products", label: "Other products" },
          { id: "prices", label: "Prices & deposits" },
          { id: "brands", label: "Accepted old bottles" },
        ]}
        value={tab}
        onChange={setTab}
      />
      {tab === "bottles" && <BottleTypesTab />}
      {tab === "products" && <ProductsTab />}
      {tab === "prices" && <PricesTab />}
      {tab === "brands" && <BrandsTab />}
    </>
  );
}
