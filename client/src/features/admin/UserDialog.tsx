"use client";

/*
 * New user / Edit user pop-up (prototype admin/administration.html "edit"): full name, username, role, phone,
 * and a temporary password for a new user. Field errors from the server ("Username already taken.") are shown
 * under the field. The parent gives it a new `key` each time it opens, so it always starts from the user's values.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
import type { z } from "zod";
import { FormDialog } from "@/components/shared/FormDialog";
import { ApiError } from "@/lib/api/client";
import type { UserResponse } from "@/lib/api/types";
import { toast } from "@/lib/toast";
import { useCreateUser, useUpdateUser } from "./api";
import { ROLES } from "./labels";
import { editUserSchema, newUserSchema, type NewUserValues } from "./schemas";

type Output = z.output<typeof newUserSchema>;
const FIELDS = ["fullName", "username", "role", "phone", "temporaryPassword"] as const;

const EMPTY: NewUserValues = {
  fullName: "",
  username: "",
  role: "DELIVERY_STAFF",
  phone: "",
  temporaryPassword: "",
};

export function UserDialog({
  open,
  onOpenChange,
  user,
  isSelf,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  /** null = new user. */
  user: UserResponse | null;
  isSelf: boolean;
}) {
  const isNew = user === null;
  const create = useCreateUser();
  const update = useUpdateUser();
  const [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<NewUserValues, unknown, Output>({
    resolver: zodResolver(isNew ? newUserSchema : editUserSchema) as unknown as Resolver<
      NewUserValues,
      unknown,
      Output
    >,
    defaultValues: user
      ? {
          fullName: user.fullName,
          username: user.username,
          role: user.role as NewUserValues["role"],
          phone: user.phone ?? "",
          temporaryPassword: "",
        }
      : EMPTY,
  });

  function onServerError(err: Error) {
    if (err instanceof ApiError) {
      const fields = err.fieldErrors;
      let shown = false;
      for (const f of FIELDS) {
        if (fields[f]) {
          setFieldError(f, { message: fields[f] });
          shown = true;
        }
      }
      if (!shown) setError(err.message);
    } else {
      setError("Something went wrong. Please try again.");
    }
  }

  const submit = handleSubmit((v) => {
    setError("");
    if (isNew) {
      create.mutate(
        {
          fullName: v.fullName,
          username: v.username,
          role: v.role,
          phone: v.phone,
          temporaryPassword: v.temporaryPassword,
        },
        {
          onSuccess: (u) => {
            toast(`User created – give ${u.fullName} the temporary password`, "ok");
            onOpenChange(false);
          },
          onError: onServerError,
        },
      );
    } else {
      update.mutate(
        {
          id: user.id,
          body: {
            fullName: v.fullName,
            username: v.username,
            role: v.role,
            phone: v.phone,
            version: user.version,
          },
        },
        {
          onSuccess: () => {
            toast("User updated", "ok");
            onOpenChange(false);
          },
          onError: onServerError,
        },
      );
    }
  });

  const busy = create.isPending || update.isPending;
  const field = (name: (typeof FIELDS)[number]) => ({
    id: `user-${name}`,
    className: errors[name] ? "invalid" : undefined,
    "aria-invalid": errors[name] ? true : undefined,
    ...register(name),
  });

  return (
    <FormDialog
      open={open}
      onOpenChange={onOpenChange}
      title={isNew ? "New user" : `Edit ${user.fullName}`}
      onSubmit={() => void submit()}
      submitting={busy}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req full">
          <label htmlFor="user-fullName">Full name</label>
          <input {...field("fullName")} autoFocus autoComplete="off" />
          {errors.fullName && <span className="error">{errors.fullName.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="user-username">Username</label>
          <input {...field("username")} placeholder="e.g. saman.d" autoComplete="off" spellCheck={false} />
          {errors.username && <span className="error">{errors.username.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="user-role">Role</label>
          {isSelf && user ? (
            <>
              {/* Shown only; the value is kept in the hidden field (a disabled field is not submitted). */}
              <select id="user-role" value={user.role} disabled>
                {ROLES.map((r) => (
                  <option key={r.code} value={r.code}>
                    {r.label}
                  </option>
                ))}
              </select>
              <input type="hidden" {...register("role")} />
            </>
          ) : (
            <select {...field("role")}>
              {ROLES.map((r) => (
                <option key={r.code} value={r.code}>
                  {r.label}
                </option>
              ))}
            </select>
          )}
          {errors.role ? (
            <span className="error">{errors.role.message}</span>
          ) : (
            isSelf && <span className="help">You cannot change your own role.</span>
          )}
        </div>
        <div className="field">
          <label htmlFor="user-phone">Phone</label>
          <input {...field("phone")} autoComplete="off" />
          {errors.phone && <span className="error">{errors.phone.message}</span>}
        </div>
        {isNew && (
          <div className="field req">
            <label htmlFor="user-temporaryPassword">Temporary password</label>
            <input
              {...field("temporaryPassword")}
              type="password"
              placeholder="at least 8 characters"
              autoComplete="new-password"
            />
            {errors.temporaryPassword ? (
              <span className="error">{errors.temporaryPassword.message}</span>
            ) : (
              <span className="help">The user must change it at the first login.</span>
            )}
          </div>
        )}
      </div>
    </FormDialog>
  );
}
