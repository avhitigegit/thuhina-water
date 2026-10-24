"use client";

/* Billing & payments API (design 7.2 Billing): accounts with aging, payments, recent money, monthly invoices, prints. */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  AccountList,
  AccountSummary,
  AccountView,
  GenerateResult,
  InvoicePreview,
  InvoiceRow,
  InvoiceView,
  PaymentList,
  CustomerPaymentRequest,
  PaymentSaved,
  RecentMoney,
} from "@/lib/api/types";

const KEY = ["billing"] as const;

export interface AccountFilters {
  q: string;
  pay: string;
  show: string;
  page: number;
  size: number;
}

export function useAccounts(f: AccountFilters) {
  return useQuery({
    queryKey: [...KEY, "accounts", f],
    queryFn: ({ signal }) => api<AccountList>("/accounts", { signal, query: { ...f } }),
    placeholderData: keepPreviousData,
  });
}

export function useAccountSummary() {
  return useQuery({
    queryKey: [...KEY, "summary"],
    queryFn: ({ signal }) => api<AccountSummary>("/accounts/summary", { signal }),
  });
}

/** One customer's account and invoices (Receive payment pop-up). */
export function useAccount(customerId: number | null) {
  return useQuery({
    queryKey: [...KEY, "account", customerId],
    queryFn: ({ signal }) => api<AccountView>(`/accounts/${customerId}`, { signal }),
    enabled: customerId !== null,
  });
}

export function useRecentMoney() {
  return useQuery({
    queryKey: [...KEY, "recent"],
    queryFn: ({ signal }) => api<RecentMoney[]>("/payments/recent", { signal }),
  });
}

/** A customer's payments, newest first (customer panel). */
export function useCustomerPayments(customerId: number, enabled = true) {
  return useQuery({
    queryKey: [...KEY, "payments", customerId],
    queryFn: ({ signal }) =>
      api<PaymentList>("/payments", { signal, query: { customer: customerId, size: 100 } }),
    enabled,
  });
}

/** A customer's invoices, newest first (customer panel). */
export function useCustomerInvoices(customerId: number, enabled = true) {
  return useQuery({
    queryKey: [...KEY, "invoices", customerId],
    queryFn: ({ signal }) => api<InvoiceRow[]>("/invoices", { signal, query: { customer: customerId } }),
    enabled,
  });
}

/** Monthly invoices tab; no period = the server's default (last month). */
export function useInvoicePreview(period: string | null) {
  return useQuery({
    queryKey: [...KEY, "preview", period],
    queryFn: ({ signal }) => api<InvoicePreview>("/invoices/preview", { signal, query: { period } }),
    placeholderData: keepPreviousData,
  });
}

export function useInvoice(id: number | null) {
  return useQuery({
    queryKey: [...KEY, "invoice", id],
    queryFn: ({ signal }) => api<InvoiceView>(`/invoices/${id}`, { signal }),
    enabled: id !== null,
  });
}

/** Payments and invoices change balances: refresh billing, the customer list / panel and the audit log. */
function useChange<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
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

export const useReceivePayment = () =>
  useChange((body: CustomerPaymentRequest) => api<PaymentSaved>("/payments", { method: "POST", body }));

export const useGenerateInvoices = () =>
  useChange((period: string) =>
    api<GenerateResult>("/invoices/generate", { method: "POST", body: { period } }),
  );

// PDFs open in a new tab to print.
export const receiptUrl = (paymentId: number) => `/api/print/receipt/${paymentId}`;
export const invoiceUrl = (invoiceId: number) => `/api/print/invoice/${invoiceId}`;
export const statementUrl = (customerId: number, from: string, to: string) =>
  `/api/print/statement/${customerId}?from=${from}&to=${to}`;

export function openPdf(url: string) {
  window.open(url, "_blank", "noopener");
}
