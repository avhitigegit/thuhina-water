/* Dashboard number tile (prototype .kpi). `alert` shows it in red; `href` makes the tile a link. */
import Link from "next/link";
import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

export function KpiCard({
  label,
  value,
  hint,
  alert,
  href,
}: {
  label: string;
  value: ReactNode;
  hint?: ReactNode;
  alert?: boolean;
  href?: string;
}) {
  const body = (
    <>
      <div className="label">{label}</div>
      <div className="value">{value}</div>
      {hint && <div className="hint">{hint}</div>}
    </>
  );
  return <div className={cn("kpi", alert && "alert")}>{href ? <Link href={href}>{body}</Link> : body}</div>;
}

export function KpiGrid({ children }: { children: ReactNode }) {
  return <div className="kpis">{children}</div>;
}
