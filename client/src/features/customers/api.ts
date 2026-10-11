"use client";

/* Customers API hooks (design 7.2 Customers). */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  Area,
  CustomerDetail,
  CustomerHistoryRow,
  CustomerList,
  CustomerPriceRow,
  CustomerRequest,
  CustomerSaved,
  CustomerType,
  DeactivateRequest,
  EndCustomerPriceRequest,
  SetCustomerPriceRequest,
} from "@/lib/api/types";

const KEY = ["customers"] as const;

export interface CustomerFilters {
  q: string;
  area: string;
  day: string;
  type: string;
  pay: string;
  price: string;
  status: string;
  page: number;
  size: number;
}

export function useCustomerList(f: CustomerFilters) {
  return useQuery({
    queryKey: [...KEY, "list", f],
    queryFn: ({ signal }) => api<CustomerList>("/customers", { signal, query: { ...f } }),
    placeholderData: keepPreviousData,
  });
}

export function useCustomer(id: number | null) {
  return useQuery({
    queryKey: [...KEY, "detail", id],
    queryFn: ({ signal }) => api<CustomerDetail>(`/customers/${id}`, { signal }),
    enabled: id !== null,
  });
}

export function useCustomerHistory(id: number) {
  return useQuery({
    queryKey: [...KEY, "history", id],
    queryFn: ({ signal }) => api<CustomerHistoryRow[]>(`/customers/${id}/history`, { signal }),
  });
}

export function useCustomerPrices(id: number, enabled = true) {
  return useQuery({
    queryKey: [...KEY, "prices", id],
    queryFn: ({ signal }) => api<CustomerPriceRow[]>(`/customers/${id}/prices`, { signal }),
    enabled,
  });
}

export function useAllCustomerPrices(status: "current" | "all") {
  return useQuery({
    queryKey: [...KEY, "all-prices", status],
    queryFn: ({ signal }) => api<CustomerPriceRow[]>("/customer-prices", { signal, query: { status } }),
  });
}

export function useCustomerTypes() {
  return useQuery({
    queryKey: ["masterdata", "customer-types"],
    queryFn: ({ signal }) => api<CustomerType[]>("/customer-types", { signal }),
  });
}

export function useAreas() {
  return useQuery({
    queryKey: ["masterdata", "areas"],
    queryFn: ({ signal }) => api<Area[]>("/areas", { signal }),
  });
}

/** A customer change reloads every customer screen, the price lists, stock (with customers) and the audit log. */
function useCustomerChange<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY });
      qc.invalidateQueries({ queryKey: ["masterdata"] });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

export const useSaveCustomer = () =>
  useCustomerChange(({ id, body }: { id: number | null; body: CustomerRequest }) =>
    id === null
      ? api<CustomerSaved>("/customers/register", { method: "POST", body })
      : api<CustomerSaved>(`/customers/${id}`, { method: "PUT", body }),
  );

export const useDeactivateCustomer = () =>
  useCustomerChange(({ id, body }: { id: number; body: DeactivateRequest }) =>
    api<void>(`/customers/${id}/deactivate`, { method: "POST", body }),
  );

export const useReactivateCustomer = () =>
  useCustomerChange((id: number) => api<void>(`/customers/${id}/reactivate`, { method: "POST" }));

export const useSetCustomerPrice = () =>
  useCustomerChange(({ id, body }: { id: number; body: SetCustomerPriceRequest }) =>
    api<CustomerPriceRow>(`/customers/${id}/prices`, { method: "POST", body }),
  );

export const useEndCustomerPrice = () =>
  useCustomerChange(({ id, body }: { id: number; body: EndCustomerPriceRequest }) =>
    api<void>(`/customers/${id}/prices/end`, { method: "POST", body }),
  );

export const useSaveCustomerType = () =>
  useCustomerChange(
    ({ id, body }: { id: number | null; body: { name?: string; description?: string; active?: boolean } }) =>
      id === null
        ? api<CustomerType>("/customer-types", { method: "POST", body })
        : api<CustomerType>(`/customer-types/${id}`, { method: "PUT", body }),
  );

export const useSaveArea = () =>
  useCustomerChange(({ id, body }: { id: number | null; body: { name: string; active?: boolean } }) =>
    id === null
      ? api<Area>("/areas", { method: "POST", body })
      : api<Area>(`/areas/${id}`, { method: "PUT", body }),
  );
