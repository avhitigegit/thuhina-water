/*
 * Toast messages, as in the prototype (bottom right; errors stay 6 s, others 3.5 s).
 * Call toast("Saved", "ok") from anywhere; <Toaster/> in the root layout shows them.
 */
export type ToastKind = "ok" | "bad" | "warn" | "info";
export interface ToastItem {
  id: number;
  message: string;
  kind: ToastKind;
}

let items: ToastItem[] = [];
let nextId = 1;
const listeners = new Set<() => void>();
const emit = () => listeners.forEach((l) => l());

export function toast(message: string, kind: ToastKind = "info"): void {
  const id = nextId++;
  items = [...items, { id, message, kind }];
  emit();
  setTimeout(() => dismissToast(id), kind === "bad" ? 6000 : 3500);
}

export function dismissToast(id: number): void {
  items = items.filter((t) => t.id !== id);
  emit();
}

export function subscribeToasts(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getToasts(): ToastItem[] {
  return items;
}

const EMPTY: ToastItem[] = [];
export function getServerToasts(): ToastItem[] {
  return EMPTY;
}
