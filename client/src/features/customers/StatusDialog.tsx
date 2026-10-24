"use client";

/*
 * Deactivate / reactivate a customer (FR-13, prototype UI.customerStatus). Deactivating asks for the date and the
 * reason, and warns about bottles and money still on the account – they stay there.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm, type Resolver } from "react-hook-form";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import type { CustomerDetail } from "@/lib/api/types";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { showServerErrors } from "@/lib/forms";
import { toast } from "@/lib/toast";
import { useDeactivateCustomer, useReactivateCustomer } from "./api";
import { DEACT_REASONS, dayName } from "./labels";
import { deactivateSchema, statusReason, type DeactivateValues } from "./schemas";

/** "Saman still holds 2 × 20L bottles, owes Rs. 1,200.00. These stay on the account …" or null. */
export function OpenItems({ customer }: { customer: CustomerDetail }) {
  const held = customer.bottles.filter((b) => b.held > 0).map((b) => `${b.held} × ${b.label}`);
  const toCollect = customer.bottles.reduce((s, b) => s + b.toCollect, 0);
  const owed = customer.bottles.reduce((s, b) => s + b.owed, 0);
  const parts: string[] = [];
  if (held.length) parts.push(`still holds ${held.join(", ")} bottles`);
  if (toCollect) parts.push(`has ${toCollect} empties to collect`);
  if (owed) parts.push(`owes ${owed} bottles`);
  if (customer.outstanding > 0) parts.push(`owes ${formatMoney(customer.outstanding)}`);
  if (!parts.length) return null;
  return (
    <div className="banner warn">
      {customer.name} {parts.join(", ")}. These stay on the account – collect the bottles and the money before
      or after deactivating.
    </div>
  );
}

export function StatusDialog({ customer, onClose }: { customer: CustomerDetail; onClose: () => void }) {
  const today = todayIso();
  const deactivate = useDeactivateCustomer();
  const reactivate = useReactivateCustomer();
  const [error, setError] = useState("");
  const {
    register,
    control,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<DeactivateValues>({
    resolver: zodResolver(deactivateSchema(today)) as unknown as Resolver<DeactivateValues>,
    defaultValues: { date: today, reason: "", details: "" },
  });

  if (customer.status !== "ACTIVE") {
    return (
      <ConfirmDialog
        open
        onOpenChange={(o) => !o && onClose()}
        title={`Reactivate ${customer.name}?`}
        confirmLabel="Reactivate"
        busy={reactivate.isPending}
        onConfirm={() =>
          reactivate.mutate(customer.id, {
            onSuccess: () => {
              toast("Customer reactivated", "ok");
              onClose();
            },
            onError: (e) => toast(e.message, "bad"),
          })
        }
      >
        <p>
          {customer.name} was made inactive
          {customer.statusDate ? ` on ${formatDate(customer.statusDate)}` : ""}
          {customer.statusReason ? ` – ${customer.statusReason}` : ""}.
        </p>
        <p>They will show on the delivery lists again from the next {dayName(customer.deliveryDay)}.</p>
      </ConfirmDialog>
    );
  }

  const submit = handleSubmit((v) => {
    setError("");
    deactivate.mutate(
      { id: customer.id, body: { date: v.date as string, reason: statusReason(v.reason, v.details) } },
      {
        onSuccess: () => {
          toast("Customer deactivated", "ok");
          onClose();
        },
        onError: (err) => setError(showServerErrors(err, ["date", "reason"] as const, setFieldError) ?? ""),
      },
    );
  });

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={`Deactivate ${customer.name}?`}
      submitLabel="Deactivate"
      danger
      onSubmit={() => void submit()}
      submitting={deactivate.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <p>
        An inactive customer is taken off the delivery lists and cannot get new bills. All history is kept and
        you can reactivate at any time.
      </p>
      <OpenItems customer={customer} />
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 2fr" }}>
        <div className="field req">
          <label htmlFor="st-date">Inactive from</label>
          <Controller
            control={control}
            name="date"
            render={({ field }) => (
              <DateField id="st-date" value={field.value} onChange={field.onChange} invalid={!!errors.date} />
            )}
          />
          {errors.date && <span className="error">{errors.date.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="st-reason">Reason</label>
          <select id="st-reason" className={errors.reason ? "invalid" : undefined} {...register("reason")}>
            <option value="">Select</option>
            {DEACT_REASONS.map((r) => (
              <option key={r}>{r}</option>
            ))}
          </select>
          {errors.reason && <span className="error">{errors.reason.message}</span>}
        </div>
        <div className="field full">
          <label htmlFor="st-details">Details</label>
          <input
            id="st-details"
            placeholder="e.g. Moved to Kandy – will call if they come back"
            className={errors.details ? "invalid" : undefined}
            {...register("details")}
          />
          {errors.details && <span className="error">{errors.details.message}</span>}
        </div>
      </div>
    </FormDialog>
  );
}
