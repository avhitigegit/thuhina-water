"use client";

/*
 * Owed to suppliers tab (FR-21, prototype purchasing/purchasing.html): goods received, paid, owed in red, terms, oldest
 * unpaid receipt. Paying a supplier has one home – the Payments page (Money out): "Pay" opens it there.
 */
import Link from "next/link";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { Badge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import type { SupplierBalance } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useSupplierBalances } from "./api";

export function SupplierPaymentsTab() {
  const balances = useSupplierBalances();

  const owedColumns: Column<SupplierBalance>[] = [
    {
      key: "supplier",
      header: "Supplier",
      cell: (b) => (
        <>
          <b>{b.name}</b> <span className="small muted mono">{b.code}</span>
        </>
      ),
    },
    { key: "received", header: "Goods received", align: "num", cell: (b) => formatMoney(b.received) },
    { key: "paid", header: "Paid", align: "num", cell: (b) => formatMoney(b.paid) },
    {
      key: "owed",
      header: "Owed",
      align: "num",
      cell: (b) =>
        Number(b.owed) > 0 ? (
          <b style={{ color: "var(--bad)" }}>{formatMoney(b.owed)}</b>
        ) : (
          formatMoney(b.owed)
        ),
    },
    { key: "terms", header: "Terms", align: "num", cell: (b) => b.termsLabel },
    {
      key: "oldest",
      header: "Oldest unpaid",
      cell: (b) =>
        b.oldestUnpaidDate ? (
          <>
            {formatDate(b.oldestUnpaidDate)} <span className="small muted">({b.oldestUnpaidDays} days)</span>
            {b.overdue && (
              <>
                {" "}
                <Badge kind="bad">Overdue</Badge>
              </>
            )}
          </>
        ) : (
          <span className="muted">–</span>
        ),
    },
    {
      key: "pay",
      header: "",
      className: "actions",
      cell: (b) =>
        Number(b.owed) > 0 && (
          <Can permission={P.PURCHASING_EDIT}>
            <Link className="btn sm" href={`/payments?supplier=${b.supplierId}#out`}>
              Pay
            </Link>
          </Can>
        ),
    },
  ];

  return (
    <>
      <div className="card">
        <div className="card-head">
          <h2>Amounts owed to suppliers</h2>
          <Link className="btn" href="/payments#out">
            Payments made →
          </Link>
        </div>
        {balances.isError ? (
          <div className="banner bad">{balances.error.message}</div>
        ) : (
          <DataTable
            columns={owedColumns}
            rows={balances.data}
            loading={balances.isPending}
            rowKey={(b) => b.supplierId}
            empty="Nothing owed."
          />
        )}
      </div>
    </>
  );
}
