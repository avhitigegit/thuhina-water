"use client";

/*
 * New / Edit customer pop-up (FR-05, FR-08 – FR-10, FR-13, prototype master-data/customers.html "edit"):
 * details · Delivery (registration date, day, cycle, next delivery – filled automatically with the next delivery day
 * after the registration date, editable) · Water price (agreed prices for a new customer; price now + "Change
 * prices…" when editing) · Payment · WhatsApp · Notes · Status (edit). A phone already in use asks "Save anyway?".
 * (The "bottles given on the registration date" section comes with Sales, M06.)
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState } from "react";
import { Controller, useForm, useWatch, type Resolver } from "react-hook-form";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useCan } from "@/features/auth/access";
import { useBottleTypes, usePriceMatrix } from "@/features/masterdata/api";
import { ApiError } from "@/lib/api/client";
import type { CustomerDetail, CustomerRequest } from "@/lib/api/types";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { showServerErrors } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import {
  useAreas,
  useCustomer,
  useCustomerTypes,
  useDeactivateCustomer,
  useReactivateCustomer,
  useSaveCustomer,
} from "./api";
import { CustomerPriceDialog } from "./CustomerPriceDialog";
import {
  CREDIT_TERMS,
  CYCLES,
  DAYS,
  DEACT_REASONS,
  dayName,
  isoWeekday,
  nextDeliveryAfter,
  PAY_TYPES,
} from "./labels";
import { PriceText } from "./PriceText";
import { customerSchema, statusReason, type CustomerValues } from "./schemas";
import { OpenItems } from "./StatusDialog";

const SERVER_FIELDS = [
  "name",
  "phone",
  "address",
  "areaId",
  "customerTypeId",
  "registeredOn",
  "deliveryDay",
  "cycleWeeks",
  "nextDeliveryDate",
  "payType",
  "creditLimit",
  "termsDays",
  "agreedReason",
  "whatsappNumber",
  "notes",
] as const;

/** Opens the pop-up for a new customer (`customerId` null) or loads the customer first. */
export function CustomerDialog({
  customerId,
  onClose,
  onSaved,
}: {
  customerId: number | null;
  onClose: () => void;
  onSaved?: (c: CustomerDetail) => void;
}) {
  const existing = useCustomer(customerId);
  if (customerId !== null && !existing.data) return null;
  return <CustomerForm customer={existing.data ?? null} onClose={onClose} onSaved={onSaved} />;
}

