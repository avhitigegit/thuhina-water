"use client";

/*
 * My Deliveries API (M17): today's customers come from /delivery/due; here: find another customer, the live bill
 * calculation, save today's bill or money without a delivery, and the delivery person's day.
 */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { invalidateAfterSale } from "@/features/sales/api";
import { api } from "@/lib/api/client";
import type {
  BillRequest,
  DueCustomer,
  MobilePaymentRequest,
  MyDay,
  PaymentSaved,
  SalePreview,
  SaleResult,
} from "@/lib/api/types";
import { useDebounced } from "@/lib/useDebounced";

const KEY = ["delivery", "mobile"] as const;

/** Active customers matching the text (at least 2 characters), for a stop that is not on today's list. */
export function useFindCustomers(q: string) {
  const text = useDebounced(q.trim());
  return useQuery({
    queryKey: [...KEY, "customers", text],
    queryFn: ({ signal }) => api<DueCustomer[]>("/delivery/mobile/customers", { signal, query: { q: text } }),
    enabled: text.length >= 2,
    placeholderData: keepPreviousData,
  });
}

/** Live calculation of today's bill (nothing is saved). `body` null = nothing to calculate yet. */
export function useMobileBillPreview(body: BillRequest | null) {
  // Debounced as text: the screen builds a new body object on every render.
  const debounced = useDebounced(body === null ? null : JSON.stringify(body));
  return useQuery({
    queryKey: [...KEY, "preview", debounced],
    queryFn: ({ signal }) =>
      api<SalePreview>("/delivery/mobile/bills/preview", {
        method: "POST",
        body: JSON.parse(debounced!),
        signal,
      }),
    enabled: debounced !== null,
    placeholderData: keepPreviousData,
    meta: { silent: true },
  });
}

export function useSaveMobileBill() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: BillRequest) => api<SaleResult>("/delivery/mobile/bills", { method: "POST", body }),
    onSuccess: () => invalidateAfterSale(qc),
    meta: { silent: true },
  });
}

export function useSaveMobilePayment() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: MobilePaymentRequest) =>
      api<PaymentSaved>("/delivery/mobile/payments", { method: "POST", body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["delivery"] });
      qc.invalidateQueries({ queryKey: ["billing"] });
      qc.invalidateQueries({ queryKey: ["customers"] });
    },
    meta: { silent: true },
  });
}

export function useMyDay() {
  return useQuery({
    queryKey: [...KEY, "day"],
    queryFn: ({ signal }) => api<MyDay>("/delivery/mobile/day", { signal }),
  });
}
