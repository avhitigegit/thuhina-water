"use client";

/* WhatsApp tab API hooks (M13, design 9.2): settings, test send, connection check, reminders, message log. Admin only. */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  WhatsAppCheckResult,
  WhatsAppMessagePage,
  WhatsAppRunResult,
  WhatsAppSettings,
  WhatsAppSettingsRequest,
} from "@/lib/api/types";

const SETTINGS_KEY = ["admin", "whatsapp", "settings"] as const;
const MESSAGES_KEY = ["admin", "whatsapp", "messages"] as const;

export function useWhatsAppSettings() {
  return useQuery({
    queryKey: SETTINGS_KEY,
    queryFn: ({ signal }) => api<WhatsAppSettings>("/whatsapp/settings", { signal }),
  });
}

export function useSaveWhatsAppSettings() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: WhatsAppSettingsRequest) =>
      api<WhatsAppSettings>("/whatsapp/settings", { method: "PUT", body }),
    onSuccess: (s) => qc.setQueryData(SETTINGS_KEY, s),
    // The tab shows field errors itself.
    meta: { silent: true },
  });
}

/** Actions that add messages to the log: reload it afterwards. */
function useLogMutation<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => qc.invalidateQueries({ queryKey: MESSAGES_KEY }),
    meta: { silent: true },
  });
}

export function useWhatsAppTest() {
  return useLogMutation((to: string) =>
    api<WhatsAppCheckResult>("/whatsapp/test", { method: "POST", body: { to } }),
  );
}

export function useWhatsAppVerify() {
  return useMutation({
    mutationFn: () => api<WhatsAppCheckResult>("/whatsapp/verify", { method: "POST" }),
    meta: { silent: true },
  });
}

export function useRunReminders() {
  return useLogMutation(() => api<WhatsAppRunResult>("/whatsapp/reminders/run", { method: "POST" }));
}

export function useSendQueuedNow() {
  return useLogMutation(() => api<WhatsAppRunResult>("/whatsapp/send-now", { method: "POST" }));
}

export interface MessageQuery {
  type: string;
  status: string;
  from: string | null;
  to: string | null;
  page: number;
  size: number;
}

export function useWhatsAppMessages(q: MessageQuery) {
  return useQuery({
    queryKey: [...MESSAGES_KEY, q],
    queryFn: ({ signal }) => api<WhatsAppMessagePage>("/whatsapp/messages", { query: { ...q }, signal }),
    placeholderData: keepPreviousData,
  });
}
