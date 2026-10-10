"use client";

/* Bottles & Products API hooks (design 7.2 Master data). */
import { useMutation, useQuery, useQueryClient, type QueryKey } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  BottleType,
  CreateBottleTypeRequest,
  CreateOldBottleBrandRequest,
  CreateProductRequest,
  OldBottleBrand,
  PriceHistoryRow,
  PriceMatrix,
  Product,
  SetPriceRequest,
  UpdateBottleTypeRequest,
  UpdateOldBottleBrandRequest,
  UpdateProductRequest,
} from "@/lib/api/types";

const KEY = ["masterdata"] as const;
const BOTTLES = [...KEY, "bottle-types"] as const;
const PRODUCTS = [...KEY, "products"] as const;
const PRICES = [...KEY, "prices"] as const;
const BRANDS = [...KEY, "brands"] as const;

export function useBottleTypes() {
  return useQuery({
    queryKey: BOTTLES,
    queryFn: ({ signal }) => api<BottleType[]>("/bottle-types", { signal }),
  });
}

export function useProducts() {
  return useQuery({ queryKey: PRODUCTS, queryFn: ({ signal }) => api<Product[]>("/products", { signal }) });
}

export function usePriceMatrix() {
  return useQuery({
    queryKey: [...PRICES, "current"],
    queryFn: ({ signal }) => api<PriceMatrix>("/prices/current", { signal }),
  });
}

export function usePriceHistory(enabled: boolean) {
  return useQuery({
    queryKey: [...PRICES, "history"],
    queryFn: ({ signal }) => api<PriceHistoryRow[]>("/prices/history", { signal }),
    enabled,
  });
}

export function useOldBottleBrands() {
  return useQuery({
    queryKey: BRANDS,
    queryFn: ({ signal }) => api<OldBottleBrand[]>("/old-bottle-brands", { signal }),
  });
}

/** A change reloads the lists it affects (and the audit log); pop-ups show errors themselves. */
function useSave<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>, invalidate: QueryKey[]) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      invalidate.forEach((queryKey) => qc.invalidateQueries({ queryKey }));
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

// Bottle types change the price grid and the brand list too (names, active rows).
export const useCreateBottleType = () =>
  useSave(
    (body: CreateBottleTypeRequest) => api<BottleType>("/bottle-types", { method: "POST", body }),
    [BOTTLES, PRICES, BRANDS],
  );

export const useUpdateBottleType = () =>
  useSave(
    ({ code, body }: { code: string; body: UpdateBottleTypeRequest }) =>
      api<BottleType>(`/bottle-types/${code}`, { method: "PUT", body }),
    [BOTTLES, PRICES, BRANDS],
  );

export const useCreateProduct = () =>
  useSave((body: CreateProductRequest) => api<Product>("/products", { method: "POST", body }), [PRODUCTS]);

export const useUpdateProduct = () =>
  useSave(
    ({ id, body }: { id: number; body: UpdateProductRequest }) =>
      api<Product>(`/products/${id}`, { method: "PUT", body }),
    [PRODUCTS],
  );

// A deposit change also shows on the Bottle types tab.
export const useSetPrice = () =>
  useSave(
    (body: SetPriceRequest) => api<PriceHistoryRow>("/prices", { method: "POST", body }),
    [PRICES, BOTTLES],
  );

export const useCreateBrand = () =>
  useSave(
    (body: CreateOldBottleBrandRequest) =>
      api<OldBottleBrand>("/old-bottle-brands", { method: "POST", body }),
    [BRANDS],
  );

export const useUpdateBrand = () =>
  useSave(
    ({ id, body }: { id: number; body: UpdateOldBottleBrandRequest }) =>
      api<OldBottleBrand>(`/old-bottle-brands/${id}`, { method: "PUT", body }),
    [BRANDS],
  );
