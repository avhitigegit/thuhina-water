"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type { ChangePasswordRequest, LoginRequest, MeResponse } from "@/lib/api/types";

export const ME_KEY = ["auth", "me"] as const;

/** The logged-in user, permissions, landing page and menu. 401 → the API client sends the user to login. */
export function useMe() {
  return useQuery({
    queryKey: ME_KEY,
    queryFn: ({ signal }) => api<MeResponse>("/auth/me", { signal }),
    staleTime: 5 * 60 * 1000,
    retry: false,
    meta: { silent: true },
  });
}

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: LoginRequest) => api<MeResponse>("/auth/login", { method: "POST", body }),
    onSuccess: (me) => {
      qc.clear();
      qc.setQueryData(ME_KEY, me);
    },
    meta: { silent: true },
  });
}

export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api<void>("/auth/logout", { method: "POST" }),
    onSettled: () => qc.clear(),
    meta: { silent: true },
  });
}

export function useChangePassword() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: ChangePasswordRequest) =>
      api<MeResponse>("/auth/change-password", { method: "POST", body }),
    onSuccess: (me) => qc.setQueryData(ME_KEY, me),
    meta: { silent: true },
  });
}
