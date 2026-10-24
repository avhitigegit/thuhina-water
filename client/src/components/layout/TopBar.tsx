"use client";

/* Top bar (prototype layout.js): breadcrumb, today's date, user name + role, Change password, Log out. */
import Link from "next/link";
import { useLogout } from "@/features/auth/api";
import type { MeResponse } from "@/lib/api/types";
import { formatDayDate, todayIso } from "@/lib/format";

export function TopBar({ me, crumbs }: { me: MeResponse; crumbs: string }) {
  const logout = useLogout();

  function onLogout() {
    // A full page load clears every cached screen of this user (intended, not a client-side navigation).
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    logout.mutate(undefined, { onSettled: () => window.location.assign("/login") });
  }

  return (
    <header className="topbar no-print">
      <span className="crumbs">{crumbs}</span>
      <span className="spacer" />
      <span className="biz-date" suppressHydrationWarning>
        Today: {formatDayDate(todayIso())}
      </span>
      <div className="who">
        <b>{me.user.fullName}</b>
        <span className="muted">{me.user.roleName}</span>
      </div>
      <Link className="btn sm" href="/change-password">
        Change password
      </Link>
      <button className="btn sm" type="button" onClick={onLogout} disabled={logout.isPending}>
        Log out
      </button>
    </header>
  );
}
