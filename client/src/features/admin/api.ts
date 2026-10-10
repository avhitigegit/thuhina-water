"use client";

/* Administration API hooks (design 7.2 Admin): users, roles matrix, audit log, company settings and logo. */
import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/lib/api/client";
import type {
  AuditFilterOptions,
  AuditPage,
  CompanySettings,
  CreateUserRequest,
  ResetPasswordResponse,
  RolesMatrixResponse,
  UpdateUserRequest,
  UserResponse,
} from "@/lib/api/types";

const USERS_KEY = ["admin", "users"] as const;
const AUDIT_KEY = ["admin", "audit"] as const;
const COMPANY_KEY = ["admin", "company"] as const;

export function useUsers() {
  return useQuery({
    queryKey: USERS_KEY,
    queryFn: ({ signal }) => api<UserResponse[]>("/users", { signal }),
  });
}

/** After a user change: reload the list and the audit log (it has a new row). */
function useUserMutation<TVars, TResult>(fn: (vars: TVars) => Promise<TResult>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: USERS_KEY });
      qc.invalidateQueries({ queryKey: AUDIT_KEY });
    },
    // The pop-ups show field errors themselves.
    meta: { silent: true },
  });
}

export function useCreateUser() {
  return useUserMutation((body: CreateUserRequest) => api<UserResponse>("/users", { method: "POST", body }));
}

export function useUpdateUser() {
  return useUserMutation(({ id, body }: { id: number; body: UpdateUserRequest }) =>
    api<UserResponse>(`/users/${id}`, { method: "PUT", body }),
  );
}

export function useSetUserActive() {
  return useUserMutation(({ id, active }: { id: number; active: boolean }) =>
    api<UserResponse>(`/users/${id}/${active ? "activate" : "deactivate"}`, { method: "POST" }),
  );
}

export function useResetPassword() {
  return useUserMutation((id: number) =>
    api<ResetPasswordResponse>(`/users/${id}/reset-password`, { method: "POST" }),
  );
}

export function useRolesMatrix() {
  return useQuery({
    queryKey: ["admin", "roles-matrix"],
    queryFn: ({ signal }) => api<RolesMatrixResponse>("/roles/matrix", { signal }),
    staleTime: Infinity,
  });
}

export interface AuditQuery {
  from: string | null;
  to: string | null;
  user: string;
  action: string;
  entity: string;
  q: string;
  page: number;
  size: number;
}

export function useAuditLog(query: AuditQuery) {
  return useQuery({
    queryKey: [...AUDIT_KEY, query],
    queryFn: ({ signal }) =>
      api<AuditPage>("/audit-log", { signal, query: { ...query } as Record<string, string | number | null> }),
    placeholderData: keepPreviousData,
  });
}

export function useAuditFilters() {
  return useQuery({
    queryKey: [...AUDIT_KEY, "filters"],
    queryFn: ({ signal }) => api<AuditFilterOptions>("/audit-log/filters", { signal }),
  });
}

export function useCompany() {
  return useQuery({
    queryKey: COMPANY_KEY,
    queryFn: ({ signal }) => api<CompanySettings>("/settings/company", { signal }),
  });
}

function useCompanyMutation<TVars>(fn: (vars: TVars) => Promise<CompanySettings>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (company) => {
      qc.setQueryData(COMPANY_KEY, company);
      qc.invalidateQueries({ queryKey: AUDIT_KEY });
    },
    meta: { silent: true },
  });
}

export function useSaveCompany() {
  return useCompanyMutation((body: Partial<CompanySettings>) =>
    api<CompanySettings>("/settings/company", { method: "PUT", body }),
  );
}

export function useUploadLogo() {
  return useCompanyMutation((file: File) => {
    const form = new FormData();
    form.append("file", file);
    return api<CompanySettings>("/settings/company/logo", { method: "POST", body: form });
  });
}

export function useRemoveLogo() {
  return useCompanyMutation(() => api<CompanySettings>("/settings/company/logo", { method: "DELETE" }));
}
