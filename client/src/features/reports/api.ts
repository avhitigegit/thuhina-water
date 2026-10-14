"use client";

/* Dashboard & reports API (design 7.2 Reports) and the demo-data generator (local / UAT). */
import { useMutation, useQuery, useQueryClient, keepPreviousData } from "@tanstack/react-query";
import { api, buildUrl } from "@/lib/api/client";
import type { DashboardView, DemoDataResult, DemoDataStatus, ReportInfo, ReportView } from "@/lib/api/types";

export function useDashboard() {
  return useQuery({
    queryKey: ["dashboard"],
    queryFn: ({ signal }) => api<DashboardView>("/dashboard", { signal }),
  });
}

export function useReportList() {
  return useQuery({
    queryKey: ["reports", "list"],
    queryFn: ({ signal }) => api<ReportInfo[]>("/reports", { signal }),
  });
}

export function useReport(key: string | null, from: string, to: string) {
  return useQuery({
    queryKey: ["reports", "view", key, from, to],
    queryFn: ({ signal }) => api<ReportView>(`/reports/${key}`, { signal, query: { from, to } }),
    enabled: key !== null,
    placeholderData: keepPreviousData,
  });
}

/** PDF opens in a new tab; Excel downloads. */
export function exportUrl(key: string, format: "pdf" | "xlsx", from: string, to: string): string {
  return buildUrl(`/reports/${key}/export`, { format, from, to });
}

/** The generator exists only on local / UAT: elsewhere the call answers 404 and the card stays hidden. */
export function useDemoDataStatus(enabled: boolean) {
  return useQuery({
    queryKey: ["demo-data"],
    queryFn: ({ signal }) => api<DemoDataStatus>("/admin/demo-data", { signal }),
    enabled,
    retry: false,
    meta: { silent: true },
  });
}

export function useGenerateDemoData() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: { from: string; to: string }) =>
      api<DemoDataResult>("/admin/demo-data", { method: "POST", body }),
    onSuccess: () => qc.invalidateQueries(),
    meta: { silent: true },
  });
}
