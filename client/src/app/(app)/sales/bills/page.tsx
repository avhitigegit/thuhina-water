"use client";

/*
 * Sales & Bills (M06, prototype sales/sales-bills.html, FR-41): find a bill by system or paper bill number (with the
 * "already entered / not entered yet" banner), customer, type and dates (last 14 days by default); click a bill for
 * its details, reversal and print. "+ New ▾" opens First purchase, Extra order, Product sale, Collect empties,
 * Customer-damaged bottle and Settle bottles owed. ?bill=B000275 searches (and opens) a bill; ?new=first&cust=C0012
 * opens a new entry for that customer (links from the customer panel).
 */
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { MenuButton } from "@/components/shared/MenuButton";
import { MoneyText } from "@/components/shared/MoneyText";
import { Badge } from "@/components/shared/StatusBadge";
import { Can, useCan } from "@/features/auth/access";
import { CustomerPicker } from "@/features/customers/CustomerPicker";
import { useBills, usePaperBillCheck } from "@/features/sales/api";
import { BillDialog } from "@/features/sales/BillDialog";
import { isSaleKind, KINDS, kindBadge, NEW_ENTRIES, type SaleKind } from "@/features/sales/labels";
import { NewSaleDialog } from "@/features/sales/SaleDialogs";
import { api } from "@/lib/api/client";
import type { CustomerLookupRow, TxnList, TxnRow } from "@/lib/api/types";
import { addDaysIso, formatDate, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useDebounced } from "@/lib/useDebounced";

const PAGE_SIZE = 50;
const SYSTEM_BILL = /^B\d{6}$/i;

/** FR-41 banner for a typed bill number: a system number is looked up in the list, a paper number on the server. */
function BillCheck({ q, list }: { q: string; list: TxnList | undefined }) {
  const system = SYSTEM_BILL.test(q);
  const paper = usePaperBillCheck(system ? "" : q);
  if (system) {
    if (!list) return null;
    const row = list.items.find((r) => r.billNo.toUpperCase() === q.toUpperCase());
    return row ? (
      <div className="banner warn">
        Bill <b>{row.billNo}</b> is already entered – {row.kindLabel}, {row.customerName},{" "}
        {formatDate(row.date)}. Do not enter it again.
      </div>
    ) : (
      <div className="banner ok">
        Bill <b>{q.toUpperCase()}</b> has not been entered yet.
      </div>
    );
  }
  if (!paper.data) return null;
  return <div className={paper.data.entered ? "banner warn" : "banner ok"}>{paper.data.message}</div>;
}

const columns: Column<TxnRow>[] = [
  {
    key: "bill",
    header: "Bill no.",
    cell: (x) => (
      <>
        <b className="mono">{x.billNo}</b>
        {x.paperBillNo && <div className="small muted">paper {x.paperBillNo}</div>}
      </>
    ),
  },
  { key: "date", header: "Date", cell: (x) => <span className="nowrap">{formatDate(x.date)}</span> },
  {
    key: "type",
    header: "Type",
    cell: (x) => (
      <>
        <Badge kind={kindBadge(x.kind)}>{x.kindLabel}</Badge>
        {x.reversed && (
          <>
            {" "}
            <Badge kind="bad">reversed</Badge>
          </>
        )}
        {x.reversalOfBillNo && <div className="small muted">cancels {x.reversalOfBillNo}</div>}
      </>
    ),
  },
  {
    key: "customer",
    header: "Customer",
    cell: (x) => (
      <>
        {x.customerName}
        {x.walkIn && <span className="small muted"> (walk-in)</span>}
      </>
    ),
  },
  {
    key: "bottles",
    header: "Bottles",
    align: "num",
    cell: (x) => (x.filled || x.empties ? `${x.filled}/${x.empties}` : ""),
  },
  {
    key: "amount",
    header: "Amount",
    align: "num",
    cell: (x) => (Number(x.amount) ? <MoneyText value={x.amount} /> : ""),
  },
  {
    key: "paid",
    header: "Paid",
    align: "num",
    cell: (x) => (Number(x.paid) ? <MoneyText value={x.paid} /> : ""),
  },
];

