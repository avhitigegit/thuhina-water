"use client";

/*
 * "Customer agreed prices" card on Bottles & Products → Prices (FR-05, prototype bottles-products.html): in force and
 * scheduled (or all, including ended), with the standard price now and the difference; the customer links to the
 * Customers page.
 */
import Link from "next/link";
import { useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { MoneyText } from "@/components/shared/MoneyText";
import { Badge } from "@/components/shared/StatusBadge";
import type { CustomerPriceRow } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import { useAllCustomerPrices } from "./api";
import { STATE_BADGE } from "./labels";

const columns: Column<CustomerPriceRow>[] = [
  {
    key: "customer",
    header: "Customer",
    cell: (e) => (
      <>
        <Link href={`/master-data/customers?id=${e.customerCode}`}>
          <b>{e.customerName}</b>
        </Link>
        <div className="small muted">
          {e.customerCode} · {e.customerTypeName} · about {e.bottlesPerMonth} {e.label} a month
        </div>
      </>
    ),
  },
  { key: "bottle", header: "Bottle", cell: (e) => e.label },
  {
    key: "agreed",
    header: "Agreed",
    align: "num",
    cell: (e) => (
      <b>
        <MoneyText value={e.price} />
      </b>
    ),
  },
  {
    key: "standard",
    header: "Standard now",
    align: "num",
    cell: (e) => {
      if (e.standardNow == null) return "–";
      const diff = Math.round(((e.price - e.standardNow) / e.standardNow) * 100);
      return (
        <>
          <MoneyText value={e.standardNow} />
          <div className="small muted">{diff < 0 ? `−${-diff}%` : diff > 0 ? `+${diff}%` : ""}</div>
        </>
      );
    },
  },
  { key: "from", header: "From", cell: (e) => formatDate(e.validFrom) },
  { key: "until", header: "Until", cell: (e) => (e.validUntil ? formatDate(e.validUntil) : "–") },
  {
    key: "reason",
    header: "Reason",
    cell: (e) => (
      <>
        {e.reason}
        <div className="small muted">{e.createdByName ?? e.createdBy}</div>
      </>
    ),
  },
  {
    key: "state",
    header: "",
    cell: (e) => {
      const s = STATE_BADGE[e.state] ?? { label: e.state, kind: "" as const };
      return <Badge kind={s.kind}>{s.label}</Badge>;
    },
  },
];

export function AgreedPricesCard() {
  const [status, setStatus] = useState<"current" | "all">("current");
  const rows = useAllCustomerPrices(status);
  return (
    <div className="card">
      <div className="card-head">
        <h2>Customer agreed prices</h2>
        <select
          aria-label="Which agreed prices"
          style={{ width: "auto" }}
          value={status}
          onChange={(e) => setStatus(e.target.value as "current" | "all")}
        >
          <option value="current">In force and scheduled</option>
          <option value="all">All, including ended</option>
        </select>
      </div>
      <p className="small muted">
        The standard price above applies unless a customer has an agreed price (for example a volume price for
        a large factory). Agreed prices are set on the customer – open a customer and click <b>Prices</b>.
      </p>
      {rows.isError ? (
        <div className="banner bad">{rows.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={rows.data}
          loading={rows.isPending}
          rowKey={(e) => e.id}
          empty="No customer has an agreed price – everyone pays the standard price."
        />
      )}
    </div>
  );
}
