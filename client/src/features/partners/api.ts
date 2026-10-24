"use client";

/* Suppliers & factories API hooks (design 7.2 Partners). */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { Factory, FactoryRequest, Supplier, SupplierRequest } from "@/lib/api/types";

const SUPPLIERS = ["partners", "suppliers"] as const;
const FACTORIES = ["partners", "factories"] as const;

export function useSuppliers() {
  return useQuery({
    queryKey: SUPPLIERS,
    queryFn: ({ signal }) => api<Supplier[]>("/suppliers", { signal }),
  });
}

export function useFactories() {
  return useQuery({ queryKey: FACTORIES, queryFn: ({ signal }) => api<Factory[]>("/factories", { signal }) });
}

function useSave<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>, key: readonly string[]) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: key });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

export const useSaveSupplier = () =>
  useSave(
    ({ id, body }: { id: number | null; body: SupplierRequest }) =>
      id === null
        ? api<Supplier>("/suppliers", { method: "POST", body })
        : api<Supplier>(`/suppliers/${id}`, { method: "PUT", body }),
    SUPPLIERS,
  );

export const useSaveFactory = () =>
  useSave(
    ({ id, body }: { id: number | null; body: FactoryRequest }) =>
      id === null
        ? api<Factory>("/factories", { method: "POST", body })
        : api<Factory>(`/factories/${id}`, { method: "PUT", body }),
    FACTORIES,
  );