function SalesBillsPage() {
  const params = useSearchParams();
  const canEdit = useCan(P.SALES_EDIT);
  const today = todayIso();
  const billParam = params.get("bill") ?? "";
  const [bill, setBill] = useState(billParam);
  const [customer, setCustomer] = useState<CustomerLookupRow | null>(null);
  const [kind, setKind] = useState("");
  const [from, setFrom] = useState<string | null>(addDaysIso(today, -14));
  const [to, setTo] = useState<string | null>(today);
  const q = useDebounced(bill.trim(), 300);
  // A bill number searches all bills (the other filters are ignored, as in the prototype).
  const filters = q
    ? { q, customerId: null, kind: "", from: null, to: null }
    : { q: "", customerId: customer?.id ?? null, kind, from, to };
  const filterKey = JSON.stringify(filters);
  const [paging, setPaging] = useState({ key: filterKey, page: 0 });
  const page = paging.key === filterKey ? paging.page : 0;
  const list = useBills({ ...filters, page, size: PAGE_SIZE });

  const [openId, setOpenId] = useState<number | null>(null);
  const [newSale, setNewSale] = useState<{ kind: SaleKind; customer: CustomerLookupRow | null } | null>(null);

  // ?bill=B000275 opens that bill once it is in the list.
  const [billOpened, setBillOpened] = useState(false);
  const exact = billParam
    ? list.data?.items.find(
        (r) => r.billNo.toUpperCase() === billParam.toUpperCase() || r.paperBillNo === billParam,
      )
    : undefined;
  if (exact && !billOpened && q === billParam.trim()) {
    setBillOpened(true);
    setOpenId(exact.id);
  }

  // ?new=first&cust=C0012 opens the entry for that customer (Admin).
  const newParam = params.get("new");
  const custParam = params.get("cust");
  useEffect(() => {
    if (!canEdit || !isSaleKind(newParam)) return;
    let cancelled = false;
    const open = (c: CustomerLookupRow | null) => !cancelled && setNewSale({ kind: newParam, customer: c });
    if (!custParam) open(null);
    else
      api<CustomerLookupRow[]>("/customers/lookup", { query: { q: custParam } })
        .then((rows) => open(rows.find((r) => r.code === custParam.toUpperCase()) ?? rows[0] ?? null))
        .catch(() => open(null));
    return () => {
      cancelled = true;
    };
  }, [canEdit, newParam, custParam]);

  return (
    <>
      <Can permission={P.SALES_EDIT}>
        <PageActions>
          <Link className="btn" href="/sales/enter-bills">
            Enter daily bills
          </Link>
          <MenuButton
            items={NEW_ENTRIES.map((e) => ({
              label: e.label,
              hint: e.hint,
              onSelect: () => setNewSale({ kind: e.kind, customer: null }),
            }))}
          >
            + New ▾
          </MenuButton>
        </PageActions>
      </Can>
      <div className="card">
        <div className="toolbar">
          <div className="field">
            <label htmlFor="b-bill">Bill no. / paper bill no.</label>
            <input
              id="b-bill"
              value={bill}
              placeholder="e.g. B000275 or 41275"
              style={{ width: 190 }}
              onChange={(e) => setBill(e.target.value)}
            />
          </div>
          <div className="field grow">
            <label htmlFor="b-cust">Customer</label>
            <CustomerPicker id="b-cust" value={customer} onChange={setCustomer} placeholder="Any customer" />
          </div>
          <div className="field">
            <label htmlFor="b-kind">Type</label>
            <select id="b-kind" value={kind} onChange={(e) => setKind(e.target.value)}>
              <option value="">All</option>
              {KINDS.map((k) => (
                <option key={k.value} value={k.value}>
                  {k.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="b-from">From</label>
            <DateField id="b-from" value={from} onChange={setFrom} />
          </div>
          <div className="field">
            <label htmlFor="b-to">To</label>
            <DateField id="b-to" value={to} onChange={setTo} />
          </div>
        </div>
        {q && <BillCheck q={q} list={list.data} />}
        {list.isError ? (
          <div className="banner bad">{list.error.message}</div>
        ) : (
          <DataTable
            columns={columns}
            page={list.data}
            loading={list.isPending}
            state={{
              page,
              size: PAGE_SIZE,
              sort: null,
              setPage: (p) => setPaging({ key: filterKey, page: p }),
              setSort: () => undefined,
              params: { page, size: PAGE_SIZE },
            }}
            rowKey={(x) => x.id}
            rowClassName={(x) => (x.reversed || x.kind === "REVERSAL" ? "row-muted" : undefined)}
            onRowClick={(x) => setOpenId(x.id)}
            empty="No bills found."
          />
        )}
      </div>

      {openId !== null && (
        <BillDialog billId={openId} onClose={() => setOpenId(null)} onOpenBill={setOpenId} />
      )}
      {newSale && (
        <NewSaleDialog
          kind={newSale.kind}
          initialCustomer={newSale.customer}
          onClose={() => setNewSale(null)}
        />
      )}
    </>
  );
}

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <SalesBillsPage />
    </Suspense>
  );
}
