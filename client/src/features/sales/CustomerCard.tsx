"use client";

/*
 * Customer balance card on the sale pop-ups (prototype UI.custCard): bottles held, empties to collect and bottles
 * owed per bottle type, outstanding and credit limit – "after this bill" from the live calculation, with the change
 * ("+1 (was 2)"). Without a calculation it shows the balances now.
 */
import Link from "next/link";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { deliveryText, payLabel } from "@/features/customers/labels";
import type { BottlesAfter, CreditView, CustomerDetail } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";

function Delta({ now, after, money }: { now: number; after: number; money?: boolean }) {
  const d = after - now;
  if (Math.abs(d) < 0.005) return null;
  return (
    <div className={d > 0 ? "delta up" : "delta down"}>
      {d > 0 ? "+" : "-"}
      {money ? formatMoney(Math.abs(d)) : `${Math.abs(d)} (was ${now})`}
    </div>
  );
}

const FIELDS = [
  { key: "held", label: "Bottles held", cls: "" },
  { key: "toCollect", label: "Empties to collect", cls: "hl" },
  { key: "owed", label: "Bottles owed", cls: "bad" },
] as const;

export function CustomerCard({
  customer: c,
  after,
  credit,
}: {
  customer: CustomerDetail;
  after?: BottlesAfter[];
  credit?: CreditView | null;
}) {
  const balanceAfter = credit ? Number(credit.after) : c.outstanding;
  const rows = c.bottles.filter((b, i) => {
    const a = after?.find((x) => x.bottleTypeCode === b.bottleTypeCode);
    return i === 0 || b.held + b.toCollect + b.owed > 0 || (a && a.held + a.toCollect + a.owed > 0);
  });
  return (
    <div className="card" style={{ padding: 10, margin: 0 }}>
      <div className="card-head">
        <div>
          <h3 style={{ margin: 0 }}>
            <Link href={`/master-data/customers?id=${c.code}`}>{c.name}</Link>
          </h3>
          <div className="small muted">
            {c.code} · {c.typeName} · {c.areaName} · {c.phone}
          </div>
        </div>
        <div>
          <StatusBadge status={payLabel(c.payType)} />
          {c.status !== "ACTIVE" && (
            <>
              {" "}
              <Badge kind="bad">Inactive</Badge>
            </>
          )}
        </div>
      </div>
      <div className="balances">
        {rows.map((b) => {
          const a = after?.find((x) => x.bottleTypeCode === b.bottleTypeCode);
          return FIELDS.map((f) => {
            const now = b[f.key];
            const value = a ? a[f.key] : now;
            return (
              <div
                key={b.bottleTypeCode + f.key}
                className={value > 0 && f.cls ? `bal ${f.cls}` : "bal"}
                data-testid={`bal-${b.bottleTypeCode}-${f.key}`}
              >
                <div className="label">
                  {f.label} ({b.label})
                </div>
                <div className="value">{value}</div>
                <Delta now={now} after={value} />
              </div>
            );
          });
        })}
        <div className={balanceAfter > 0 ? "bal hl" : "bal"} data-testid="bal-outstanding">
          <div className="label">Outstanding</div>
          <div className="value" style={{ fontSize: "1rem" }}>
            {formatMoney(balanceAfter)}
          </div>
          <Delta now={c.outstanding} after={balanceAfter} money />
        </div>
        {c.payType !== "CASH" && (
          <div className={balanceAfter > c.creditLimit ? "bal bad" : "bal"}>
            <div className="label">Credit limit</div>
            <div className="value" style={{ fontSize: "1rem" }}>
              {formatMoney(c.creditLimit)}
            </div>
            <div className="small muted">{c.termsDays} days</div>
          </div>
        )}
      </div>
      <div className="small muted" style={{ marginTop: 8 }}>
        Delivery: {deliveryText(c.deliveryDay, c.cycleWeeks, c.nextDeliveryDate)} · usual{" "}
        {Object.entries(c.usualQty)
          .filter(([, q]) => q > 0)
          .map(([code, q]) => `${q} × ${c.prices.find((p) => p.bottleTypeCode === code)?.label ?? code}`)
          .join(", ") || "–"}{" "}
        · price{" "}
        {c.prices
          .filter((p) => p.price != null)
          .map((p) => `${p.label} ${formatMoney(p.price)}${p.agreed ? " (agreed)" : ""}`)
          .join(", ")}
      </div>
    </div>
  );
}
