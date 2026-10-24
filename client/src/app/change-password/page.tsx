"use client";

/*
 * Change password. Forced after login when the Admin gave a temporary password (must_change_password);
 * the server refuses every other call until this is done. Also used by anyone from the top bar.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { useChangePassword, useLogout, useMe } from "@/features/auth/api";
import { ApiError } from "@/lib/api/client";
import { toast } from "@/lib/toast";

const schema = z
  .object({
    currentPassword: z.string().min(1, "Enter your current password."),
    newPassword: z.string().min(8, "The new password must be at least 8 characters.").max(100),
    confirmPassword: z.string().min(1, "Type the new password again."),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "The two new passwords do not match.",
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    path: ["newPassword"],
    message: "The new password must be different from the old one.",
  });
type FormValues = z.infer<typeof schema>;

export default function ChangePasswordPage() {
  const router = useRouter();
  const me = useMe();
  const change = useChangePassword();
  const logout = useLogout();
  const [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  const forced = me.data?.mustChangePassword ?? false;

  const onSubmit = handleSubmit((values) => {
    setError("");
    change.mutate(
      { currentPassword: values.currentPassword, newPassword: values.newPassword },
      {
        onSuccess: (updated) => {
          toast("Password changed", "ok");
          router.replace(updated.landingPage);
        },
        onError: (err) => {
          if (err instanceof ApiError && err.code === "WRONG_PASSWORD") {
            setFieldError("currentPassword", { message: err.message });
          } else if (err instanceof ApiError && err.code === "SAME_PASSWORD") {
            setFieldError("newPassword", { message: err.message });
          } else {
            setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
          }
        },
      },
    );
  });

  function onLogout() {
    // A full page load clears every cached screen of this user (intended, not a client-side navigation).
    // eslint-disable-next-line @next/next/no-location-assign-relative-destination
    logout.mutate(undefined, { onSettled: () => window.location.assign("/login") });
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="logo">T</div>
        <h1 style={{ marginBottom: 2 }}>Change password</h1>
        {me.data && (
          <p className="muted">
            {me.data.user.fullName} ({me.data.user.roleName})
          </p>
        )}
        {forced && (
          <div className="banner info">
            You logged in with a temporary password. Choose a new password to continue.
          </div>
        )}
        <form onSubmit={onSubmit} noValidate>
          <div className="field" style={{ marginBottom: 10 }}>
            <label htmlFor="currentPassword">{forced ? "Temporary password" : "Current password"}</label>
            <input
              id="currentPassword"
              type="password"
              autoComplete="current-password"
              autoFocus
              className={errors.currentPassword ? "invalid" : undefined}
              {...register("currentPassword")}
            />
            {errors.currentPassword && <span className="error">{errors.currentPassword.message}</span>}
          </div>
          <div className="field" style={{ marginBottom: 10 }}>
            <label htmlFor="newPassword">New password</label>
            <input
              id="newPassword"
              type="password"
              autoComplete="new-password"
              className={errors.newPassword ? "invalid" : undefined}
              {...register("newPassword")}
            />
            {errors.newPassword ? (
              <span className="error">{errors.newPassword.message}</span>
            ) : (
              <span className="help">At least 8 characters.</span>
            )}
          </div>
          <div className="field" style={{ marginBottom: 10 }}>
            <label htmlFor="confirmPassword">New password again</label>
            <input
              id="confirmPassword"
              type="password"
              autoComplete="new-password"
              className={errors.confirmPassword ? "invalid" : undefined}
              {...register("confirmPassword")}
            />
            {errors.confirmPassword && <span className="error">{errors.confirmPassword.message}</span>}
          </div>
          {error && (
            <div className="banner bad" role="alert">
              {error}
            </div>
          )}
          <button
            className="btn primary"
            style={{ width: "100%", justifyContent: "center", padding: 9 }}
            type="submit"
            disabled={change.isPending}
          >
            {change.isPending ? "Saving…" : "Change password"}
          </button>
        </form>
        <p className="small" style={{ marginTop: 12, display: "flex", justifyContent: "space-between" }}>
          {forced || !me.data ? <span /> : <Link href={me.data.landingPage}>← Back</Link>}
          <button type="button" className="btn link small" onClick={onLogout}>
            Log out
          </button>
        </p>
      </div>
    </div>
  );
}
