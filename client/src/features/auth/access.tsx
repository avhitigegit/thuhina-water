"use client";

/*
 * Who is logged in and what they may do, for every screen inside the app shell.
 *   useCurrentUser()      → the /auth/me data
 *   useCan("customers.edit") → true / false
 *   <Can permission="customers.edit"> <button>…</button> </Can>  – hides write buttons for view-only roles
 *   usePageAccess()       → { viewOnly } for the open page (from the menu "view" flag)
 * Hiding is only for a clean screen; the server refuses every call the role may not make.
 */
import { createContext, useContext, type ReactNode } from "react";
import type { MeResponse } from "@/lib/api/types";
import type { Permission } from "@/lib/permissions";

interface AccessValue {
  me: MeResponse;
  viewOnly: boolean;
}

const AccessContext = createContext<AccessValue | null>(null);

export function AccessProvider({ value, children }: { value: AccessValue; children: ReactNode }) {
  return <AccessContext.Provider value={value}>{children}</AccessContext.Provider>;
}

function useAccess(): AccessValue {
  const value = useContext(AccessContext);
  if (!value) throw new Error("useAccess must be used inside the app shell");
  return value;
}

export function useCurrentUser(): MeResponse {
  return useAccess().me;
}

export function useCan(permission: Permission): boolean {
  return useAccess().me.permissions.includes(permission);
}

export function usePageAccess(): { viewOnly: boolean } {
  return { viewOnly: useAccess().viewOnly };
}

/** Shows its children only when the user has the permission (otherwise `fallback`, default nothing). */
export function Can({
  permission,
  children,
  fallback = null,
}: {
  permission: Permission;
  children: ReactNode;
  fallback?: ReactNode;
}) {
  return <>{useCan(permission) ? children : fallback}</>;
}
