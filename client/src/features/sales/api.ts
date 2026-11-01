"use client";

/* Sales & Bills API hooks (design 7.2 Sales). */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  PaperBillCheck,
  ReverseRequest,
  SalePreview,
  SaleResult,
  TxnDetail,
  TxnList,
} from "@/lib/api/types";
import { useDebounced } from "@/lib/useDebounced";
import type { SaleKind } from "./labels";

const KEY = ["sales"] as const;

export interface BillFilters {
  q: string;
  customerId: number | null;
  kind: string;
  from: string | null;
  to: string | null;
  /** true = only bills saved on the phone, false = only office bills, null = all. */
  onPhone: boolean | null;
  page: number;
  size: number;
}

export function useBills(f: BillFilters) {
  return useQuery({
    queryKey: [...KEY, "list", f],
    queryFn: ({ signal }) => api<TxnList>("/sales/txns", { signal, query: { ...f } }),
    placeholderData: keepPreviousData,
  });
}

export function useBill(id: number | null) {
  return useQuery({
    queryKey: [...KEY, "detail", id],
    queryFn: ({ signal }) => api<TxnDetail>(`/sales/txns/${id}`, { signal }),
    enabled: id !== null,
  });
}

/** "Bill 41275 is already entered – …" / "has not been entered yet" (FR-41). */
export function usePaperBillCheck(no: string) {
  return useQuery({
    queryKey: [...KEY, "paper", no],
    queryFn: ({ signal }) => api<PaperBillCheck>(`/sales/paper-bill/${encodeURIComponent(no)}`, { signal }),
    enabled: no !== "",
  });
}

/** Path of each sale type under /sales (save = POST path, live calculation = POST path/preview). */
export const SALE_PATH: Record<SaleKind, string> = {
  first: "/sales/first-purchase",
  extra: "/sales/extra-order",
  product: "/sales/product-sale",
  collect: "/sales/collect-empties",
  damage: "/sales/customer-damage",
  owed: "/sales/settle-owed",
};

/** Live calculation of a sale (nothing is saved). `body` null = not ready (e.g. no customer yet). */
export function useSalePreview(kind: SaleKind, body: object | null) {
  const debounced = useDebounced(body);
  return useQuery({
    queryKey: [...KEY, "preview", kind, debounced],
    queryFn: ({ signal }) =>
      api<SalePreview>(SALE_PATH[kind] + "/preview", { method: "POST", body: debounced, signal }),
    enabled: debounced !== null,
    placeholderData: keepPreviousData,
    meta: { silent: true },
  });
}

/**
 * A sale or a reversal changes bills, the customer (balances, history, next delivery), stock, products and the
 * audit log – all of them are reloaded.
 */
export function invalidateAfterSale(qc: ReturnType<typeof useQueryClient>) {
  qc.invalidateQueries({ queryKey: KEY });
  qc.invalidateQueries({ queryKey: ["customers"] });
  qc.invalidateQueries({ queryKey: ["inventory"] });
  qc.invalidateQueries({ queryKey: ["masterdata", "products"] });
  qc.invalidateQueries({ queryKey: ["masterdata", "brands"] });
  qc.invalidateQueries({ queryKey: ["admin", "audit"] });
  qc.invalidateQueries({ queryKey: ["delivery"] });
}

export function useSaveSale(kind: SaleKind) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: object) => api<SaleResult>(SALE_PATH[kind], { method: "POST", body }),
    onSuccess: () => invalidateAfterSale(qc),
    meta: { silent: true },
  });
}

export function useReverseBill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: ReverseRequest }) =>
      api<SaleResult>(`/sales/txns/${id}/reverse`, { method: "POST", body }),
    onSuccess: () => invalidateAfterSale(qc),
    meta: { silent: true },
  });
}

/** The bill PDF (A5) opens in a new tab, where the browser prints it. */
export function billPdfUrl(id: number): string {
  return `/api/print/bill/${id}`;
}
