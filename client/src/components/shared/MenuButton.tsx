"use client";

/*
 * Button with a drop-down list of actions (prototype UI.menuButton, e.g. "+ New ▾" on Sales & Bills).
 * Each item has a label and an optional grey hint line. Arrow keys, Enter and Escape work (Radix menu).
 */
import { DropdownMenu } from "radix-ui";
import type { ReactNode } from "react";

export interface MenuItem {
  label: string;
  hint?: string;
  onSelect: () => void;
}

export function MenuButton({
  children,
  items,
  className = "btn primary",
}: {
  children: ReactNode;
  items: MenuItem[];
  className?: string;
}) {
  return (
    <DropdownMenu.Root modal={false}>
      <DropdownMenu.Trigger asChild>
        <button type="button" className={className}>
          {children}
        </button>
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content className="menu" align="end" sideOffset={4}>
          {items.map((it) => (
            <DropdownMenu.Item key={it.label} onSelect={it.onSelect}>
              {it.label}
              {it.hint && <small>{it.hint}</small>}
            </DropdownMenu.Item>
          ))}
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  );
}
