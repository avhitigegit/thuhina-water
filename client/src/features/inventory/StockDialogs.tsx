"use client";

/*
 * Record damage (FR-29, BR-05, BR-06) and Stock adjustment (FR-30) pop-ups (prototype inventory/inventory.html).
 * Damage is Company (written off at company cost, with the place) or Customer (a customer-damage bill: the customer
 * pays a new filled bottle – deposit + water; over the credit limit needs Admin approval).
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm, useWatch, type Resolver } from "react-hook-form";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useCan, useCurrentUser } from "@/features/auth/access";
import { CustomerPicker } from "@/features/customers/CustomerPicker";
import { useBottleTypes } from "@/features/masterdata/api";
import { useSalePreview } from "@/features/sales/api";
import { CREDIT_LIMIT, CreditApprovalDialog } from "@/features/sales/CreditApprovalDialog";
import { ApiError } from "@/lib/api/client";
import type { CustomerLookupRow, StockOverview } from "@/lib/api/types";
import { formatMoney, todayIso } from "@/lib/format";
import { showServerErrors } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useAdjustStock, useRecordDamage } from "./api";
import { ADJUSTABLE, DAMAGE_LOCATIONS } from "./labels";
import { adjustmentSchema, damageSchema, type AdjustmentValues, type DamageValues } from "./schemas";

export function DamageDialog({ stock, onClose }: { stock: StockOverview; onClose: () => void }) {
  const today = todayIso();
  const record = useRecordDamage();
  const bottleTypes = useBottleTypes();
  const canSell = useCan(P.SALES_EDIT);
  const [error, setError] = useState("");
  const [customer, setCustomer] = useState<CustomerLookupRow | null>(null);
  const [creditError, setCreditError] = useState<ApiError | null>(null);
  const firstBottle = stock.bottles[0]?.code ?? "";
  const {
    register,
    control,
    handleSubmit,
    setValue,
    setError: setFieldError,
    formState: { errors },
  } = useForm<DamageValues>({
    resolver: zodResolver(damageSchema(today)) as unknown as Resolver<DamageValues>,
    defaultValues: {
      responsibility: "COMPANY",
      date: today,
      bottleTypeCode: firstBottle,
      qty: "1",
      location: "FILLED_IN_STORE",
      customerId: null,
      paid: "",
      reason: "",
    },
  });
  const [responsibility, bottle, qty, date, paid, reason] = useWatch({
    control,
    name: ["responsibility", "bottleTypeCode", "qty", "date", "paid", "reason"],
  });
  const forCustomer = responsibility === "CUSTOMER";
  const deposit = bottleTypes.data?.find((b) => b.code === bottle)?.deposit ?? 0;
  const value = (Number(qty) || 0) * deposit;
  // Customer damage from this pop-up: the customer buys a filled replacement (Sales & Bills has more options).
  const preview = useSalePreview(
    "damage",
    forCustomer && customer && date
      ? {
          customerId: customer.id,
          date,
          bottleTypeCode: bottle,
          qty: Number(qty) || 0,
          mode: "REPLACE",
          giveFilled: true,
          reason: reason.trim(),
          paid: paid.trim() === "" ? undefined : Number(paid) || 0,
        }
      : null,
  );
  const p = forCustomer && customer ? preview.data : undefined;

  function send(v: DamageValues, approveOverLimit: boolean) {
    setError("");
    record.mutate(
      forCustomer
        ? {
            responsibility: "CUSTOMER",
            date: v.date as string,
            bottleTypeCode: v.bottleTypeCode,
            qty: Number(v.qty),
            reason: v.reason,
            customerId: v.customerId ?? undefined,
            mode: "REPLACE",
            giveFilled: true,
            paid: v.paid.trim() === "" ? 0 : Number(v.paid),
            approveOverLimit,
          }
        : {
            responsibility: "COMPANY",
            date: v.date as string,
            bottleTypeCode: v.bottleTypeCode,
            qty: Number(v.qty),
            location: v.location as (typeof DAMAGE_LOCATIONS)[number]["value"],
            reason: v.reason,
          },
      {
        onSuccess: () => {
          setCreditError(null);
          toast(forCustomer ? "Customer damage recorded" : "Damage recorded and written off", "ok");
          onClose();
        },
        onError: (err) => {
          if (err instanceof ApiError && err.code === CREDIT_LIMIT) {
            setCreditError(err);
            return;
          }
          setCreditError(null);
          setError(
            showServerErrors(
              err,
              ["date", "bottleTypeCode", "qty", "location", "reason", "customerId", "paid"] as const,
              setFieldError,
            ) ?? "",
          );
        },
      },
    );
  }

  const submit = handleSubmit((v) => send(v, false));

  return (
    <>
      <FormDialog
        open
        wide
        onOpenChange={(o) => !o && onClose()}
        title="Record damaged bottles"
        onSubmit={() => void submit()}
        submitting={record.isPending || !!creditError}
      >
        {error && <div className="banner bad">{error}</div>}
        <div className="radio-row" style={{ marginBottom: 10 }}>
          <label className="check">
            <input type="radio" value="COMPANY" {...register("responsibility")} /> <b>Company</b> – in store,
            at factory or during delivery (written off at company cost)
          </label>
          <label
            className={canSell ? "check" : "check muted"}
            title={canSell ? undefined : "Only users who enter sales can record customer damage"}
          >
            <input type="radio" value="CUSTOMER" disabled={!canSell} {...register("responsibility")} />{" "}
            <b>Customer</b> – customer pays for a new bottle
          </label>
        </div>
        <div className="form-grid">
          <div className="field req">
            <label htmlFor="dmg-date">Date</label>
            <Controller
              control={control}
              name="date"
              render={({ field }) => (
                <DateField
                  id="dmg-date"
                  value={field.value}
                  onChange={field.onChange}
                  invalid={!!errors.date}
                />
              )}
            />
            {errors.date && <span className="error">{errors.date.message}</span>}
          </div>
          <div className="field req">
            <label htmlFor="dmg-bottle">Bottle</label>
            <select id="dmg-bottle" {...register("bottleTypeCode")}>
              {stock.bottles.map((b) => (
                <option key={b.code} value={b.code}>
                  {b.name}
                </option>
              ))}
            </select>
            {errors.bottleTypeCode && <span className="error">{errors.bottleTypeCode.message}</span>}
          </div>
          <div className="field req">
            <label htmlFor="dmg-qty">Quantity</label>
            <input
              id="dmg-qty"
              type="number"
              min="1"
              className={errors.qty ? "invalid num" : "num"}
              {...register("qty")}
            />
            {errors.qty && <span className="error">{errors.qty.message}</span>}
          </div>
          {forCustomer ? (
            <>
              <div className="field req span2">
                <label htmlFor="dmg-customer">Customer</label>
                <CustomerPicker
                  id="dmg-customer"
                  value={customer}
                  invalid={!!errors.customerId}
                  onChange={(c) => {
                    setCustomer(c);
                    setValue("customerId", c?.id ?? null, { shouldValidate: !!errors.customerId });
                  }}
                />
                {errors.customerId && <span className="error">{errors.customerId.message}</span>}
              </div>
              <div className="field">
                <label htmlFor="dmg-paid">Paid now (Rs.)</label>
                <input
                  id="dmg-paid"
                  type="number"
                  min="0"
                  className={errors.paid ? "invalid num" : "num"}
                  {...register("paid")}
                />
                {errors.paid && <span className="error">{errors.paid.message}</span>}
              </div>
            </>
          ) : (
            <div className="field req">
              <label htmlFor="dmg-where">Where</label>
              <select id="dmg-where" {...register("location")}>
                {DAMAGE_LOCATIONS.map((l) => (
                  <option key={l.value} value={l.value}>
                    {l.label}
                  </option>
                ))}
              </select>
              {errors.location && <span className="error">{errors.location.message}</span>}
            </div>
          )}
          <div className="field req full">
            <label htmlFor="dmg-reason">Reason</label>
            <input
              id="dmg-reason"
              placeholder={
                forCustomer ? "e.g. Bottle cracked – dropped by customer" : "e.g. Dropped while loading"
              }
              className={errors.reason ? "invalid" : undefined}
              {...register("reason")}
            />
            {errors.reason && <span className="error">{errors.reason.message}</span>}
          </div>
        </div>
        {forCustomer ? (
          <div style={{ marginTop: 10 }}>
            {p?.problems
              .filter((x) => x.field !== "reason")
              .map((x) => (
                <div key={x.message} className="banner bad">
                  {x.message}
                </div>
              ))}
            {p ? (
              <p className="small">
                Customer pays a new bottle (deposit) {formatMoney(p.deposit)} + water {formatMoney(p.water)} ={" "}
                <b>{formatMoney(p.amount)}</b>. The damaged bottle is written off and a filled replacement is
                given. For more options use Sales &amp; Bills → Customer-damaged bottle.
              </p>
            ) : (
              <p className="small muted">Select the customer to see the charge.</p>
            )}
          </div>
        ) : (
          <p className="small muted" style={{ marginTop: 10 }}>
            The bottles are disposed of and written off. Value: {formatMoney(value)}.
          </p>
        )}
      </FormDialog>
      {creditError && (
        <CreditApprovalDialog
          error={creditError}
          busy={record.isPending}
          onApprove={() => void handleSubmit((v) => send(v, true))()}
          onClose={() => setCreditError(null)}
        />
      )}
    </>
  );
}

export function AdjustmentDialog({ stock, onClose }: { stock: StockOverview; onClose: () => void }) {
  const today = todayIso();
  const me = useCurrentUser();
  const adjust = useAdjustStock();
  const [error, setError] = useState("");
  const items = [
    ...stock.bottles.map((b) => ({ code: b.code, name: b.name, bottle: true })),
    ...stock.products.map((p) => ({ code: p.code, name: p.name, bottle: false })),
  ];
  const {
    register,
    control,
    handleSubmit,
    setValue,
    setError: setFieldError,
    formState: { errors },
  } = useForm<AdjustmentValues>({
    resolver: zodResolver(adjustmentSchema(today)) as unknown as Resolver<AdjustmentValues>,
    defaultValues: {
      date: today,
      itemCode: items[0]?.code ?? "",
      isBottle: items[0]?.bottle ?? true,
      mode: "COUNT",
      bucket: "FILLED",
      counted: "",
      qty: "",
      reason: "",
    },
  });
  const [itemCode, mode, bucket] = useWatch({ control, name: ["itemCode", "mode", "bucket"] });
  const item = items.find((i) => i.code === itemCode);
  const isBottle = item?.bottle ?? true;
  const lost = isBottle && mode === "LOST";
  const bottleRow = stock.bottles.find((b) => b.code === itemCode);
  const bucketKey = ADJUSTABLE.find((b) => b.value === bucket)?.key ?? "filled";
  const system = isBottle
    ? (bottleRow?.[bucketKey] ?? 0)
    : (stock.products.find((p) => p.code === itemCode)?.stockQty ?? 0);

  const submit = handleSubmit((v) => {
    setError("");
    adjust.mutate(
      {
        date: v.date as string,
        itemCode: v.itemCode,
        mode: isBottle ? v.mode : "COUNT",
        bucket: isBottle ? v.bucket : undefined,
        counted: lost ? undefined : Number(v.counted),
        qty: lost ? Number(v.qty) : undefined,
        reason: v.reason,
      },
      {
        onSuccess: (r) => {
          toast(`Adjustment ${r.adjNo} saved`, "ok");
          onClose();
        },
        onError: (err) =>
          setError(
            showServerErrors(
              err,
              ["date", "itemCode", "mode", "bucket", "counted", "qty", "reason"] as const,
              setFieldError,
            ) ?? "",
          ),
      },
    );
  });

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Stock adjustment"
      onSubmit={() => void submit()}
      submitting={adjust.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      {isBottle && (
        <div className="radio-row" style={{ marginBottom: 10 }}>
          <label className="check">
            <input type="radio" value="COUNT" {...register("mode")} /> Physical count
          </label>
          <label className="check">
            <input type="radio" value="LOST" {...register("mode")} /> Lost bottles
          </label>
        </div>
      )}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req">
          <label htmlFor="adj-date">Date</label>
          <Controller
            control={control}
            name="date"
            render={({ field }) => (
              <DateField
                id="adj-date"
                value={field.value}
                onChange={field.onChange}
                invalid={!!errors.date}
              />
            )}
          />
          {errors.date && <span className="error">{errors.date.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="adj-item">Item</label>
          <select
            id="adj-item"
            {...register("itemCode", {
              onChange: (e) =>
                setValue("isBottle", items.find((i) => i.code === e.target.value)?.bottle ?? true),
            })}
          >
            {items.map((i) => (
              <option key={i.code} value={i.code}>
                {i.name}
              </option>
            ))}
          </select>
        </div>
        {isBottle && (
          <div className="field">
            <label htmlFor="adj-bucket">Status</label>
            <select id="adj-bucket" {...register("bucket")}>
              {ADJUSTABLE.map((b) => (
                <option key={b.value} value={b.value}>
                  {b.label}
                </option>
              ))}
            </select>
            {errors.bucket && <span className="error">{errors.bucket.message}</span>}
          </div>
        )}
        <div className="field">
          <label htmlFor="adj-system">In system</label>
          <input id="adj-system" value={system} readOnly className="num" />
        </div>
        {lost ? (
          <div className="field req">
            <label htmlFor="adj-qty">Bottles lost</label>
            <input
              id="adj-qty"
              type="number"
              min="1"
              className={errors.qty ? "invalid num" : "num"}
              {...register("qty")}
            />
            {errors.qty && <span className="error">{errors.qty.message}</span>}
          </div>
        ) : (
          <div className="field req">
            <label htmlFor="adj-counted">Counted</label>
            <input
              id="adj-counted"
              type="number"
              min="0"
              className={errors.counted ? "invalid num" : "num"}
              {...register("counted")}
            />
            {errors.counted && <span className="error">{errors.counted.message}</span>}
          </div>
        )}
        <div className="field req full">
          <label htmlFor="adj-reason">Reason</label>
          <input
            id="adj-reason"
            placeholder="e.g. Month-end count"
            className={errors.reason ? "invalid" : undefined}
            {...register("reason")}
          />
          {errors.reason && <span className="error">{errors.reason.message}</span>}
        </div>
      </div>
      <p className="small muted">Recorded by {me.user.fullName}.</p>
    </FormDialog>
  );
}
