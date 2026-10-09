"use client";

/*
 * Protected app shell (design 3.3): loads /auth/me, sends the user to the change-password screen when needed,
 * draws the sidebar and top bar, the page heading, the view-only banner, and "No access" when the role may
 * not open the page (the server also returns 403 for its API calls).
 */
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import { AccessProvider } from "@/features/auth/access";
import { useMe } from "@/features/auth/api";
import { findMenuItem } from "@/features/auth/landing";
import { isRedirectError } from "@/lib/api/client";
import { P } from "@/lib/permissions";
import { findRoute } from "@/lib/routes";
import { NoAccess } from "@/components/shared/NoAccess";
import { PageActionsSlotContext } from "./PageActions";
import { Sidebar } from "./Sidebar";
import { TopBar } from "./TopBar";

/** Pages inside the shell that are not in the menu: the shared-components check page (Admin only). */
const EXTRA_PAGES: Record<string, { title: string; description: string; permission: string }> = {
  "/ui-check": {
    title: "UI components check",
    description: "Shared components used by every screen – for checking during development and UAT",
    permission: P.ADMIN_MANAGE,
  },
};

function Loading() {
  return <div className="loading-page">Loading…</div>;
}

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const me = useMe();
  const [actionsSlot, setActionsSlot] = useState<HTMLElement | null>(null);

  const mustChange = me.data?.mustChangePassword ?? false;
  useEffect(() => {
    if (mustChange) router.replace("/change-password");
  }, [mustChange, router]);

  if (me.isPending || mustChange || (me.isError && isRedirectError(me.error))) return <Loading />;
  if (me.isError || !me.data) {
    return (
      <div className="login-page">
        <div className="login-card">
          <h2>Cannot load the app</h2>
          <div className="banner bad">{me.error?.message ?? "Something went wrong."}</div>
          <button className="btn primary" type="button" onClick={() => me.refetch()}>
            Try again
          </button>
        </div>
      </div>
    );
  }

  const user = me.data;
  const item = findMenuItem(user, pathname);
  const route = findRoute(pathname);
  const extra = EXTRA_PAGES[pathname];
  const allowed = item ? true : extra ? user.permissions.includes(extra.permission) : false;
  const title = item?.title ?? extra?.title ?? route?.title ?? "Page";
  const description = route?.description ?? extra?.description ?? "";
  const viewOnly = item?.view ?? false;
  const crumbs = item
    ? item.module.group
      ? `${item.module.module} › ${item.title}`
      : item.module.module
    : (extra?.title ?? "");

  return (
    <AccessProvider value={{ me: user, viewOnly }}>
      <title>{`${title} · Thuhina Water`}</title>
      <div className="shell">
        <Sidebar menu={user.menu} pathname={pathname} />
        <div className="main">
          <TopBar me={user} crumbs={allowed ? crumbs : ""} />
          <main className="content">
            {allowed ? (
              <>
                <div className="page-head">
                  <div>
                    <h1>{title}</h1>
                    {description && <p className="sub">{description}</p>}
                  </div>
                  <div ref={setActionsSlot} />
                </div>
                {viewOnly && (
                  <div className="banner view">
                    View-only access for the {user.user.roleName} role. Entry and edit actions are hidden.
                  </div>
                )}
                <PageActionsSlotContext.Provider value={actionsSlot}>
                  {children}
                </PageActionsSlotContext.Provider>
              </>
            ) : (
              <NoAccess roleName={user.user.roleName} pageTitle={title} home={user.landingPage} />
            )}
          </main>
        </div>
      </div>
    </AccessProvider>
  );
}
