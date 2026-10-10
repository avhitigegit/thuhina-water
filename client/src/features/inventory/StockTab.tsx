"use client";

/*
 * Stock tab (FR-27, FR-31, prototype inventory/inventory.html): bottles by status – filled in red below the minimum,
 * in circulation, minimum filled editable in the table (saves on change); product stock (red when 3 or fewer) with
 * its cost value. (The "With customers = held + to collect + owed" line comes with Customers, M05.)
 */
import { useState, type KeyboardEvent } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { MoneyText } from "@/components/shared/MoneyText";
import { useCan } from "@/features/auth/access";
import { ApiError } from "@/lib/api/client";
import type { BottleStock, ProductStock, StockOverview } from "@/lib/api/types";
import { formatNumber } from "@/lib/format";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useSetMinLevel } from "./api";
import { BUCKETS, isBottleLow, LOW_PRODUCT_STOCK } from "./labels";

/** Minimum filled level, saved when the value changes (blur or Enter), as in the prototype. */
function MinLevelInput({ bottle }: { bottle: BottleStock }) {
  const save = useSetMinLevel();
  const saved = bottle.minFilled == null ? "" : String(bottle.minFilled);
  const [value, setValue] = useState(saved);
  const [lastSaved, setLastSaved] = useState(saved);
  // A new value from the server (another user, a reload) replaces what is shown.
  if (saved !== lastSaved) {
    setLastSaved(saved);
    setValue(saved);
  }

  function commit() {
    if (value === saved) return;
    if (!/^\d+$/.test(value)) {
      toast("Minimum level: a whole number, 0 or more.", "bad");
      setValue(saved);
      return;
    }
    save.mutate(
      { code: bottle.code, minFilled: Number(value) },
      {
        onSuccess: () => toast("Minimum level saved", "ok"),
        onError: (err) => {
          toast(err instanceof ApiError ? err.message : "Something went wrong.", "bad");
          setValue(saved);
        },
      },
    );
  }

  return (
    <input
      type="number"
      min="0"
      step="1"
      className="num"
      style={{ width: 80 }}
      aria-label={`Minimum filled ${bottle.label}`}
      value={value}
      disabled={save.isPending}
      onChange={(e) => setValue(e.target.value)}
      onBlur={commit}
      onKeyDown={(e: KeyboardEvent<HTMLInputElement>) => {
        if (e.key === "Enter") e.currentTarget.blur();
      }}
    />
  );
}

export function StockTab({ stock }: { stock: StockOverview | undefined }) {
  const canEdit = useCan(P.STOCK_EDIT);

  const bottleColumns: Column<BottleStock>[] = [
    { key: "bottle", header: "Bottle", cell: (b) => <b>{b.name}</b> },
    ...BUCKETS.map((k): Column<BottleStock> => ({
      key: k.key,
      header: k.label,
      align: "num",
      cell: (b) =>
        k.key === "filled" && isBottleLow(b) ? (
          <b style={{ color: "var(--bad)" }}>{formatNumber(b.filled)}</b>
        ) : (
          formatNumber(b[k.key])
        ),
    })),
    {
      key: "circulation",
      header: "In circulation",
      align: "num",
      cell: (b) => <b>{formatNumber(b.inCirculation)}</b>,
    },
    {
      key: "min",
      header: "Min. filled",
      align: "num",
      cell: (b) => (canEdit ? <MinLevelInput bottle={b} /> : (b.minFilled ?? "–")),
    },
  ];

  const productColumns: Column<ProductStock>[] = [
    { key: "name", header: "Product", cell: (p) => p.name },
    {
      key: "stock",
      header: "In stock",
      align: "num",
      cell: (p) =>
        p.stockQty <= LOW_PRODUCT_STOCK ? <b style={{ color: "var(--bad)" }}>{p.stockQty}</b> : p.stockQty,
    },
    { key: "value", header: "Cost value", align: "num", cell: (p) => <MoneyText value={p.costValue} /> },
  ];

  return (
    <>
      <div className="card">
        <h2>Bottles</h2>
        <DataTable
          columns={bottleColumns}
          rows={stock?.bottles}
          loading={!stock}
          rowKey={(b) => b.code}
          empty="No active bottle types."
        />
        <p className="small muted" style={{ marginTop: 8 }}>
          Stock changes automatically from goods receipts, factory batches, deliveries, collections, old
          bottles handed in and write-offs. Change the minimum level in the table to set the low-stock alert.
        </p>
      </div>
      <div className="card">
        <h2>Other products</h2>
        <DataTable
          columns={productColumns}
          rows={stock?.products}
          loading={!stock}
          rowKey={(p) => p.id}
          empty="No products."
        />
      </div>
    </>
  );
}
