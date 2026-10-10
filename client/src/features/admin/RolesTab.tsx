"use client";

/* Roles & access tab (design 8.2): read-only – the roles are fixed and set in the system. */
import { DataTable, type Column } from "@/components/shared/DataTable";
import { Badge } from "@/components/shared/StatusBadge";
import type { AccessCell, RolesMatrixResponse } from "@/lib/api/types";
import { useRolesMatrix } from "./api";

type Row = RolesMatrixResponse["rows"][number];

function CellView({ cell }: { cell: AccessCell }) {
  if (cell.level === "NONE") return <span className="muted">–</span>;
  return (
    <>
      {cell.level === "FULL" ? <Badge kind="ok">Full</Badge> : <Badge kind="info">View only</Badge>}
      {cell.note && <div className="small muted">{cell.note}</div>}
    </>
  );
}

export function RolesTab() {
  const matrix = useRolesMatrix();
  const roles = matrix.data?.roles ?? [];
  const columns: Column<Row>[] = [
    { key: "area", header: "Menu", cell: (r) => r.area },
    ...roles.map((role, i): Column<Row> => ({
      key: role.role,
      header: role.roleName,
      align: "center",
      cell: (r) => <CellView cell={r.access[i]} />,
    })),
  ];

  return (
    <div className="card">
      <h2>What each role can do</h2>
      {matrix.isError ? (
        <div className="banner bad">{matrix.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={matrix.data?.rows}
          loading={matrix.isPending}
          rowKey={(r) => r.area}
        />
      )}
      <p className="small muted" style={{ marginTop: 8 }}>
        The three roles are fixed. Each user has one role, chosen on the Users tab; the server checks every
        action against it.
      </p>
    </div>
  );
}
