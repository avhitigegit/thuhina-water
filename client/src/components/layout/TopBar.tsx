"use client";

/*
 * Top bar (prototype layout.js): breadcrumb, today's date and the user's name + role. Clicking the name opens a small
 * menu with Change password and Log out (UAT finding 1).
 */
import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useLogout } from "@/features/auth/api";
import { useIdleLogout } from "@/features/auth/idle";
import type { MeResponse } from "@/lib/api/types";
import { formatDayDate, todayIso } from "@/lib/format";

export function TopBar({ me, crumbs }: { me: MeResponse; crumbs: string }) {
  const logout = useLogout();
  useIdleLogout(me.user.role);
  const [open, setOpen] = useState(false);
  const box = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (e: MouseEvent) => {
      if (box.current && !box.current.contains(e.target as Node)) setOpen(false);
    };
    const esc = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", esc);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", esc);
    };
  }, [open]);

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
      <div className="user-menu" ref={box}>
        <button
          type="button"
          className="who"
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={() => setOpen((o) => !o)}
        >
          <span>
            <b>{me.user.fullName}</b>
            <span className="muted">{me.user.roleName}</span>
          </span>
          <span className="caret" aria-hidden>
            ▾
          </span>
        </button>
        {open && (
          <div className="user-menu-list" role="menu">
            <Link role="menuitem" href="/change-password" onClick={() => setOpen(false)}>
              Change password
            </Link>
            <button role="menuitem" type="button" onClick={onLogout} disabled={logout.isPending}>
              Log out
            </button>
          </div>
        )}
      </div>
    </header>
  );
}
