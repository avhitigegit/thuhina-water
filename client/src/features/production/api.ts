"use client";

/* Filling Factory API (design 7.2 Production): KPIs, batches, dispatches, returns, factory balances and payments. */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  BatchView,
  DispatchRequest,
  DispatchView,
  FactoryBill,
  FactoryMonth,
  FactoryPaymentRequest,
  FactoryPaymentRow,
  ProductionSummary,
  FactoryReceiveRequest,
  FactoryReceiveResult,
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

/** Filling bills (one per factory return): Bill to Bill payments pay these. {@code open}: only bills with something due. */
export function useFactoryBills(factoryId: number | null = null, open = false) {
  return useQuery({
    queryKey: [...KEY, "bills", factoryId, open],
    queryFn: ({ signal }) =>
      api<FactoryBill[]>("/factory-bills", {
        signal,
        query: { ...(factoryId !== null ? { factory: factoryId } : {}), open },
      }),
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
      qc.invalidateQueries({ queryKey: ["money"] });
      qc.invalidateQueries({ queryKey: ["inventory"] });
      qc.invalidateQueries({ queryKey: ["delivery"] });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

export const useSendEmpties = () =>
  useChange((body: DispatchRequest) => api<DispatchView>("/factory-dispatches", { method: "POST", body }));

/** One load back from a factory: the server splits it over the open batches of that bottle type, oldest first. */
export const useReceiveFilled = () =>
  useChange((body: FactoryReceiveRequest) =>
    api<FactoryReceiveResult>("/factory-returns", { method: "POST", body }),
  );

/** How a load is split over the open batches (oldest first): filled first, then rejected (as the server does). */
export function allocate(stillAtFactory: number[], filled: number, rejected: number): [number, number][] {
  let f = filled;
  let r = rejected;
  return stillAtFactory.map((left) => {
    const takeF = Math.min(f, left);
    const takeR = Math.min(r, left - takeF);
    f -= takeF;
    r -= takeR;
    return [takeF, takeR];
  });
}

export const usePayFactory = () =>
  useChange((body: FactoryPaymentRequest) =>
    api<FactoryPaymentRow>("/factory-payments", { method: "POST", body }),
  );

/** Dispatch note PDF (A4) – opens in a new tab to print. */
export function dispatchNoteUrl(dispatchNo: string): string {
  return `/api/print/dispatch-note/${encodeURIComponent(dispatchNo)}`;
}
