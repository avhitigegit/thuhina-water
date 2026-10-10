"use client";

/*
 * Audit log tab (FR-57, prototype admin/administration.html): from / to (default the last 7 days), user, action,
 * record type and search; newest first; paged on the server. Reversals are highlighted. Read-only.
 */
import { useEffect, useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { Badge } from "@/components/shared/StatusBadge";
import type { AuditEntry } from "@/lib/api/types";
import { addDaysIso, formatDateTime, todayIso } from "@/lib/format";
import { useAuditFilters, useAuditLog } from "./api";
import { actionBadgeKind, actionLabel } from "./labels";

const PAGE_SIZE = 50;

function useDebounced<T>(value: T, ms: number): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

const columns: Column<AuditEntry>[] = [
  {
    key: "ts",
    header: "When",
    cell: (a) => <span className="nowrap">{formatDateTime(a.ts)}</span>,
  },
  {
    key: "user",
    header: "User",
    cell: (a) => (
      <>
        {a.fullName ?? a.username ?? "system"}
        <div className="small muted">{a.roleName ?? (a.username === "system" ? "System" : "")}</div>
      </>
    ),
  },
  {
    key: "action",
    header: "Action",
    cell: (a) => <Badge kind={actionBadgeKind(a.action)}>{actionLabel(a.action)}</Badge>,
  },
  {
    key: "record",
    header: "Record",
    cell: (a) => (
      <>
        {a.entity} {a.ref && <span className="mono small">{a.ref}</span>}
      </>
    ),
  },
  { key: "details", header: "Details", cell: (a) => <span className="small">{a.details}</span> },
];

export function AuditTab() {
  const today = todayIso();
  const [from, setFrom] = useState<string | null>(addDaysIso(today, -7));
  const [to, setTo] = useState<string | null>(today);
  const [user, setUser] = useState("");
  const [action, setAction] = useState("");
  const [entity, setEntity] = useState("");
  const [search, setSearch] = useState("");
  const q = useDebounced(search.trim(), 300);
  // The page belongs to the filters it was chosen with – a new filter starts again at the first page.
  const filterKey = JSON.stringify([from, to, user, action, entity, q]);
  const [paging, setPaging] = useState({ key: filterKey, page: 0 });
  const page = paging.key === filterKey ? paging.page : 0;
  const setPage = (p: number) => setPaging({ key: filterKey, page: p });

  const badRange = from !== null && to !== null && to < from;
  const filters = useAuditFilters();
  const log = useAuditLog({ from, to: badRange ? from : to, user, action, entity, q, page, size: PAGE_SIZE });

  return (
    <div className="card">
      <div className="toolbar">
        <div className="field">
          <label htmlFor="audit-from">From</label>
          <DateField id="audit-from" value={from} onChange={setFrom} />
        </div>
        <div className="field">
          <label htmlFor="audit-to">To</label>
          <DateField id="audit-to" value={to} onChange={setTo} invalid={badRange} />
        </div>
        <div className="field">
          <label htmlFor="audit-user">User</label>
          <select id="audit-user" value={user} onChange={(e) => setUser(e.target.value)}>
            <option value="">All</option>
            {filters.data?.users.map((u) => (
              <option key={u.username} value={u.username}>
                {u.fullName ? `${u.fullName} (${u.username})` : u.username}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="audit-action">Action</label>
          <select id="audit-action" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All</option>
            {filters.data?.actions.map((a) => (
              <option key={a} value={a}>
                {actionLabel(a)}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="audit-entity">Record type</label>
          <select id="audit-entity" value={entity} onChange={(e) => setEntity(e.target.value)}>
            <option value="">All</option>
            {filters.data?.entities.map((e) => (
              <option key={e} value={e}>
                {e}
              </option>
            ))}
          </select>
        </div>
        <div className="field grow">
          <label htmlFor="audit-q">Search</label>
          <input
            id="audit-q"
            value={search}
            placeholder="Bill no., customer, PO…"
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>
      {badRange && <div className="banner warn">&apos;To&apos; must be on or after &apos;From&apos;.</div>}
      {log.isError ? (
        <div className="banner bad">{log.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          page={log.data}
          loading={log.isPending}
          state={{
            page,
            size: PAGE_SIZE,
            sort: null,
            setPage,
            setSort: () => undefined,
            params: { page, size: PAGE_SIZE },
          }}
          rowKey={(a) => a.id}
          rowClassName={(a) => (a.action === "REVERSE" ? "row-bad" : undefined)}
          empty="No entries."
        />
      )}
    </div>
  );
}
