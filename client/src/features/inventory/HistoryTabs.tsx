"use client";

/*
 * Damage & adjustments tab (FR-29, FR-30) and Movements tab (every posting, filter by item and text, paged) of the
 * Stock page (prototype inventory/inventory.html).
 */
import { useEffect, useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import type { DamageListRow, MovementRow, StockOverview } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import { useDamages, useMovements } from "./api";
import { BUCKETS, KIND_LABEL } from "./labels";

const damageColumns: Column<DamageListRow>[] = [
  { key: "date", header: "Date", cell: (r) => <span className="nowrap">{formatDate(r.date)}</span> },
  {
    key: "kind",
    header: "Type",
    cell: (r) => {
      const k = KIND_LABEL[r.kind] ?? { label: r.kind, kind: "" as const };
      return (
        <>
          <Badge kind={k.kind}>{k.label}</Badge>
          {r.reversed && (
            <>
              {" "}
              <Badge>reversed</Badge>
            </>
          )}
          <div className="small muted mono">{r.ref}</div>
        </>
      );
    },
  },
  { key: "item", header: "Item", cell: (r) => r.itemName },
  { key: "qty", header: "Qty", align: "num", cell: (r) => r.qty },
  {
    key: "resp",
    header: "Responsibility",
    cell: (r) =>
      r.responsibility ? (
        <StatusBadge status={r.responsibility === "COMPANY" ? "Company" : "Customer"} />
      ) : null,
  },
  { key: "where", header: "Where", cell: (r) => r.where ?? "" },
  { key: "customer", header: "Customer", cell: (r) => r.customerName ?? "" },
  { key: "reason", header: "Reason", cell: (r) => r.reason },
  { key: "by", header: "By", cell: (r) => r.createdByName ?? r.createdBy },
];

export function DamagesTab() {
  const damages = useDamages();
  return (
    <div className="card">
      <h2>Damaged and lost bottles</h2>
      {damages.isError ? (
        <div className="banner bad">{damages.error.message}</div>
      ) : (
        <DataTable
          columns={damageColumns}
          rows={damages.data}
          loading={damages.isPending}
          rowKey={(r) => r.ref}
          empty="No damage or adjustments recorded."
        />
      )}
    </div>
  );
}

/** +3 in green, −2 in red, nothing for 0 (prototype sign()). */
function Signed({ value }: { value: number }) {
  if (!value) return null;
  return (
    <span style={{ color: value > 0 ? "var(--ok)" : "var(--bad)" }}>
      {value > 0 ? "+" : "−"}
      {Math.abs(value)}
    </span>
  );
}

const movementColumns: Column<MovementRow>[] = [
  { key: "date", header: "Date", cell: (m) => <span className="nowrap">{formatDate(m.date)}</span> },
  {
    key: "movement",
    header: "Movement",
    cell: (m) => (
      <>
        {m.description}
        <div className="small muted">
          <span className="mono">{m.docNo}</span>
          {m.docNo && " · "}
          {m.createdByName ?? m.createdBy}
        </div>
      </>
    ),
  },
  { key: "item", header: "Item", cell: (m) => m.itemName },
  ...BUCKETS.map((b): Column<MovementRow> => ({
    key: b.key,
    header: b.label,
    align: "num",
    cell: (m) => <Signed value={m[b.key]} />,
  })),
  { key: "product", header: "Product", align: "num", cell: (m) => <Signed value={m.product} /> },
];

const PAGE_SIZE = 50;

export function MovementsTab({ stock }: { stock: StockOverview | undefined }) {
  const [item, setItem] = useState("");
  const [search, setSearch] = useState("");
  const [q, setQ] = useState("");
  useEffect(() => {
    const t = setTimeout(() => setQ(search.trim()), 300);
    return () => clearTimeout(t);
  }, [search]);
  // The page belongs to the filters it was chosen with – a new filter starts again at the first page.
  const filterKey = item + "|" + q;
  const [paging, setPaging] = useState({ key: filterKey, page: 0 });
  const page = paging.key === filterKey ? paging.page : 0;
  const moves = useMovements({ item, q, page, size: PAGE_SIZE });

  return (
    <div className="card">
      <div className="toolbar">
        <div className="field">
          <label htmlFor="m-item">Item</label>
          <select id="m-item" value={item} onChange={(e) => setItem(e.target.value)}>
            <option value="">All</option>
            {stock?.bottles.map((b) => (
              <option key={b.code} value={b.code}>
                {b.name}
              </option>
            ))}
            {stock?.products.map((p) => (
              <option key={p.code} value={p.code}>
                {p.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field grow">
          <label htmlFor="m-q">Search</label>
          <input
            id="m-q"
            value={search}
            placeholder="Bill no., customer, batch…"
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>
      {moves.isError ? (
        <div className="banner bad">{moves.error.message}</div>
      ) : (
        <DataTable
          columns={movementColumns}
          page={moves.data}
          loading={moves.isPending}
          state={{
            page,
            size: PAGE_SIZE,
            sort: null,
            setPage: (p) => setPaging({ key: filterKey, page: p }),
            setSort: () => undefined,
            params: { page, size: PAGE_SIZE },
          }}
          rowKey={(m, i) => `${m.sourceType}-${m.docNo}-${m.itemCode}-${i}`}
          empty="No movements."
        />
      )}
    </div>
  );
}
