"use client";

/*
 * Tabs that remember the open tab in the URL hash (prototype UI.tabs), so a reload or a link
 * (e.g. /billing#invoices) opens the same tab.
 *   const [tab, setTab] = useHashTab(["accounts", "payments"]);
 *   <Tabs tabs={[{ id: "accounts", label: "Accounts" }, …]} value={tab} onChange={setTab} />
 * A tab can show a count after its label ({ count: 2, countKind: "bad" } = a red 2).
 */
import { useCallback, useEffect, useState } from "react";

export interface TabItem {
  id: string;
  label: string;
  count?: number;
  countKind?: "bad";
}

export function useHashTab<T extends string>(ids: readonly T[]): [T, (id: T) => void] {
  const [current, setCurrent] = useState<T>(ids[0]);
  const key = ids.join("|");

  useEffect(() => {
    const read = () => {
      const want = window.location.hash.slice(1) as T;
      if (ids.includes(want)) setCurrent(want);
    };
    read();
    window.addEventListener("hashchange", read);
    return () => window.removeEventListener("hashchange", read);
    // ids are compared by value through `key`
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key]);

  const select = useCallback((id: T) => {
    setCurrent(id);
    try {
      window.history.replaceState(window.history.state, "", "#" + id);
    } catch {
      /* ignore */
    }
  }, []);

  return [current, select];
}

export function Tabs<T extends string>({
  tabs,
  value,
  onChange,
}: {
  tabs: (TabItem & { id: T })[];
  value: T;
  onChange: (id: T) => void;
}) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button
          key={t.id}
          type="button"
          role="tab"
          aria-selected={t.id === value}
          className={t.id === value ? "active" : undefined}
          onClick={() => onChange(t.id)}
        >
          {t.label}
          {t.count !== undefined && (
            <span className={t.countKind === "bad" && t.count > 0 ? "tab-count bad" : "tab-count"}>
              {t.count}
            </span>
          )}
        </button>
      ))}
    </div>
  );
}
