import type { MeResponse } from "@/lib/api/types";

/** Every page the user may open, from the server menu (hidden modules included – they are only left out of the sidebar). */
export function menuItems(me: MeResponse) {
  return me.menu.flatMap((m) => m.items.map((item) => ({ ...item, module: m })));
}

/** The menu item for a pathname (exact or a sub-path), or undefined when the user may not open it. */
export function findMenuItem(me: MeResponse, pathname: string) {
  return menuItems(me).find((i) => pathname === i.path || pathname.startsWith(i.path + "/"));
}

/**
 * Where to go after login: the page the user asked for (?next=) when they may open it, otherwise the
 * role's landing page (Admin / Accountant → Dashboard, Delivery Staff → Daily Delivery List).
 */
export function afterLoginPath(me: MeResponse, next: string | null): string {
  if (me.mustChangePassword) return "/change-password";
  if (next && next.startsWith("/") && !next.startsWith("//")) {
    const pathname = next.split(/[?#]/)[0];
    if (findMenuItem(me, pathname) || (pathname === "/ui-check" && me.permissions.includes("admin.manage"))) {
      return next;
    }
  }
  return me.landingPage;
}
