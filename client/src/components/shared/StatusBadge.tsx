/* Coloured status label (prototype UI.statusBadge). Unknown statuses are grey. */
import { cn } from "@/lib/utils";

export type BadgeKind = "" | "ok" | "warn" | "bad" | "info" | "dark";

const STATUS_KIND: Record<string, BadgeKind> = {
  Active: "ok",
  Inactive: "",
  Draft: "",
  Approved: "info",
  Sent: "info",
  "Partly received": "warn",
  Received: "ok",
  Cancelled: "bad",
  Open: "info",
  "Quotes received": "warn",
  Selected: "ok",
  Closed: "",
  Paid: "ok",
  "Part paid": "warn",
  Unpaid: "bad",
  "At factory": "warn",
  "Partly returned": "warn",
  Returned: "ok",
  Cash: "",
  Credit: "info",
  "Monthly bill": "dark",
  Company: "warn",
  Customer: "bad",
  Overdue: "bad",
  Entered: "ok",
  Due: "info",
  Accepted: "ok",
  Rejected: "bad",
  Expired: "warn",
  Agreed: "info",
  Standard: "",
};

export function Badge({ kind = "", children }: { kind?: BadgeKind; children: React.ReactNode }) {
  return <span className={cn("badge", kind)}>{children}</span>;
}

export function StatusBadge({ status, kind }: { status: string; kind?: BadgeKind }) {
  return <Badge kind={kind ?? STATUS_KIND[status] ?? ""}>{status}</Badge>;
}
