"use client";

/*
 * Buttons for the page heading (top right, like the prototype's .page-actions).
 * A page writes <PageActions><button className="btn primary">+ New</button></PageActions>
 * and the shell shows them next to the title.
 */
import { createContext, useContext, type ReactNode } from "react";
import { createPortal } from "react-dom";

export const PageActionsSlotContext = createContext<HTMLElement | null>(null);

export function PageActions({ children }: { children: ReactNode }) {
  const slot = useContext(PageActionsSlotContext);
  return slot ? createPortal(<div className="page-actions">{children}</div>, slot) : null;
}
