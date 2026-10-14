"use client";

/* Filling Factory API (design 7.2 Production): KPIs, batches, dispatches, returns, factory balances and payments. */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  BatchView,
  DispatchRequest,
  DispatchView,
  FactoryMonth,
  FactoryPaymentRequest,
  FactoryPaymentRow,
  ProductionSummary,
  ReturnRequest,
  ReturnResult,
} from "@/lib/api/types";

const KEY = ["production"] as const;

export function useProductionSummary() {
  return useQuery({
    queryKey: [...KEY, "summary"],
    queryFn: ({ signal }) => api<ProductionSummary>("/production/summary", { signal }),
  });
}

export function useBatches() {
  return useQuery({
    queryKey: [...KEY, "batches"],
    queryFn: ({ signal }) => api<BatchView[]>("/factory-batches", { signal }),
  });
}

export function useFactoryBalances() {
  return useQuery({
    queryKey: [...KEY, "balances"],
    queryFn: ({ signal }) => api<FactoryMonth[]>("/factory-balances", { signal }),
  });
}

export function useFactoryPayments() {
  return useQuery({
    queryKey: [...KEY, "payments"],
    queryFn: ({ signal }) => api<FactoryPaymentRow[]>("/factory-payments", { signal }),
  });
}

/** Every change refreshes the factory page, the Factories tab (at factory, owed), stock and the audit log. */
function useChange<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY });
      qc.invalidateQueries({ queryKey: ["partners", "factories"] });
      qc.invalidateQueries({ queryKey: ["inventory"] });
      qc.invalidateQueries({ queryKey: ["delivery"] });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

export const useSendEmpties = () =>
  useChange((body: DispatchRequest) => api<DispatchView>("/factory-dispatches", { method: "POST", body }));

export const useReceiveFilled = () =>
  useChange(({ batchId, body }: { batchId: number; body: ReturnRequest }) =>
    api<ReturnResult>(`/factory-batches/${batchId}/returns`, { method: "POST", body }),
  );

export const usePayFactory = () =>
  useChange((body: FactoryPaymentRequest) =>
    api<FactoryPaymentRow>("/factory-payments", { method: "POST", body }),
  );

/** Dispatch note PDF (A4) – opens in a new tab to print. */
export function dispatchNoteUrl(dispatchNo: string): string {
  return `/api/print/dispatch-note/${encodeURIComponent(dispatchNo)}`;
}
