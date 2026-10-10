"use client";

/* Stock page API hooks (design 7.2 Inventory). */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  AdjustmentRequest,
  AdjustmentResponse,
  CompanyDamageRequest,
  DamageListRow,
  MovementPage,
  StockOverview,
} from "@/lib/api/types";

const KEY = ["inventory"] as const;

export function useStock() {
  return useQuery({
    queryKey: [...KEY, "stock"],
    queryFn: ({ signal }) => api<StockOverview>("/stock", { signal }),
  });
}

export function useDamages() {
  return useQuery({
    queryKey: [...KEY, "damages"],
    queryFn: ({ signal }) => api<DamageListRow[]>("/stock/damages", { signal }),
  });
}

export function useMovements(params: { item: string; q: string; page: number; size: number }) {
  return useQuery({
    queryKey: [...KEY, "movements", params],
    queryFn: ({ signal }) => api<MovementPage>("/stock/movements", { signal, query: params }),
    placeholderData: keepPreviousData,
  });
}

/** Every stock change reloads the whole Stock page (stock, lists, movements), products and the audit log. */
function useStockChange<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY });
      qc.invalidateQueries({ queryKey: ["masterdata", "products"] });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

export const useRecordDamage = () =>
  useStockChange((body: CompanyDamageRequest) =>
    api<DamageListRow>("/stock/damage", { method: "POST", body }),
  );

export const useAdjustStock = () =>
  useStockChange((body: AdjustmentRequest) =>
    api<AdjustmentResponse>("/stock/adjustments", { method: "POST", body }),
  );

export const useSetMinLevel = () =>
  useStockChange(({ code, minFilled }: { code: string; minFilled: number }) =>
    api<{ minFilled: number }>(`/stock/min-levels/${code}`, { method: "PUT", body: { minFilled } }),
  );
