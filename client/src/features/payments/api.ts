"use client";

/* Payments page API: Money in and Money out lists (GET /money/in, /money/out). Saving stays with each payment's own API. */
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { MoneyList } from "@/lib/api/types";

export type Direction = "in" | "out";

export interface MoneyFilters {
  from: string | null;
  to: string | null;
  q: string;
}

export function useMoney(direction: Direction, f: MoneyFilters) {
  return useQuery({
    queryKey: ["money", direction, f],
    queryFn: ({ signal }) =>
      api<MoneyList>(`/money/${direction}`, {
        signal,
        query: { from: f.from ?? undefined, to: f.to ?? undefined, q: f.q.trim() || undefined },
      }),
    placeholderData: keepPreviousData,
  });
}
