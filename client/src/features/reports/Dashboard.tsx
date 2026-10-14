"use client";

/*
 * Dashboard (M12, prototype dashboard/dashboard.html, BRD section 8, FR-31): quick buttons by permission (Enter bills,
 * Receive payment), low-stock banners, KPIs for today (each a link only when the role may open its page), today's deliveries,
 * "Needs attention" and the sales of the last 14 days. One call: GET /dashboard.
 */
import Link from "next/link";
import { PageActions } from "@/components/layout/PageActions";
import { BarChart } from "@/components/shared/BarChart";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import type { AttentionItem, DashDelivery } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";
import { P } from "@/lib/permissions";
import { useDashboard } from "./api";

const deliveryColumns: Column<DashDelivery>[] = [
  {
    key: "customer",
    header: "Customer",
    cell: (d) => (
      <>
        <b>{d.name}</b>
        <div className="small muted">{d.area}</div>
      </>
    ),
  },
  { key: "usual", header: "Usual", align: "num", cell: (d) => d.usual },
  {
    key: "status",
    header: "Status",
    cell: (d) =>
      d.status === "MISSED" ? (
        <Badge kind="bad">{d.statusLabel}</Badge>
      ) : (
        <StatusBadge status={d.statusLabel} />
      ),
  },
];

function Maybe({ href, children }: { href: string | null; children: React.ReactNode }) {
  return href ? <Link href={href}>{children}</Link> : <>{children}</>;
}

function Attention({ item }: { item: AttentionItem }) {
  return (
    <li style={{ margin: "5px 0" }}>
      <Maybe href={item.href}>{item.linkText}</Maybe>
      {item.text}
    </li>
  );
}

export function Dashboard() {
  const q = useDashboard();
  const d = q.data;

  return (
    <>
      <PageActions>
        <Can permission={P.SALES_EDIT}>
          <Link className="btn primary" href="/sales/enter-bills">
            Enter bills
          </Link>
        </Can>
        <Can permission={P.BILLING_EDIT}>
          <Link className="btn" href="/billing?pay=new">
            Receive payment
          </Link>
        </Can>
      </PageActions>

      {q.isError && <div className="banner bad">{q.error.message}</div>}
      {!d ? (
        !q.isError && <div className="card muted">Loading…</div>
      ) : (
        <>
          {d.lowStock.map((a) => (
            <div key={a.bottleTypeCode} className="banner bad">
              <b>Low stock:</b> {a.message.replace(/^Low stock:\s*/, "")}{" "}
              {d.lowStockHref && <Link href={`${d.lowStockHref}?send=1`}>Send empties</Link>}
            </div>
          ))}
          <div className="kpis">
            {d.kpis.map((k) => (
              <div key={k.key} className={k.alert ? "kpi alert" : "kpi"} data-testid={`kpi-${k.key}`}>
                <div className="label">{k.label}</div>
                <div className="value">
                  <Maybe href={k.href}>{k.value}</Maybe>
                </div>
                {k.hint && <div className="hint">{k.hint}</div>}
              </div>
            ))}
          </div>
          <div className="grid side">
            <div className="card">
              <div className="card-head">
                <h2>Today&apos;s deliveries</h2>
                {d.deliveriesHref && <Link href={d.deliveriesHref}>Open delivery list →</Link>}
              </div>
              <DataTable
                columns={deliveryColumns}
                rows={d.deliveries}
                rowKey={(r) => r.customerId}
                empty="No deliveries today."
              />
            </div>
            <div>
              <div className="card">
                <h2>Needs attention</h2>
                <ul style={{ margin: 0, paddingLeft: 18 }} data-testid="attention">
                  {d.attention.length ? (
                    d.attention.map((a, i) => <Attention key={i} item={a} />)
                  ) : (
                    <li className="muted">Nothing pending.</li>
                  )}
                </ul>
              </div>
              <div className="card">
                <h2>Sales – last 14 days</h2>
                <BarChart
                  label="Sales – last 14 days"
                  points={d.sales14.map((s) => ({
                    key: s.date,
                    label: s.date.slice(8),
                    value: Number(s.amount),
                    title: `${formatDate(s.date)}: ${formatMoney(s.amount)}`,
                    shown: formatMoney(s.amount),
                  }))}
                />
              </div>
            </div>
          </div>
        </>
      )}
    </>
  );
}
