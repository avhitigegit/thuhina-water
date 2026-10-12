"use client";

/* Delivery schedule API (design 7.2 Delivery, Print): the daily list, planning, the bottle requirement and the sheets. */
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { DueList, Planning, Requirement } from "@/lib/api/types";

const KEY = ["delivery"] as const;

/** Customers due on a date (for today also the missed ones) – the Daily Delivery List. */
export function useDueList(date: string, area: string) {
  return useQuery({
    queryKey: [...KEY, "due", date, area],
    queryFn: ({ signal }) =>
      api<DueList>("/delivery/due", { signal, query: { date, area: area || undefined } }),
    placeholderData: keepPreviousData,
  });
}

/** Delivery Planning for a date: KPIs, ready check and customers by route. */
export function usePlanning(date: string, area: string, show: "due" | "all") {
  return useQuery({
    queryKey: [...KEY, "planning", date, area, show],
    queryFn: ({ signal }) =>
      api<Planning>("/delivery/planning", { signal, query: { date, area: area || undefined, show } }),
    placeholderData: keepPreviousData,
  });
}

/** Filled bottles of one type needed for the next 14 days (Admin). */
export function useRequirement(bottle: string, enabled: boolean) {
  return useQuery({
    queryKey: [...KEY, "requirement", bottle],
    queryFn: ({ signal }) =>
      api<Requirement>("/delivery/requirement", { signal, query: { days: 14, bottle: bottle || undefined } }),
    enabled,
  });
}

function sheetUrl(path: string, date: string, area: string): string {
  const q = new URLSearchParams({ date });
  if (area) q.set("area", area);
  return `/api/print/${path}?${q}`;
}

/** Delivery sheet PDF (A4 landscape) – opens in a new tab to print. */
export function deliverySheetUrl(date: string, area: string): string {
  return sheetUrl("delivery-sheet", date, area);
}

/** Load sheet PDF (A4 landscape) – route totals and the customers to deliver. */
export function loadSheetUrl(date: string, area: string): string {
  return sheetUrl("load-sheet", date, area);
}
