"use client";

import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";
import { Toaster } from "@/components/shared/Toaster";
import { ApiError, isRedirectError } from "@/lib/api/client";
import { toast } from "@/lib/toast";

declare module "@tanstack/react-query" {
  interface Register {
    /** silent: the screen shows the error itself (no toast). */
    queryMeta: { silent?: boolean };
    mutationMeta: { silent?: boolean };
  }
}

/** API errors become a red toast with the server's message, unless the screen handles them (meta.silent). */
function showError(error: unknown, silent?: boolean) {
  if (silent || isRedirectError(error)) return;
  if (error instanceof DOMException && error.name === "AbortError") return;
  toast(error instanceof ApiError ? error.message : "Something went wrong. Please try again.", "bad");
}

function makeQueryClient() {
  return new QueryClient({
    queryCache: new QueryCache({ onError: (error, query) => showError(error, query.meta?.silent) }),
    mutationCache: new MutationCache({
      onError: (error, _vars, _ctx, mutation) => showError(error, mutation.meta?.silent),
    }),
    defaultOptions: {
      queries: {
        staleTime: 30 * 1000,
        refetchOnWindowFocus: false,
        // Business errors and "not allowed" never get better by retrying.
        retry: (count, error) =>
          !(error instanceof ApiError && error.status > 0 && error.status < 500) && count < 2,
      },
      mutations: { retry: false },
    },
  });
}

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(makeQueryClient);
  return (
    <QueryClientProvider client={client}>
      {children}
      <Toaster />
    </QueryClientProvider>
  );
}
