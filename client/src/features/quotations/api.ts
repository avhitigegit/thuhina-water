"use client";

/* Customer quotations API (design 7.2 Customer quotations): list, items with today's prices, view, save, steps, prices. */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  CqItemOption,
  CqLineRequest,
  CqListResponse,
  CqRequest,
  CqSaved,
  CqView,
  WhatsAppSendStatus,
} from "@/lib/api/types";

const KEY = ["quotations"] as const;

export function useQuotations(status: string, q: string) {
  return useQuery({
    queryKey: [...KEY, "list", status, q],
    queryFn: ({ signal }) => api<CqListResponse>("/customer-quotations", { signal, query: { status, q } }),
    placeholderData: keepPreviousData,
  });
}

export function useQuotation(id: number | null) {
  return useQuery({
    queryKey: [...KEY, "view", id],
    queryFn: ({ signal }) => api<CqView>(`/customer-quotations/${id}`, { signal }),
    enabled: id !== null,
  });
}

/** What can be quoted with the price on the date: the customer's price now, or the standard price of the type. */
export function useQuoteItems(customerTypeId: number | null, customerId: number | null, date: string | null) {
  return useQuery({
    queryKey: [...KEY, "items", customerTypeId, customerId, date],
    queryFn: ({ signal }) =>
      api<CqItemOption[]>("/customer-quotations/items", {
        signal,
        query: { customerTypeId, customerId, date },
      }),
    enabled: customerTypeId !== null,
    placeholderData: keepPreviousData,
  });
}

/** Every change refreshes the quotations, the customers (prices, registration) and the audit log. */
function useChange<TVars, TResult = CqSaved>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY });
      qc.invalidateQueries({ queryKey: ["customers"] });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

/** What the pop-up sends: any field may be missing (the server answers with the prototype's message). */
export type QuoteBody = Omit<Partial<CqRequest>, "lines"> & { lines: Partial<CqLineRequest>[] };

export const useSaveQuotation = () =>
  useChange(({ id, body }: { id: number | null; body: QuoteBody }) =>
    id === null
      ? api<CqSaved>("/customer-quotations", { method: "POST", body })
      : api<CqSaved>(`/customer-quotations/${id}`, { method: "PUT", body }),
  );

export type Step = "send" | "accept" | "reject" | "apply-prices";

export const useQuotationStep = () =>
  useChange(({ id, step, body }: { id: number; step: Step; body: object }) =>
    api<CqSaved>(`/customer-quotations/${id}/${step}`, { method: "POST", body }),
  );

/** WhatsApp of the quotation: last message and the number it would go to. */
export function useQuotationWhatsApp(id: number) {
  return useQuery({
    queryKey: [...KEY, "whatsapp", id],
    queryFn: ({ signal }) => api<WhatsAppSendStatus>(`/customer-quotations/${id}/whatsapp`, { signal }),
  });
}

/** Sends the quotation PDF on WhatsApp (a draft becomes Sent by WhatsApp). */
export const useSendQuotationWhatsApp = () =>
  useChange(({ id, to, agreed }: { id: number; to: string; agreed: boolean }) =>
    api<WhatsAppSendStatus>(`/customer-quotations/${id}/whatsapp`, { method: "POST", body: { to, agreed } }),
  );

/** Quotation PDF (A4) – opens in a new tab to print. */
export function quotationPdfUrl(id: number): string {
  return `/api/print/customer-quotation/${id}`;
}
