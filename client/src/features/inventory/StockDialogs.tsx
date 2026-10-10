"use client";

/*
 * Record damage (FR-29, BR-05) and Stock adjustment (FR-30) pop-ups (prototype inventory/inventory.html).
 * Company damage only for now – the Customer option comes with Sales (M06).
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, useForm, useWatch, type Resolver } from "react-hook-form";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useCurrentUser } from "@/features/auth/access";
import { useBottleTypes } from "@/features/masterdata/api";
import type { StockOverview } from "@/lib/api/types";
import { formatMoney, todayIso } from "@/lib/format";
import { showServerErrors } from "@/lib/forms";
import { toast } from "@/lib/toast";
import { useAdjustStock, useRecordDamage } from "./api";
import { ADJUSTABLE, DAMAGE_LOCATIONS } from "./labels";
import { adjustmentSchema, damageSchema, type AdjustmentValues, type DamageValues } from "./schemas";

export function DamageDialog({ stock, onClose }: { stock: StockOverview; onClose: () => void }) {
  const today = todayIso();
  const record = useRecordDamage();
  const bottleTypes = useBottleTypes();
  const [error, setError] = useState("");
  const firstBottle = stock.bottles[0]?.code ?? "";
  const {
    register,
    control,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<DamageValues>({
    resolver: zodResolver(damageSchema(today)) as unknown as Resolver<DamageValues>,
    defaultValues: {
      date: today,
      bottleTypeCode: firstBottle,
      qty: "1",
      location: "FILLED_IN_STORE",
      reason: "",
    },
  });
  const [bottle, qty] = useWatch({ control, name: ["bottleTypeCode", "qty"] });
  const deposit = bottleTypes.data?.find((b) => b.code === bottle)?.deposit ?? 0;
  const value = (Number(qty) || 0) * deposit;

  const submit = handleSubmit((v) => {
    setError("");
    record.mutate(
      {
        date: v.date as string,
        bottleTypeCode: v.bottleTypeCode,
        qty: Number(v.qty),
        location: v.location as (typeof DAMAGE_LOCATIONS)[number]["value"],
        reason: v.reason,
      },
      {
        onSuccess: () => {
          toast("Damage recorded and written off", "ok");
          onClose();
        },
        onError: (err) =>
          setError(
            showServerErrors(
              err,
              ["date", "bottleTypeCode", "qty", "location", "reason"] as const,
              setFieldError,
            ) ?? "",
          ),
      },
    );
  });

  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title="Record damaged bottles"
      onSubmit={() => void submit()}
      submitting={record.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="radio-row" style={{ marginBottom: 10 }}>
        <label className="check">
          <input type="radio" checked readOnly /> <b>Company</b> – in store, at factory or during delivery
          (written off at company cost)
        </label>
        <label className="check muted" title="Comes with Sales & Bills (M06)">
          <input type="radio" disabled /> Customer – customer pays for a new bottle (from Sales &amp; Bills)
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
        <div className="field req full">
          <label htmlFor="dmg-reason">Reason</label>
          <input
            id="dmg-reason"
            placeholder="e.g. Dropped while loading"
            className={errors.reason ? "invalid" : undefined}
            {...register("reason")}
          />
          {errors.reason && <span className="error">{errors.reason.message}</span>}
        </div>
      </div>
      <p className="small muted" style={{ marginTop: 10 }}>
        The bottles are disposed of and written off. Value: {formatMoney(value)}.
      </p>
    </FormDialog>
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
