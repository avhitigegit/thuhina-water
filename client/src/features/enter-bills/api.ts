"use client";

/* Enter Bills API (design 7.2): the due list, the suggested paper bill number and posting one bill per row. */
import { useQuery, type QueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { CustomerDetail, DueList, NextPaperBill, SaleResult } from "@/lib/api/types";

/** Customers due on a date (default today) – read once per date change (rows typed by hand are kept). */
export function fetchDueList(qc: QueryClient, date: string): Promise<DueList> {
  return qc.fetchQuery({
    queryKey: ["sales", "due", date],
    queryFn: ({ signal }) => api<DueList>("/delivery/due", { signal, query: { date } }),
    staleTime: 0,
  });
}

/** A customer typed into a row: bottles, usual quantities, prices and balance. */
export function fetchCustomer(qc: QueryClient, id: number): Promise<CustomerDetail> {
  return qc.fetchQuery({
    queryKey: ["customers", "detail", id],
    queryFn: ({ signal }) => api<CustomerDetail>(`/customers/${id}`, { signal }),
    staleTime: 0,
  });
}

/** Highest numeric paper bill number entered + 1 (null before the first numeric paper bill). */
export function useNextPaperBill() {
  return useQuery({
    queryKey: ["sales", "paper-next"],
    queryFn: ({ signal }) => api<NextPaperBill>("/sales/paper-bill/next", { signal }),
  });
}

export function postBill(body: object): Promise<SaleResult> {
  return api<SaleResult>("/sales/bills", { method: "POST", body });
}
