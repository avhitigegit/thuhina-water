"use client";

/* Links that open a pop-up (?pay=C0012, ?statement=C0012 …): the customer they mean, and clearing them after use. */
import { findMatch } from "@/features/customers/CustomerPicker";
import { api } from "@/lib/api/client";
import type { CustomerLookupRow } from "@/lib/api/types";

/** The customer a link means (?pay=C0012): the code, phone or name lookup's match. */
export async function lookup(text: string): Promise<CustomerLookupRow | null> {
  const rows = await api<CustomerLookupRow[]>("/customers/lookup", { query: { q: text } }).catch(() => []);
  return findMatch(text, rows) ?? rows.find((r) => r.code === text.toUpperCase()) ?? null;
}

/** Removes the link's ?pay= / ?statement= / ?invoice= so the same link works again later. */
export function clearLinkParams() {
  try {
    window.history.replaceState(window.history.state, "", window.location.pathname + window.location.hash);
  } catch {
    /* ignore */
  }
}
