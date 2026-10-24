"use client";

/* Purchasing API (design 7.2 Purchasing): quotation requests, quotations, purchase orders, receipts, supplier payments. */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  AttachmentResponse,
  PaymentRequest,
  PaymentRow,
  PoDetail,
  PoRow,
  PoUpdateRequest,
  QrDetail,
  QrRequest,
  QrRow,
  QuotationRequestBody,
  ReceiptRequest,
  ReceiptResult,
  SupplierBalance,
} from "@/lib/api/types";

const KEY = ["purchasing"] as const;

export function useQuotationRequests() {
  return useQuery({
    queryKey: [...KEY, "requests"],
    queryFn: ({ signal }) => api<QrRow[]>("/quotation-requests", { signal }),
  });
}

export function useQuotationRequest(id: number | null) {
  return useQuery({
    queryKey: [...KEY, "request", id],
    queryFn: ({ signal }) => api<QrDetail>(`/quotation-requests/${id}`, { signal }),
    enabled: id !== null,
  });
}

export function usePurchaseOrders(status: string) {
  return useQuery({
    queryKey: [...KEY, "orders", status],
    queryFn: ({ signal }) =>
      api<PoRow[]>("/purchase-orders", { signal, query: { status: status || undefined } }),
  });
}

export function usePurchaseOrder(id: number | null) {
  return useQuery({
    queryKey: [...KEY, "order", id],
    queryFn: ({ signal }) => api<PoDetail>(`/purchase-orders/${id}`, { signal }),
    enabled: id !== null,
  });
}

export function useSupplierBalances() {
  return useQuery({
    queryKey: [...KEY, "balances"],
    queryFn: ({ signal }) => api<SupplierBalance[]>("/supplier-balances", { signal }),
  });
}

export function useSupplierPayments() {
  return useQuery({
    queryKey: [...KEY, "payments"],
    queryFn: ({ signal }) => api<PaymentRow[]>("/supplier-payments", { signal }),
  });
}

/** Every purchasing change refreshes purchasing, the suppliers' "Owed", stock (receipts) and the audit log. */
function useChange<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY });
      qc.invalidateQueries({ queryKey: ["partners", "suppliers"] });
      qc.invalidateQueries({ queryKey: ["inventory"] });
      qc.invalidateQueries({ queryKey: ["masterdata", "products"] });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

export const useCreateRequest = () =>
  useChange((body: QrRequest) => api<QrDetail>("/quotation-requests", { method: "POST", body }));

export const useAddQuotation = () =>
  useChange(({ qrId, body }: { qrId: number; body: QuotationRequestBody }) =>
    api<QrDetail>(`/quotation-requests/${qrId}/quotations`, { method: "POST", body }),
  );

export const useAttachQuotation = () =>
  useChange(({ quotationId, file }: { quotationId: number; file: File }) => {
    const form = new FormData();
    form.append("file", file);
    return api<AttachmentResponse>(`/quotations/${quotationId}/attachment`, { method: "POST", body: form });
  });

export const useChooseQuotation = () =>
  useChange(({ quotationId, reason }: { quotationId: number; reason: string }) =>
    api<QrDetail>(`/quotations/${quotationId}/choose`, { method: "POST", body: { reason } }),
  );

export const useCreatePo = () =>
  useChange((quotationId: number) =>
    api<PoDetail>(`/quotations/${quotationId}/create-po`, { method: "POST" }),
  );

export const useUpdatePo = () =>
  useChange(({ id, body }: { id: number; body: PoUpdateRequest }) =>
    api<PoDetail>(`/purchase-orders/${id}`, { method: "PUT", body }),
  );

/** Approve (Draft → Approved) or send (Approved → Sent). */
export const usePoStep = () =>
  useChange(({ id, step }: { id: number; step: "approve" | "send" }) =>
    api<PoDetail>(`/purchase-orders/${id}/${step}`, { method: "POST" }),
  );

export const useCancelPo = () =>
  useChange(({ id, reason }: { id: number; reason: string }) =>
    api<PoDetail>(`/purchase-orders/${id}/cancel`, { method: "POST", body: { reason } }),
  );

export const useReceiveGoods = () =>
  useChange(({ id, body }: { id: number; body: ReceiptRequest }) =>
    api<ReceiptResult>(`/purchase-orders/${id}/receipts`, { method: "POST", body }),
  );

export const useRecordPayment = () =>
  useChange((body: PaymentRequest) => api<PaymentRow>("/supplier-payments", { method: "POST", body }));

/** The scanned quotation – opens in a new tab. */
export function attachmentUrl(quotationId: number): string {
  return `/api/quotations/${quotationId}/attachment`;
}

/** Purchase order PDF (A4) – opens in a new tab to print. */
export function poPdfUrl(id: number): string {
  return `/api/print/purchase-order/${id}`;
}
