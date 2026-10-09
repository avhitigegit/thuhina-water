"use client";

/*
 * Sidebar built from the /auth/me menu (prototype layout.js): a single link when the module has one page,
 * a heading with sub-items for Master Data, Sales & Deliveries and Delivery Schedule, a "view" tag on
 * view-only pages, hidden modules (e.g. Data Migration) left out. Collapsed groups are remembered.
 */
import Link from "next/link";
import { useEffect, useState } from "react";
import type { MenuModule } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { Icon } from "./icons";

const COLLAPSED_KEY = "thuhina.nav.collapsed";

function readCollapsed(): Record<string, boolean> {
  try {
    return JSON.parse(window.localStorage.getItem(COLLAPSED_KEY) ?? "{}");
  } catch {
    return {};
  }
}

function isActive(pathname: string, path: string) {
  return pathname === path || pathname.startsWith(path + "/");
}

export function Sidebar({ menu, pathname }: { menu: MenuModule[]; pathname: string }) {
  const [collapsed, setCollapsed] = useState<Record<string, boolean>>({});

  useEffect(() => {
    // localStorage is only available in the browser, after the first render.
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setCollapsed(readCollapsed());
  }, []);

  function toggle(module: string) {
    setCollapsed((prev) => {
      const next = { ...prev, [module]: !prev[module] };
      try {
        window.localStorage.setItem(COLLAPSED_KEY, JSON.stringify(next));
      } catch {
        /* storage blocked – the sidebar still works */
      }
      return next;
    });
  }

  const viewTag = <span className="view-tag">view</span>;

  return (
    <aside className="sidebar no-print">
      <div className="brandbar">
        <div className="logo">T</div>
        <div>
          <b>Thuhina Water</b>
          <span>Inventory &amp; Sales</span>
        </div>
      </div>
      <nav aria-label="Main menu">
        {menu
          .filter((m) => !m.hidden && m.items.length > 0)
          .map((m) => {
            if (!m.group) {
              const item = m.items[0];
              return (
                <Link
                  key={m.module}
                  href={item.path}
                  className={cn("nav-single", isActive(pathname, item.path) && "active")}
                >
                  <Icon name={m.icon} />
                  <span>{m.module}</span>
                  {item.view && viewTag}
                </Link>
              );
            }
            const current = m.items.some((i) => isActive(pathname, i.path));
            return (
              <div key={m.module} className={cn("nav-group", collapsed[m.module] && !current && "collapsed")}>
                <button className="nav-head" type="button" onClick={() => toggle(m.module)}>
                  <Icon name={m.icon} />
                  <span>{m.module}</span>
                  <Icon name="chev" className="chev" />
                </button>
                <ul>
                  {m.items.map((i) => (
                    <li key={i.key}>
                      <Link href={i.path} className={cn(isActive(pathname, i.path) && "active")}>
                        {i.title}
                        {i.view && viewTag}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            );
          })}
      </nav>
    </aside>
  );
}
