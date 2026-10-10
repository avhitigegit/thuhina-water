"use client";

/*
 * Users tab (prototype admin/administration.html): name with a "you" badge, username, role badge, phone, last login,
 * status, and Edit / Reset password / Deactivate | Activate. Nobody can deactivate their own account or reset their
 * own password here (they use Change password in the top bar).
 */
import { useState } from "react";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { FormDialog } from "@/components/shared/FormDialog";
import { Badge, StatusBadge } from "@/components/shared/StatusBadge";
import { useCurrentUser } from "@/features/auth/access";
import { ApiError } from "@/lib/api/client";
import type { UserResponse } from "@/lib/api/types";
import { formatDateTime } from "@/lib/format";
import { toast } from "@/lib/toast";
import { useResetPassword, useSetUserActive, useUsers } from "./api";
import { roleBadgeKind } from "./labels";
import { UserDialog } from "./UserDialog";

function errorMessage(err: unknown): string {
  return err instanceof ApiError ? err.message : "Something went wrong. Please try again.";
}

export function UsersTab() {
  const me = useCurrentUser();
  const users = useUsers();
  const setActive = useSetUserActive();
  const reset = useResetPassword();

  const [editing, setEditing] = useState<UserResponse | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [dialogKey, setDialogKey] = useState(0);
  const [toReset, setToReset] = useState<UserResponse | null>(null);
  const [toDeactivate, setToDeactivate] = useState<UserResponse | null>(null);
  const [temporary, setTemporary] = useState<{ name: string; password: string } | null>(null);

  const isSelf = (u: UserResponse) => u.id === me.user.id;

  function openEdit(u: UserResponse | null) {
    setEditing(u);
    setDialogKey((k) => k + 1);
    setDialogOpen(true);
  }

  function activate(u: UserResponse) {
    setActive.mutate(
      { id: u.id, active: true },
      {
        onSuccess: () => toast("User activated", "ok"),
        onError: (e) => toast(errorMessage(e), "bad"),
      },
    );
  }

  function confirmDeactivate() {
    if (!toDeactivate) return;
    setActive.mutate(
      { id: toDeactivate.id, active: false },
      {
        onSuccess: () => {
          toast("User deactivated", "ok");
          setToDeactivate(null);
        },
        onError: (e) => {
          toast(errorMessage(e), "bad");
          setToDeactivate(null);
        },
      },
    );
  }

  function confirmReset() {
    if (!toReset) return;
    const name = toReset.fullName;
    reset.mutate(toReset.id, {
      onSuccess: (r) => {
        setToReset(null);
        setTemporary({ name, password: r.temporaryPassword });
      },
      onError: (e) => {
        toast(errorMessage(e), "bad");
        setToReset(null);
      },
    });
  }

  const columns: Column<UserResponse>[] = [
    {
      key: "name",
      header: "Name",
      cell: (u) => (
        <>
          <b>{u.fullName}</b>
          {isSelf(u) && (
            <>
              {" "}
              <Badge kind="info">you</Badge>
            </>
          )}
        </>
      ),
    },
    { key: "username", header: "Username", cell: (u) => <span className="mono">{u.username}</span> },
    { key: "role", header: "Role", cell: (u) => <Badge kind={roleBadgeKind(u.role)}>{u.roleName}</Badge> },
    { key: "phone", header: "Phone", cell: (u) => u.phone ?? "" },
    {
      key: "lastLogin",
      header: "Last login",
      cell: (u) => <span className="nowrap">{u.lastLoginAt ? formatDateTime(u.lastLoginAt) : "–"}</span>,
    },
    {
      key: "status",
      header: "Status",
      cell: (u) => (
        <>
          <StatusBadge status={u.active ? "Active" : "Inactive"} />
          {u.mustChangePassword && u.active && <div className="small muted nowrap">Must change password</div>}
        </>
      ),
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (u) => (
        <>
          <button className="btn sm" type="button" onClick={() => openEdit(u)}>
            Edit
          </button>
          <button
            className="btn sm"
            type="button"
            disabled={isSelf(u)}
            title={isSelf(u) ? "Use Change password in the top bar for your own account." : undefined}
            onClick={() => setToReset(u)}
          >
            Reset password
          </button>
          {u.active ? (
            <button
              className="btn sm danger"
              type="button"
              disabled={isSelf(u)}
              title={isSelf(u) ? "You cannot deactivate your own account." : undefined}
              onClick={() => setToDeactivate(u)}
            >
              Deactivate
            </button>
          ) : (
            <button
              className="btn sm"
              type="button"
              disabled={setActive.isPending}
              onClick={() => activate(u)}
            >
              Activate
            </button>
          )}
        </>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Users</h2>
        <button className="btn primary" type="button" onClick={() => openEdit(null)}>
          + New user
        </button>
      </div>
      <p className="small muted">
        There is no public signup. The Admin creates each user with a temporary password, which the user
        changes at first login.
      </p>
      {users.isError ? (
        <div className="banner bad">{errorMessage(users.error)}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={users.data}
          loading={users.isPending}
          rowKey={(u) => u.id}
          rowClassName={(u) => (u.active ? undefined : "row-muted")}
          empty="No users."
        />
      )}

      <UserDialog
        key={dialogKey}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        user={editing}
        isSelf={editing ? isSelf(editing) : false}
      />

      <ConfirmDialog
        open={toReset !== null}
        onOpenChange={(o) => !o && setToReset(null)}
        title={`Reset password for ${toReset?.fullName ?? ""}?`}
        confirmLabel="Reset"
        busy={reset.isPending}
        onConfirm={confirmReset}
      >
        <p>
          A new temporary password will be given. The user is logged out now and must change it at the next
          login.
        </p>
      </ConfirmDialog>

      <ConfirmDialog
        open={toDeactivate !== null}
        onOpenChange={(o) => !o && setToDeactivate(null)}
        title={`Deactivate ${toDeactivate?.fullName ?? ""}?`}
        confirmLabel="Deactivate"
        danger
        busy={setActive.isPending}
        onConfirm={confirmDeactivate}
      >
        <p>The user is logged out at once and cannot log in until activated again. Their history is kept.</p>
      </ConfirmDialog>

      <FormDialog
        open={temporary !== null}
        onOpenChange={(o) => !o && setTemporary(null)}
        title="Temporary password"
        cancelLabel="Done"
        extraButtons={
          <button
            className="btn"
            type="button"
            onClick={() => {
              if (!temporary) return;
              navigator.clipboard
                ?.writeText(temporary.password)
                .then(() => toast("Copied", "ok"))
                .catch(() => toast("Could not copy – select the text instead", "warn"));
            }}
          >
            Copy
          </button>
        }
      >
        <p>
          Give this to <b>{temporary?.name}</b>:
        </p>
        <p className="mono" style={{ fontSize: 18, userSelect: "all" }} data-testid="temporary-password">
          {temporary?.password}
        </p>
        <p className="small muted">It is shown only now. The user must change it at the next login.</p>
      </FormDialog>
    </div>
  );
}
