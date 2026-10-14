"use client";

/*
 * Customer accounts (FR-48, FR-51, prototype billing.html): KPIs (total outstanding and each age bucket, 90+ in red),
 * search / payment type / show filters, one row per customer with a balance – 0–30 / 31–60 / 61–90 (amber) / 90+ (red),
 * balance ("over limit" in red), Receive / Statement – totals of every matching row; then recent payments and cash
 * collected (FR-47). The customer name opens the customer panel.
 */
import { useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { KpiCard, KpiGrid } from "@/components/shared/KpiCard";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { useCan } from "@/features/auth/access";
import { PAY_TYPES } from "@/features/customers/labels";
import type { AccountRow, RecentMoney } from "@/lib/api/types";
import { formatDate, formatMoney, formatNumber } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useDebounced } from "@/lib/useDebounced";
import { useAccounts, useAccountSummary, useRecentMoney } from "./api";

const PAGE_SIZE = 50;

/** Bucket amounts as whole rupees ("7,900"), empty when nothing (prototype). */
function bucket(v: number | string): string {
  const n = Math.round(Number(v));
  return n ? formatNumber(n) : "";
}

export function AccountsTab({
  onOpen,
  onReceive,
  onStatement,
}: {
  onOpen: (customerId: number) => void;
  onReceive: (a: AccountRow) => void;
  onStatement: (a: AccountRow) => void;
}) {
  const canPay = useCan(P.BILLING_EDIT);
  const summary = useAccountSummary();
  const [search, setSearch] = useState("");
  const q = useDebounced(search.trim(), 300);
  const [pay, setPay] = useState("");
  const [show, setShow] = useState("");
  const filterKey = JSON.stringify([q, pay, show]);
  const [paging, setPaging] = useState({ key: filterKey, page: 0 });
  const page = paging.key === filterKey ? paging.page : 0;
  const list = useAccounts({ q, pay, show, page, size: PAGE_SIZE });
  const s = summary.data;
  const t = list.data?.totals;

  const columns: Column<AccountRow>[] = [
    {
      key: "customer",
      header: "Customer",
      cell: (a) => (
        <>
          <button
            type="button"
            className="btn link"
            style={{ fontWeight: 600 }}
            onClick={() => onOpen(a.customerId)}
          >
            {a.name}
          </button>
          <div className="small muted">
            {a.code} · {a.phone}
          </div>
        </>
      ),
    },
    { key: "pays", header: "Pays", cell: (a) => <StatusBadge status={a.payTypeLabel} /> },
    {
      key: "limit",
      header: "Limit",
      align: "num",
      cell: (a) => (Number(a.creditLimit) ? formatMoney(a.creditLimit) : ""),
    },
    { key: "b0", header: "0–30", align: "num", cell: (a) => bucket(a.days0to30) },
    { key: "b30", header: "31–60", align: "num", cell: (a) => bucket(a.days31to60) },
    {
      key: "b60",
      header: "61–90",
      align: "num",
      cell: (a) => bucket(a.days61to90) && <b style={{ color: "var(--warn)" }}>{bucket(a.days61to90)}</b>,
    },
    {
      key: "b90",
      header: "90+",
      align: "num",
      cell: (a) => bucket(a.over90) && <b style={{ color: "var(--bad)" }}>{bucket(a.over90)}</b>,
    },
    {
      key: "balance",
      header: "Balance",
      align: "num",
      cell: (a) => (
        <>
          <b
            style={a.overLimit ? { color: "var(--bad)" } : undefined}
            title={a.overLimit ? "Over credit limit" : undefined}
          >
            {formatMoney(a.balance)}
          </b>
          {a.overLimit && (
            <div className="small" style={{ color: "var(--bad)" }}>
              over limit
            </div>
          )}
          {Number(a.balance) < 0 && <div className="small muted">advance</div>}
        </>
      ),
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (a) => (
        <>
          {canPay && Number(a.balance) > 0 && (
            <>
              <button className="btn sm primary" type="button" onClick={() => onReceive(a)}>
                Receive
              </button>{" "}
            </>
          )}
          <button className="btn sm" type="button" onClick={() => onStatement(a)}>
            Statement
          </button>
        </>
      ),
    },
  ];

  return (
    <>
      {summary.isError && <div className="banner bad">{summary.error.message}</div>}
      {s && (
        <KpiGrid>
          <KpiCard label="Total outstanding" value={formatMoney(s.totalOutstanding)} />
          <KpiCard label="0–30 days" value={formatMoney(s.days0to30)} />
          <KpiCard label="31–60 days" value={formatMoney(s.days31to60)} />
          <KpiCard label="61–90 days" value={formatMoney(s.days61to90)} />
          <KpiCard label="Over 90 days" value={formatMoney(s.over90)} alert={Number(s.over90) > 0} />
        </KpiGrid>
      )}
      <div className="card">
        <div className="toolbar">
          <div className="field grow">
            <label htmlFor="acc-q">Search</label>
            <input
              id="acc-q"
              placeholder="Customer name, code or phone"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="acc-pay">Payment type</label>
            <select id="acc-pay" value={pay} onChange={(e) => setPay(e.target.value)}>
              <option value="">All</option>
              {PAY_TYPES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="acc-show">Show</label>
            <select id="acc-show" value={show} onChange={(e) => setShow(e.target.value)}>
              <option value="">All with a balance</option>
              <option value="over">Over credit limit</option>
              <option value="old">Older than 30 days</option>
            </select>
          </div>
        </div>
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
            rowKey={(a) => a.customerId}
            empty="No balances."
            footer={
              t && [
                "Total",
                "",
                "",
                bucket(t.days0to30),
                bucket(t.days31to60),
                bucket(t.days61to90),
                bucket(t.over90),
                <b key="t">{formatMoney(t.balance)}</b>,
                "",
              ]
            }
          />
        )}
      </div>
      <RecentCard onOpen={onOpen} />
    </>
  );
}

function RecentCard({ onOpen }: { onOpen: (customerId: number) => void }) {
  const recent = useRecentMoney();
  const columns: Column<RecentMoney>[] = [
    { key: "date", header: "Date", cell: (r) => formatDate(r.date) },
    {
      key: "customer",
      header: "Customer",
      cell: (r) =>
        r.customerId ? (
          <button type="button" className="btn link" onClick={() => onOpen(r.customerId!)}>
            {r.customerName}
          </button>
        ) : (
          r.customerName
        ),
    },
    {
      key: "how",
      header: "How",
      cell: (r) => (
        <>
          {r.how}
          {r.kind === "PAYMENT" && <div className="small muted mono">{r.ref}</div>}
        </>
      ),
    },
    { key: "amount", header: "Amount", align: "num", cell: (r) => formatMoney(r.amount) },
  ];
  return (
    <div className="card">
      <h2>Recent payments and cash collected</h2>
      {recent.isError ? (
        <div className="banner bad">{recent.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={recent.data}
          loading={recent.isPending}
          rowKey={(r) => `${r.kind}-${r.id}`}
          empty="No payments yet."
        />
      )}
    </div>
  );
}
