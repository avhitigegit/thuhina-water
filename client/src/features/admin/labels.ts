/* Words and badge colours of the Administration page (prototype admin/administration.html). */
import type { BadgeKind } from "@/components/shared/StatusBadge";
import type { RoleCode } from "@/lib/api/types";

export const ROLES: { code: RoleCode; label: string }[] = [
  { code: "ADMIN", label: "Admin" },
  { code: "ACCOUNTANT", label: "Accountant" },
  { code: "DELIVERY_STAFF", label: "Delivery Staff" },
];

/** Admin dark, Accountant blue, Delivery Staff grey – as in the prototype users table. */
export function roleBadgeKind(role: string): BadgeKind {
  return role === "ADMIN" ? "dark" : role === "ACCOUNTANT" ? "info" : "";
}

/** "CREATE" → "Create". */
export function actionLabel(action: string): string {
  return action.charAt(0) + action.slice(1).toLowerCase();
}

const ACTION_KIND: Record<string, BadgeKind> = {
  CREATE: "ok",
  UPDATE: "info",
  REVERSE: "bad",
  APPROVE: "warn",
  IMPORT: "dark",
};

export function actionBadgeKind(action: string): BadgeKind {
  return ACTION_KIND[action] ?? "";
}