function CustomerForm({
  customer,
  onClose,
  onSaved,
}: {
  customer: CustomerDetail | null;
  onClose: () => void;
  onSaved?: (c: CustomerDetail) => void;
}) {
  const isNew = customer === null;
  const today = todayIso();
  const canPrices = useCan(P.CUSTOMER_PRICES_EDIT);
  const areas = useAreas();
  const types = useCustomerTypes();
  const bottleTypes = useBottleTypes();
  const matrix = usePriceMatrix();
  const save = useSaveCustomer();
  const deactivate = useDeactivateCustomer();
  const reactivate = useReactivateCustomer();
  const bottles = (bottleTypes.data ?? []).filter((b) => b.active);
  const [error, setError] = useState("");
  const [dupMessage, setDupMessage] = useState<string | null>(null);
  const [pricesOpen, setPricesOpen] = useState(false);
  const [status, setStatus] = useState({
    value: customer?.status ?? "ACTIVE",
    date: today as string | null,
    reason: "",
    details: "",
  });

  const schema = customerSchema(
    isNew,
    today,
    bottles.map((b) => b.code),
  );
  const defaultDay = customer?.deliveryDay ?? 1;
  const {
    register,
    control,
    handleSubmit,
    setValue,
    getValues,
    setError: setFieldError,
    formState: { errors },
  } = useForm<CustomerValues>({
    resolver: zodResolver(schema) as unknown as Resolver<CustomerValues>,
    defaultValues: {
      name: customer?.name ?? "",
      phone: customer?.phone ?? "",
      address: customer?.address ?? "",
      areaId: customer ? String(customer.areaId) : "",
      customerTypeId: customer
        ? String(customer.customerTypeId)
        : String(types.data?.find((t) => t.name === "Household")?.id ?? ""),
      registeredOn: today,
      deliveryDay: String(defaultDay),
      cycleWeeks: String(customer?.cycleWeeks ?? 1),
      nextDeliveryDate: customer?.nextDeliveryDate ?? nextDeliveryAfter(today, defaultDay),
      usualQty: Object.fromEntries(Object.entries(customer?.usualQty ?? {}).map(([k, v]) => [k, String(v)])),
      payType: (customer?.payType as CustomerValues["payType"]) ?? "CASH",
      creditLimit: customer && customer.creditLimit > 0 ? String(customer.creditLimit) : "",
      termsDays: String(customer && customer.termsDays > 0 ? customer.termsDays : 30),
      agreedPrices: {},
      agreedReason: "",
      whatsappOptIn: customer?.whatsappOptIn ?? false,
      whatsappNumber: customer?.whatsappNumber ?? "",
      notes: customer?.notes ?? "",
    },
  });
  const [payType, typeId, next, day, agreedPrices] = useWatch({
    control,
    name: ["payType", "customerTypeId", "nextDeliveryDate", "deliveryDay", "agreedPrices"],
  });

  // A new customer starts as Household once the types have loaded (the prototype's default).
  const household = types.data?.find((t) => t.name === "Household" && t.active)?.id;
  useEffect(() => {
    if (isNew && household && !getValues("customerTypeId")) setValue("customerTypeId", String(household));
  }, [isNew, household, getValues, setValue]);

  /** Standard price of a bottle type for the chosen customer type (today). */
  function standard(code: string): number | null {
    const cell = matrix.data?.water.find(
      (c) => c.bottleTypeCode === code && String(c.customerTypeId) === typeId,
    );
    return cell?.current?.price ?? null;
  }

  /** First delivery = the next delivery day after the registration date (new) or after today (edit). */
  function autoNext(fromDate: string | null, dayValue: string) {
    if (fromDate)
      setValue("nextDeliveryDate", nextDeliveryAfter(fromDate, Number(dayValue)), { shouldValidate: false });
  }

  function send(v: CustomerValues, allowDuplicatePhone: boolean) {
    setError("");
    const body: CustomerRequest = {
      name: v.name,
      phone: v.phone,
      address: v.address,
      areaId: Number(v.areaId),
      customerTypeId: Number(v.customerTypeId),
      deliveryDay: Number(v.deliveryDay),
      cycleWeeks: Number(v.cycleWeeks),
      nextDeliveryDate: v.nextDeliveryDate as string,
      usualQty: Object.fromEntries(bottles.map((b) => [b.code, Number(v.usualQty[b.code] || 0)])),
      payType: v.payType,
      creditLimit: v.payType === "CASH" ? 0 : Number(v.creditLimit),
      termsDays: v.payType === "CASH" ? 0 : Number(v.termsDays),
      notes: v.notes,
      whatsappOptIn: v.whatsappOptIn,
      whatsappNumber: v.whatsappNumber,
      allowDuplicatePhone,
      ...(isNew
        ? {
            registeredOn: v.registeredOn as string,
            agreedPrices: Object.fromEntries(
              Object.entries(v.agreedPrices)
                .filter(([, p]) => p.trim() !== "")
                .map(([k, p]) => [k, Number(p)]),
            ),
            agreedReason: v.agreedReason,
          }
        : { version: customer.version }),
    };
    save.mutate(
      { id: customer?.id ?? null, body },
      {
        onSuccess: async (saved) => {
          setDupMessage(null);
          if (!isNew && status.value !== customer.status) {
            try {
              if (status.value === "INACTIVE") {
                await deactivate.mutateAsync({
                  id: customer.id,
                  body: {
                    date: status.date ?? today,
                    reason: statusReason(status.reason, status.details.trim()),
                  },
                });
              } else {
                await reactivate.mutateAsync(customer.id);
              }
            } catch (e) {
              setError(e instanceof ApiError ? e.message : "The status could not be changed.");
              return;
            }
          }
          toast(
            isNew
              ? `Customer ${saved.customer.code} added`
              : status.value !== customer.status
                ? `Customer updated – now ${status.value === "ACTIVE" ? "Active" : "Inactive"}`
                : "Customer updated",
            "ok",
          );
          onSaved?.(saved.customer);
          onClose();
        },
        onError: (err) => {
          if (err instanceof ApiError && err.code === "DUP_PHONE") {
            setDupMessage(err.message);
            return;
          }
          setError(showServerErrors(err, SERVER_FIELDS, setFieldError) ?? "");
        },
      },
    );
  }

  const submit = handleSubmit((v) => {
    if (!isNew && status.value === "INACTIVE" && customer.status !== "INACTIVE") {
      if (!status.reason || (status.reason === "Other" && !status.details.trim())) {
        setError('Select the reason (and enter details for "Other") to make the customer inactive.');
        return;
      }
      if (!status.date || status.date > today) {
        setError("Enter the inactive-from date (not in the future).");
        return;
      }
    }
    send(v, false);
  });

  const field = (name: (typeof SERVER_FIELDS)[number]) => ({
    id: `cu-${name}`,
    className: errors[name] ? "invalid" : undefined,
    ...register(name),
  });
  const err = (name: (typeof SERVER_FIELDS)[number]) =>
    errors[name] ? <span className="error">{errors[name]?.message}</span> : null;
  const typeName = types.data?.find((t) => String(t.id) === typeId)?.name ?? "";
  const nextWeekday = next ? isoWeekday(next) : null;

  return (
    <>
      <FormDialog
        open
        wide
        onOpenChange={(o) => !o && onClose()}
        title={isNew ? "New customer" : `Edit ${customer.name}`}
        onSubmit={() => void submit()}
        submitting={save.isPending || deactivate.isPending || reactivate.isPending}
      >
        {error && <div className="banner bad">{error}</div>}
        <div className="form-grid">
          <div className="field">
            <label htmlFor="cu-code">Customer code</label>
            <input id="cu-code" value={customer?.code ?? "Generated on save"} readOnly className="mono" />
          </div>
          <div className="field req span2">
            <label htmlFor="cu-name">Name</label>
            <input {...field("name")} autoFocus />
            {err("name")}
          </div>
          <div className="field req">
            <label htmlFor="cu-phone">Phone</label>
            <input {...field("phone")} placeholder="077 123 4567" />
            {err("phone")}
          </div>
          <div className="field req span2">
            <label htmlFor="cu-address">Address</label>
            <input {...field("address")} />
            {err("address")}
          </div>
          <div className="field req">
            <label htmlFor="cu-areaId">Area</label>
            <select {...field("areaId")}>
              <option value="">Select</option>
              {(areas.data ?? [])
                .filter((a) => a.active || a.id === customer?.areaId)
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name}
                  </option>
                ))}
            </select>
            {err("areaId")}
          </div>
          <div className="field req">
            <label htmlFor="cu-customerTypeId">Customer type</label>
            <select {...field("customerTypeId")}>
              <option value="">Select</option>
              {(types.data ?? [])
                .filter((t) => t.active || t.id === customer?.customerTypeId)
                .map((t) => (
                  <option key={t.id} value={t.id}>
                    {t.name}
                  </option>
                ))}
            </select>
            {err("customerTypeId")}
          </div>

          <div className="section-title full">Delivery</div>
          {isNew && (
            <div className="field req">
              <label htmlFor="cu-registeredOn">Registration date</label>
              <Controller
                control={control}
                name="registeredOn"
                render={({ field: f }) => (
                  <DateField
                    id="cu-registeredOn"
                    value={f.value}
                    onChange={(v) => {
                      f.onChange(v);
                      autoNext(v, day);
                    }}
                    invalid={!!errors.registeredOn}
                  />
                )}
              />
              {err("registeredOn")}
            </div>
          )}
          <div className="field req">
            <label htmlFor="cu-deliveryDay">Delivery day</label>
            <select
              {...register("deliveryDay", {
                onChange: (e) =>
                  autoNext(isNew ? (getValues("registeredOn") ?? today) : today, e.target.value),
              })}
              id="cu-deliveryDay"
            >
              {DAYS.map((d, i) => (
                <option key={d} value={i + 1}>
                  {d}
                </option>
              ))}
            </select>
          </div>
          <div className="field req">
            <label htmlFor="cu-cycleWeeks">Cycle</label>
            <select id="cu-cycleWeeks" {...register("cycleWeeks")}>
              {CYCLES.map((c) => (
                <option key={c.weeks} value={c.weeks}>
                  {c.label}
                </option>
              ))}
            </select>
          </div>
          <div className="field req">
            <label htmlFor="cu-nextDeliveryDate">Next delivery</label>
            <Controller
              control={control}
              name="nextDeliveryDate"
              render={({ field: f }) => (
                <DateField
                  id="cu-nextDeliveryDate"
                  value={f.value}
                  onChange={f.onChange}
                  invalid={!!errors.nextDeliveryDate}
                />
              )}
            />
            {errors.nextDeliveryDate ? (
              err("nextDeliveryDate")
            ) : (
              <span className="help">
                {nextWeekday && dayName(nextWeekday)}
                {nextWeekday && String(nextWeekday) !== day && (
                  <b style={{ color: "var(--bad)" }}> – not the delivery day</b>
                )}
              </span>
            )}
          </div>
          {bottles.map((b) => (
            <div className="field" key={b.code}>
              <label htmlFor={`cu-usual-${b.code}`}>Usual {b.litres}L per delivery</label>
              <input
                id={`cu-usual-${b.code}`}
                type="number"
                min="0"
                className="num"
                {...register(`usualQty.${b.code}`)}
              />
            </div>
          ))}

          <div className="section-title full">Water price</div>
          {isNew ? (
            <>
              {bottles.map((b) => {
                const std = standard(b.code);
                const ap = Number(agreedPrices?.[b.code] || 0);
                return (
                  <div className="field" key={b.code}>
                    <label htmlFor={`cu-ap-${b.code}`}>{b.litres}L agreed price (Rs.)</label>
                    <input
                      id={`cu-ap-${b.code}`}
                      type="number"
                      min="1"
                      step="0.5"
                      placeholder="standard"
                      disabled={!canPrices}
                      className={errors.agreedPrices?.[b.code] ? "invalid num" : "num"}
                      {...register(`agreedPrices.${b.code}`)}
                    />
                    <span className="help">
                      Standard {typeName}: {std != null ? formatMoney(std) : "–"}
                      {ap > 0 && std != null && ap !== std && (
                        <span style={{ color: ap < std ? "var(--warn)" : "inherit" }}>
                          {" "}
                          · {ap < std ? `${formatMoney(std - ap)} below` : `${formatMoney(ap - std)} above`}
                        </span>
                      )}
                    </span>
                  </div>
                );
              })}
              {Object.values(agreedPrices ?? {}).some((p) => (p ?? "").trim() !== "") && (
                <div className="field span2">
                  <label htmlFor="cu-agreedReason">Reason for the agreed price</label>
                  <input
                    {...field("agreedReason")}
                    placeholder="e.g. Volume price – about 3,500 bottles a month"
                  />
                  {err("agreedReason")}
                </div>
              )}
            </>
          ) : (
            <>
              <div className="field span2">
                <span className="label">Price now</span>
                <div style={{ padding: "6px 0" }}>
                  {customer.prices.map((p) => (
                    <div key={p.bottleTypeCode}>
                      {p.label}{" "}
                      {String(customer.customerTypeId) !== typeId && !p.agreed ? (
                        <>
                          {standard(p.bottleTypeCode) != null ? formatMoney(standard(p.bottleTypeCode)) : "–"}{" "}
                          <span className="small muted">({typeName} standard after saving)</span>
                        </>
                      ) : (
                        <PriceText price={p} />
                      )}
                    </div>
                  ))}
                </div>
              </div>
              {canPrices && (
                <div className="field" style={{ alignSelf: "end" }}>
                  <button type="button" className="btn" onClick={() => setPricesOpen(true)}>
                    Change prices…
                  </button>
                </div>
              )}
            </>
          )}

          <div className="section-title full">Payment</div>
          <div className="field req">
            <label htmlFor="cu-payType">Payment type</label>
            <select {...field("payType")}>
              {PAY_TYPES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>
          {payType !== "CASH" && (
            <>
              <div className="field req">
                <label htmlFor="cu-creditLimit">Credit limit (Rs.)</label>
                <input {...field("creditLimit")} type="number" min="0" step="500" />
                {err("creditLimit")}
              </div>
              <div className="field req">
                <label htmlFor="cu-termsDays">Payment terms</label>
                <select {...field("termsDays")}>
                  {CREDIT_TERMS.map((t) => (
                    <option key={t} value={t}>
                      {t} days
                    </option>
                  ))}
                </select>
                {err("termsDays")}
              </div>
            </>
          )}
          <label className="check" style={{ alignSelf: "end" }}>
            <input type="checkbox" {...register("whatsappOptIn")} /> Send WhatsApp messages
          </label>
          <div className="field">
            <label htmlFor="cu-whatsappNumber">WhatsApp number</label>
            <input {...field("whatsappNumber")} placeholder="same as phone (mobile)" />
            {err("whatsappNumber")}
          </div>
          <div className="field full">
            <label htmlFor="cu-notes">Notes</label>
            <input {...field("notes")} placeholder="e.g. call before delivery" />
            {err("notes")}
          </div>

          <div className="section-title full">Status</div>
          {isNew ? (
            <>
              <div className="field">
                <label htmlFor="cu-status">Status</label>
                <input id="cu-status" value="Active" readOnly />
              </div>
              <div className="field span2 small muted" style={{ alignSelf: "end" }}>
                New customers start as Active. Deactivate later from this pop-up or the list when they stop
                buying.
              </div>
            </>
          ) : (
            <>
              <div className="field req">
                <label htmlFor="cu-st">Status</label>
                <select
                  id="cu-st"
                  value={status.value}
                  onChange={(e) => setStatus({ ...status, value: e.target.value })}
                >
                  <option value="ACTIVE">Active</option>
                  <option value="INACTIVE">Inactive</option>
                </select>
              </div>
              {status.value === "INACTIVE" && customer.status === "ACTIVE" && (
                <>
                  <div className="field req">
                    <label htmlFor="cu-st-date">Inactive from</label>
                    <DateField
                      id="cu-st-date"
                      value={status.date}
                      onChange={(d) => setStatus({ ...status, date: d })}
                    />
                  </div>
                  <div className="field req">
                    <label htmlFor="cu-st-reason">Reason</label>
                    <select
                      id="cu-st-reason"
                      value={status.reason}
                      onChange={(e) => setStatus({ ...status, reason: e.target.value })}
                    >
                      <option value="">Select</option>
                      {DEACT_REASONS.map((r) => (
                        <option key={r}>{r}</option>
                      ))}
                    </select>
                  </div>
                  <div className="field span2">
                    <label htmlFor="cu-st-details">Details</label>
                    <input
                      id="cu-st-details"
                      value={status.details}
                      placeholder="e.g. Moved to Kandy"
                      onChange={(e) => setStatus({ ...status, details: e.target.value })}
                    />
                  </div>
                  <div className="full">
                    <OpenItems customer={customer} />
                  </div>
                </>
              )}
              {customer.status === "INACTIVE" && customer.statusReason && (
                <div className="field span2 small muted" style={{ alignSelf: "end" }}>
                  Inactive since {formatDate(customer.statusDate)} – {customer.statusReason}
                </div>
              )}
            </>
          )}
          <div className="full small muted">
            Standard water price for {typeName || "this type"}:{" "}
            {bottles
              .map((b) => {
                const s = standard(b.code);
                return `${b.litres}L ${s != null ? formatMoney(s) : "–"}`;
              })
              .join(", ")}
            . Leave the agreed price blank to use the standard price.
            {payType !== "CASH" &&
              " Credit customers cannot go over the credit limit without Admin approval."}
          </div>
        </div>
      </FormDialog>

      {dupMessage && (
        <ConfirmDialog
          open
          onOpenChange={(o) => !o && setDupMessage(null)}
          title="Phone already used"
          confirmLabel="Save anyway"
          busy={save.isPending}
          onConfirm={() => void handleSubmit((v) => send(v, true))()}
        >
          <p>{dupMessage}</p>
          <p>Save anyway?</p>
        </ConfirmDialog>
      )}
      {pricesOpen && customer && (
        <CustomerPriceDialog customer={customer} onClose={() => setPricesOpen(false)} />
      )}
    </>
  );
}
