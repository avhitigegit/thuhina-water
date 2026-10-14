"use client";

/* Expenses API (design 7.2 Expenses): month view, month-by-month summary, add / edit / delete, expense types. */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  ExpenseMonthView,
  ExpenseSaved,
  ExpenseSummary,
  ExpenseType,
  ExpenseTypeRequest,
} from "@/lib/api/types";

const KEY = ["expenses"] as const;

export interface MonthFilters {
  /** "2026-09"; empty = this month. */
  month: string;
  type: string;
  q: string;
}

export function useExpenseMonth(f: MonthFilters) {
  return useQuery({
    queryKey: [...KEY, "month", f],
    queryFn: ({ signal }) => api<ExpenseMonthView>("/expenses", { signal, query: { ...f } }),
    placeholderData: keepPreviousData,
  });
}

/** Type × month; no range = the 6 months up to this month. */
export function useExpenseSummary(enabled = true) {
  return useQuery({
    queryKey: [...KEY, "summary"],
    queryFn: ({ signal }) => api<ExpenseSummary>("/expenses/summary", { signal }),
    enabled,
  });
}

export function useExpenseTypes() {
  return useQuery({
    queryKey: [...KEY, "types"],
    queryFn: ({ signal }) => api<ExpenseType[]>("/expense-types", { signal }),
  });
}

/** What the add / edit pop-up sends; the server checks everything (prototype messages). */
export interface ExpenseBody {
  paidDate: string | null;
  forMonth: string;
  typeId: number | null;
  description: string;
  paidTo: string;
  billNo: string;
  amount: number | null;
  method: "CASH" | "BANK_TRANSFER" | "CHEQUE";
  reference: string;
  version?: number;
}

/** Every change refreshes the expenses page and the audit log. */
function useChange<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: KEY });
      qc.invalidateQueries({ queryKey: ["admin", "audit"] });
    },
    meta: { silent: true },
  });
}

export const useSaveExpense = () =>
  useChange(({ id, body }: { id: number | null; body: ExpenseBody }) =>
    id === null
      ? api<ExpenseSaved>("/expenses", { method: "POST", body })
      : api<ExpenseSaved>(`/expenses/${id}`, { method: "PUT", body }),
  );

export const useDeleteExpense = () =>
  useChange(({ id, reason, version }: { id: number; reason: string; version: number }) =>
    api<void>(`/expenses/${id}`, { method: "DELETE", body: { reason, version } }),
  );

export const useSaveExpenseType = () =>
  useChange(({ id, body }: { id: number | null; body: Partial<ExpenseTypeRequest> }) =>
    id === null
      ? api<ExpenseType>("/expense-types", { method: "POST", body })
      : api<ExpenseType>(`/expense-types/${id}`, { method: "PUT", body }),
  );

/** Expense statement PDF for a month – opens in a new tab to print. */
export function expenseStatementUrl(month: string): string {
  return `/api/print/expenses?month=${month}`;
}
