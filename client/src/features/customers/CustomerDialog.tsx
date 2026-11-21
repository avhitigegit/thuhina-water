"use client";

/*
 * New / Edit customer pop-up (FR-05, FR-08 – FR-10, FR-13, prototype master-data/customers.html "edit"):
 * numbered sections (redesign 21/11/2026): 1 Customer details (with WhatsApp and notes) · 2 Delivery (registration date,
 * day, cycle, next delivery – filled automatically with the next delivery day after the registration date, editable –
 * and the usual bottles) · 3 Water price (a new customer's boxes show the standard price of the type; a different price
 * is the agreed price; the price now when editing) · 4 Payment terms · 5 Bottles given on the registration date (new
 * customer, FR-08 + FR-32: filled bottles, old bottles handed in and money received in their own boxes, live calculation
 * from /customers/register/preview) · Status (Active or Inactive, also for a new customer). A phone already in use asks "Save anyway?"; over the credit limit asks for Admin approval. A new customer
 * and the first-purchase bills are saved together; the "Customer registered" pop-up lists the bills to print.
 * Register as customer from an accepted customer quotation (M16) opens it pre-filled (name, phone, address, area, type,
 * notes and the quoted water prices as agreed prices) and links the new customer to the quotation.
 */
import { PaidByFields, payFields, referenceProblem, type PayMethod } from "@/components/shared/PaidBy";
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useState, type ReactNode } from "react";
import { Controller, useForm, useWatch, type Resolver } from "react-hook-form";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useCan } from "@/features/auth/access";
import { useBottleTypes, useOldBottleBrands, usePriceMatrix } from "@/features/masterdata/api";
import { openBillPdf } from "@/features/sales/BillDialog";
import { CREDIT_LIMIT, CreditApprovalDialog } from "@/features/sales/CreditApprovalDialog";
import { ApiError } from "@/lib/api/client";
import type { CustomerDetail, CustomerRequest, CustomerSaved, RegistrationPreview } from "@/lib/api/types";
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
  useRegistrationPreview,
  useSaveCustomer,
} from "./api";
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
import {
  cashPaidProblem,
  customerSchema,
  inactiveProblem,
  MSG_FIX_FIELDS,
  statusReason,
  type CustomerValues,
} from "./schemas";
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

/** A new customer's details from an accepted customer quotation (M16). */
export interface CustomerPrefill {
  quotationId: number;
  name: string;
  phone: string;
  address: string;
  areaId: number | null;
  customerTypeId: number;
  notes: string;
  /** Quoted water prices that differ from the standard price, by bottle type. */
  agreedPrices: Record<string, number>;
  agreedReason: string;
  /** Usual quantity per delivery from the quoted water lines (N-09). */
  usualQty?: Record<string, number>;
}

/** Opens the pop-up for a new customer (`customerId` null) or loads the customer first. */
export function CustomerDialog({
  customerId,
  prefill,
  onClose,
  onSaved,
}: {
  customerId: number | null;
  prefill?: CustomerPrefill;
  onClose: () => void;
  onSaved?: (c: CustomerDetail) => void;
}) {
  const existing = useCustomer(customerId);
  if (customerId !== null && !existing.data) return null;
  return (
    <CustomerForm
      customer={existing.data ?? null}
      prefill={customerId === null ? prefill : undefined}
      onClose={onClose}
      onSaved={onSaved}
    />
  );
}

function CustomerForm({
  customer,
  prefill,
  onClose,
  onSaved,
}: {
  customer: CustomerDetail | null;
  prefill?: CustomerPrefill;
  onClose: () => void;
  onSaved?: (c: CustomerDetail) => void;
}) {
  const isNew = customer === null;
  const today = todayIso();
  const canPrices = useCan(P.CUSTOMER_PRICES_EDIT);
  // Bottles given on the registration date are sales (first purchases).
  const canSell = useCan(P.SALES_EDIT);
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
  const [creditError, setCreditError] = useState<ApiError | null>(null);
  const [registered, setRegistered] = useState<CustomerSaved | null>(null);
  // "Save anyway" for a duplicate phone is kept when the Admin then approves the credit limit.
  const [allowDup, setAllowDup] = useState(false);
  // Cash paid on the registration bills. Nothing is filled in: the user types what was really received.
  const [paidText, setPaidText] = useState<string | null>(null);
  const [paidError, setPaidError] = useState("");
  // How the money paid now was received (the same "Paid by" as every bill and payment).
  const [payMethod, setPayMethod] = useState<PayMethod>("CASH");
  const [payReference, setPayReference] = useState("");
  const [refError, setRefError] = useState("");
  // Old-bottle brand chosen when more than one is accepted for the size ("" = the first, N-03).
  const [oldBrand, setOldBrand] = useState("");
  const brands = useOldBottleBrands();
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
      name: customer?.name ?? prefill?.name ?? "",
      phone: customer?.phone ?? prefill?.phone ?? "",
      address: customer?.address ?? prefill?.address ?? "",
      areaId: customer ? String(customer.areaId) : prefill?.areaId ? String(prefill.areaId) : "",
      customerTypeId: customer
        ? String(customer.customerTypeId)
        : prefill
          ? String(prefill.customerTypeId)
          : String(types.data?.find((t) => t.name === "Household")?.id ?? ""),
      registeredOn: today,
      deliveryDay: String(defaultDay),
      cycleWeeks: String(customer?.cycleWeeks ?? 1),
      nextDeliveryDate: customer?.nextDeliveryDate ?? nextDeliveryAfter(today, defaultDay),
      usualQty: Object.fromEntries(
        Object.entries(customer?.usualQty ?? prefill?.usualQty ?? {}).map(([k, v]) => [k, String(v)]),
      ),
      payType: (customer?.payType as CustomerValues["payType"]) ?? "CASH",
      creditLimit: customer && customer.creditLimit > 0 ? String(customer.creditLimit) : "",
      termsDays: String(customer && customer.termsDays > 0 ? customer.termsDays : 30),
      agreedPrices: Object.fromEntries(
        Object.entries(prefill?.agreedPrices ?? {}).map(([k, v]) => [k, String(v)]),
      ),
      agreedReason: prefill?.agreedReason ?? "",
      bottlesGiven: {},
      oldBottles: "0",
      whatsappOptIn: customer?.whatsappOptIn ?? false,
      whatsappNumber: customer?.whatsappNumber ?? "",
      notes: customer?.notes ?? prefill?.notes ?? "",
    },
  });
  const [payType, typeId, next, day, agreedPrices] = useWatch({
    control,
    name: ["payType", "customerTypeId", "nextDeliveryDate", "deliveryDay", "agreedPrices"],
  });
  const [given, oldBottles, registeredOn, creditLimit, name] = useWatch({
    control,
    name: ["bottlesGiven", "oldBottles", "registeredOn", "creditLimit", "name"],
  });

  // Bottles given on the registration date start empty: the user types what was really given (a pre-filled number
  // made Cash paid required for bottles that were never given).
  const mainBottle = bottles[0]?.code;

  const whole = (v: string | undefined) => (/^\d+$/.test((v ?? "").trim()) ? Number(v) : 0);
  const givenNumbers = Object.fromEntries(bottles.map((b) => [b.code, whole(given?.[b.code])]));
  // An inactive customer gets no bottles, so the bottles section is left out.
  const inactiveNew = isNew && status.value === "INACTIVE";
  const anyGiven = canSell && !inactiveNew && Object.values(givenNumbers).some((q) => q > 0);
  const agreedNumbers = Object.fromEntries(
    Object.entries(agreedPrices ?? {})
      .filter(([, p]) => Number(p) > 0)
      .map(([k, p]) => [k, Number(p)]),
  );
  const regPreview = useRegistrationPreview(
    isNew && anyGiven && typeId && registeredOn
      ? ({
          name,
          customerTypeId: Number(typeId),
          registeredOn,
          payType,
          creditLimit: payType === "CASH" ? 0 : Number(creditLimit) || 0,
          agreedPrices: agreedNumbers,
          bottlesGiven: givenNumbers,
          oldBottles: whole(oldBottles),
          paid: paidText === null ? undefined : Number(paidText) || 0,
        } as unknown as CustomerRequest)
      : null,
  );
  const rp = anyGiven ? regPreview.data : undefined;
  const paidValue = paidText ?? "";
  const paidNumber = Number(paidValue) || 0;
  const oldBrands = (brands.data ?? []).filter((b) => b.active && b.bottleTypeCode === mainBottle);
  const oldBrandId = oldBrands.find((b) => String(b.id) === oldBrand)?.id ?? oldBrands[0]?.id;

  // A new customer starts as Household once the types have loaded (the prototype's default).
  const household = types.data?.find((t) => t.name === "Household" && t.active)?.id;
  useEffect(() => {
    if (isNew && household && !getValues("customerTypeId")) setValue("customerTypeId", String(household));
  }, [isNew, household, getValues, setValue]);

  // From a quotation: the type and area are chosen once their lists have loaded (the selects start empty).
  const typesLoaded = !!types.data;
  const areasLoaded = !!areas.data;
  useEffect(() => {
    if (prefill && typesLoaded) setValue("customerTypeId", String(prefill.customerTypeId));
  }, [prefill, typesLoaded, setValue]);
  useEffect(() => {
    if (prefill?.areaId && areasLoaded) setValue("areaId", String(prefill.areaId));
  }, [prefill, areasLoaded, setValue]);

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

  function send(v: CustomerValues, allowDuplicatePhone: boolean, approveOverLimit = false) {
    setError("");
    setAllowDup(allowDuplicatePhone);
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
            bottlesGiven:
              canSell && !inactiveNew
                ? Object.fromEntries(bottles.map((b) => [b.code, whole(v.bottlesGiven[b.code])]))
                : {},
            oldBottles: canSell && !inactiveNew ? whole(v.oldBottles) : 0,
            oldBrandId: canSell && !inactiveNew && whole(v.oldBottles) > 0 ? oldBrandId : undefined,
            paid: anyGiven ? paidNumber : 0,
            ...(anyGiven && paidNumber > 0
              ? { payMethod, payReference: payFields(payMethod, payReference).reference }
              : {}),
            approveOverLimit,
            fromQuotationId: prefill?.quotationId,
            ...(inactiveNew
              ? {
                  inactiveFrom: status.date as string,
                  inactiveReason: statusReason(status.reason, status.details.trim()),
                }
              : {}),
          }
        : { version: customer.version }),
    };
    save.mutate(
      { id: customer?.id ?? null, body },
      {
        onSuccess: async (saved) => {
          setDupMessage(null);
          setCreditError(null);
          if (isNew) {
            // "Customer registered" with the bills to print; the panel opens when it is closed.
            setRegistered(saved);
            return;
          }
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
            status.value !== customer.status
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
          if (err instanceof ApiError && err.code === CREDIT_LIMIT) {
            setDupMessage(null);
            setCreditError(err);
            return;
          }
          setCreditError(null);
          setError(showServerErrors(err, SERVER_FIELDS, setFieldError) ?? "");
        },
      },
    );
  }

  /** Brings the first field marked in red into view (the form is long; the message is at the top). */
  function showFirstProblem() {
    setTimeout(() => {
      const el = document.querySelector<HTMLElement>("[role=dialog] .invalid");
      el?.scrollIntoView?.({ block: "center" });
      el?.focus({ preventScroll: true });
    }, 0);
  }

  /** All fields are checked together, so one Save shows everything that is missing. */
  function submit() {
    const paidProblem = anyGiven
      ? cashPaidProblem(payType, paidValue, rp ? Number(rp.amount) : undefined)
      : "";
    setPaidError(paidProblem);
    const refProblem = anyGiven && paidNumber > 0 ? (referenceProblem(payMethod, payReference) ?? "") : "";
    setRefError(refProblem);
    const makingInactive = status.value === "INACTIVE" && customer?.status !== "INACTIVE";
    const statusProblem = makingInactive
      ? inactiveProblem(status, today, isNew ? getValues("registeredOn") : null)
      : "";
    void handleSubmit(
      (v) => {
        if (paidProblem || refProblem) {
          setError(MSG_FIX_FIELDS);
          showFirstProblem();
        } else if (statusProblem) setError(statusProblem);
        else send(v, false);
      },
      () => {
        setError(statusProblem || MSG_FIX_FIELDS);
        showFirstProblem();
      },
    )();
  }

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
        open={!registered}
        wide
        onOpenChange={(o) => !o && onClose()}
        title={isNew ? "New customer" : `Edit ${customer.name}`}
        onSubmit={submit}
        submitting={save.isPending || deactivate.isPending || reactivate.isPending || !!creditError}
      >
        {error && <div className="banner bad">{error}</div>}
        <div className="cu-form">
          {/* 1 ---------------------------------------------------------------- who */}
          <Section n={1} title="Customer details" hint="Who the customer is and how to reach them.">
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
              <div className="field">
                <label htmlFor="cu-whatsappNumber">WhatsApp number</label>
                <input {...field("whatsappNumber")} placeholder="same as phone (mobile)" />
                {err("whatsappNumber")}
              </div>
              <label className="check cu-check">
                <input type="checkbox" {...register("whatsappOptIn")} /> Send WhatsApp messages
              </label>
              <div className="field full">
                <label htmlFor="cu-notes">Notes</label>
                <input {...field("notes")} placeholder="e.g. call before delivery" />
                {err("notes")}
              </div>
            </div>
          </Section>

          {/* 2 ---------------------------------------------------------------- delivery */}
          <Section n={2} title="Delivery" hint="When we deliver and how many bottles each time.">
            <div className="form-grid">
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
            </div>
            <div className="cu-sub">Usual bottles per delivery</div>
            <div className="cu-tiles">
              {bottles.map((b) => (
                <div className="field cu-tile" key={b.code}>
                  <label htmlFor={`cu-usual-${b.code}`}>Usual {b.litres}L per delivery</label>
                  <input
                    id={`cu-usual-${b.code}`}
                    type="number"
                    min="0"
                    className={errors.usualQty?.[b.code] ? "invalid num" : "num"}
                    {...register(`usualQty.${b.code}`)}
                  />
                  {errors.usualQty?.[b.code] ? (
                    <span className="error">{errors.usualQty[b.code]?.message}</span>
                  ) : (
                    b.code === mainBottle && <span className="help">Needed for at least one bottle size</span>
                  )}
                </div>
              ))}
            </div>
          </Section>

          {/* 3 ---------------------------------------------------------------- water price */}
          <Section
            n={3}
            title="Water price"
            hint={
              isNew
                ? `The standard ${typeName || "type"} price is filled in. Change it only for an agreed price.`
                : "The price the customer pays now."
            }
          >
            {isNew ? (
              <>
                <div className="cu-tiles">
                  {bottles.map((b) => {
                    const std = standard(b.code);
                    const ap = Number(agreedPrices?.[b.code] || 0);
                    const agreed = ap > 0 && std != null && ap !== std;
                    return (
                      <div
                        className={agreed ? "field cu-tile cu-price agreed" : "field cu-tile cu-price"}
                        key={b.code}
                      >
                        <label htmlFor={`cu-ap-${b.code}`}>{b.litres}L water price (Rs.)</label>
                        <Controller
                          control={control}
                          name={`agreedPrices.${b.code}`}
                          defaultValue=""
                          render={({ field: f }) => (
                            <PriceBox
                              id={`cu-ap-${b.code}`}
                              value={f.value ?? ""}
                              standard={std}
                              disabled={!canPrices}
                              invalid={!!errors.agreedPrices?.[b.code]}
                              onChange={f.onChange}
                            />
                          )}
                        />
                        {errors.agreedPrices?.[b.code] && (
                          <span className="error">{errors.agreedPrices[b.code]?.message}</span>
                        )}
                        <span className="help">
                          {agreed ? (
                            <>
                              <span className="cu-tag agreed">Agreed</span> Standard {typeName}:{" "}
                              {formatMoney(std)}
                              <span style={{ color: ap < std ? "var(--warn)" : "inherit" }}>
                                {" "}
                                ·{" "}
                                {ap < std
                                  ? `${formatMoney(std - ap)} below`
                                  : `${formatMoney(ap - std)} above`}
                              </span>{" "}
                              <button
                                type="button"
                                className="link-btn"
                                onClick={() =>
                                  setValue(`agreedPrices.${b.code}`, "", { shouldValidate: true })
                                }
                              >
                                Use standard
                              </button>
                            </>
                          ) : (
                            <>
                              <span className="cu-tag">Standard</span> Standard {typeName}:{" "}
                              {std != null ? formatMoney(std) : "–"}
                            </>
                          )}
                        </span>
                      </div>
                    );
                  })}
                </div>
                {Object.values(agreedPrices ?? {}).some((p) => (p ?? "").trim() !== "") && (
                  <div className="form-grid" style={{ marginTop: 12 }}>
                    <div className="field full">
                      <label htmlFor="cu-agreedReason">Reason for the agreed price</label>
                      <input
                        {...field("agreedReason")}
                        placeholder="e.g. Volume price – about 3,500 bottles a month"
                      />
                      {err("agreedReason")}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <>
                <div className="cu-tiles">
                  {customer.prices.map((p) => (
                    <div className="cu-tile cu-price" key={p.bottleTypeCode}>
                      <span className="label">{p.label}</span>
                      <div className="cu-price-now">
                        {String(customer.customerTypeId) !== typeId && !p.agreed ? (
                          <>
                            {standard(p.bottleTypeCode) != null
                              ? formatMoney(standard(p.bottleTypeCode))
                              : "–"}{" "}
                            <span className="small muted">({typeName} standard after saving)</span>
                          </>
                        ) : (
                          <PriceText price={p} />
                        )}
                      </div>
                    </div>
                  ))}
                </div>
                <p className="small muted" style={{ margin: "8px 0 0" }}>
                  Agreed prices are changed with the Prices button on the customer&apos;s details.
                </p>
              </>
            )}
          </Section>

          {/* 4 ---------------------------------------------------------------- payment */}
          <Section n={4} title="Payment terms" hint="Cash on delivery, or credit with a limit.">
            <div className="form-grid">
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
                  <p className="full small muted" style={{ margin: 0 }}>
                    Credit customers cannot go over the credit limit without Admin approval.
                  </p>
                </>
              )}
            </div>
          </Section>

          {/* 5 ---------------------------------------------------------------- first delivery */}
          {inactiveNew && canSell && (
            <p className="small muted">An inactive customer gets no bottles on the registration date.</p>
          )}
          {isNew && canSell && !inactiveNew && (
            <Section
              n={5}
              title="Bottles given on the registration date"
              hint="Type what was really given today. Leave empty when nothing was given."
            >
              <div className="cu-boxes">
                <div className="cu-box">
                  <div className="cu-box-title">Filled bottles given</div>
                  <div className="cu-box-grid">
                    {bottles.map((b) => (
                      <div className="field" key={b.code}>
                        <label htmlFor={`cu-given-${b.code}`}>Filled {b.litres}L bottles given</label>
                        <input
                          id={`cu-given-${b.code}`}
                          type="number"
                          min="0"
                          className={errors.bottlesGiven?.[b.code] ? "invalid num" : "num"}
                          {...register(`bottlesGiven.${b.code}`)}
                        />
                        {errors.bottlesGiven?.[b.code] && (
                          <span className="error">{errors.bottlesGiven[b.code]?.message}</span>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
                <div className="cu-box">
                  <div className="cu-box-title">Old bottles handed in</div>
                  <div className="cu-box-grid">
                    {oldBrands.length > 1 && (
                      <div className="field">
                        <label htmlFor="cu-oldBrand">Old bottle brand</label>
                        <select
                          id="cu-oldBrand"
                          value={String(oldBrandId)}
                          onChange={(e) => setOldBrand(e.target.value)}
                        >
                          {oldBrands.map((b) => (
                            <option key={b.id} value={b.id}>
                              {b.name}
                            </option>
                          ))}
                        </select>
                      </div>
                    )}
                    <div className="field">
                      <label htmlFor="cu-oldBottles">
                        {oldBrands.length === 1
                          ? `Old ${oldBrands[0].name} bottles handed in`
                          : "Old bottles handed in"}
                      </label>
                      <input
                        id="cu-oldBottles"
                        type="number"
                        min="0"
                        className={errors.oldBottles ? "invalid num" : "num"}
                        {...register("oldBottles")}
                      />
                      {errors.oldBottles ? (
                        <span className="error">{errors.oldBottles.message}</span>
                      ) : (
                        <span className="help">No deposit is charged for these.</span>
                      )}
                    </div>
                  </div>
                </div>
                <div className="cu-box cu-box-pay">
                  <div className="cu-box-title">Money received now</div>
                  <div className="cu-box-grid">
                    <div className={payType === "CASH" && anyGiven ? "field req" : "field"}>
                      <label htmlFor="cu-paid">Paid now (Rs.)</label>
                      <input
                        id="cu-paid"
                        type="number"
                        min="0"
                        className={paidError ? "invalid num" : "num"}
                        value={paidValue}
                        disabled={!anyGiven}
                        onChange={(e) => {
                          setPaidText(e.target.value);
                          setPaidError("");
                        }}
                      />
                      {paidError && <span className="error">{paidError}</span>}
                    </div>
                    {anyGiven && paidNumber > 0 && (
                      <PaidByFields
                        id="cu-pay"
                        method={payMethod}
                        reference={payReference}
                        onMethod={setPayMethod}
                        onReference={(v) => {
                          setPayReference(v);
                          setRefError("");
                        }}
                        error={refError}
                      />
                    )}
                  </div>
                </div>
              </div>
              <div style={{ marginTop: 12 }}>
                <RegistrationSummary preview={rp} paid={paidNumber} anyGiven={anyGiven} />
              </div>
            </Section>
          )}

          {/* 6 ---------------------------------------------------------------- status */}
          <Section n={isNew && canSell && !inactiveNew ? 6 : 5} title="Status">
            <div className="form-grid">
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
              {status.value === "INACTIVE" && customer?.status !== "INACTIVE" && (
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
                  {customer && (
                    <div className="full">
                      <OpenItems customer={customer} />
                    </div>
                  )}
                </>
              )}
              {customer?.status === "INACTIVE" && customer.statusReason && (
                <div className="field span2 small muted" style={{ alignSelf: "end" }}>
                  Inactive since {formatDate(customer.statusDate)} – {customer.statusReason}
                </div>
              )}
            </div>
          </Section>
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
      {creditError && (
        <CreditApprovalDialog
          error={creditError}
          busy={save.isPending}
          onApprove={() => void handleSubmit((v) => send(v, allowDup, true))()}
          onClose={() => setCreditError(null)}
        />
      )}
      {registered && (
        <RegisteredDialog
          saved={registered}
          onClose={() => {
            onSaved?.(registered.customer);
            onClose();
          }}
        />
      )}
    </>
  );
}

/** One numbered part of the customer form. */
function Section({
  n,
  title,
  hint,
  children,
}: {
  n: number;
  title: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <section className="cu-sec">
      <div className="cu-sec-head">
        <span className="cu-num">{n}</span>
        <div>
          <h3>{title}</h3>
          {hint && <p>{hint}</p>}
        </div>
      </div>
      {children}
    </section>
  );
}

/**
 * The water price of one bottle size for a new customer: shows the standard price of the customer type; a different
 * price is the agreed price ({@code value} = the agreed price, "" = standard). While typing the box shows what is typed.
 */
function PriceBox({
  id,
  value,
  standard,
  disabled,
  invalid,
  onChange,
}: {
  id: string;
  value: string;
  standard: number | null;
  disabled: boolean;
  invalid: boolean;
  onChange: (v: string) => void;
}) {
  const [typing, setTyping] = useState<string | null>(null);
  const shown = typing ?? (value.trim() !== "" ? value : standard != null ? String(standard) : "");
  return (
    <input
      id={id}
      type="number"
      min="1"
      step="0.5"
      placeholder="standard"
      disabled={disabled}
      className={invalid ? "invalid num" : "num"}
      value={shown}
      onFocus={() => setTyping(shown)}
      onBlur={() => setTyping(null)}
      onChange={(e) => {
        const t = e.target.value;
        setTyping(t);
        // The standard price (or an empty box) is no agreed price.
        onChange(t.trim() === "" || (standard != null && Number(t) === standard) ? "" : t);
      }}
    />
  );
}

/**
 * Live summary of the bottles given (prototype firstCalc): "2 × 20L: water Rs. 700.00 + deposit 1 × Rs. 1,000.00
 * (1 old bottle – no deposit)", total, paid and what goes to the account; problems such as "Only 2 filled 20L
 * bottles in store." in red.
 */
function RegistrationSummary({
  preview,
  paid,
  anyGiven,
}: {
  preview: RegistrationPreview | undefined;
  paid: number;
  anyGiven: boolean;
}) {
  if (!anyGiven)
    return (
      <p className="small muted">
        No bottles given now – you can record the first purchase later from Sales &amp; Bills.
      </p>
    );
  if (!preview) return null;
  const amount = Number(preview.amount);
  return (
    <>
      {preview.problems.length > 0 && (
        <div className="banner bad" style={{ margin: "0 0 8px" }}>
          {preview.problems.map((p) => (
            <div key={p.message}>{p.message}</div>
          ))}
        </div>
      )}
      <div className="banner info" style={{ margin: 0 }} data-testid="registration-summary">
        {preview.lines.map((l) => (
          <div key={l.bottleTypeCode ?? l.label}>
            {l.filled} × {l.label}: water {formatMoney((l.unitPrice ?? 0) * l.filled)}
            {l.agreedPrice && " (agreed price)"} + deposit {l.depositCount} × {formatMoney(l.depositUnit)}
            {l.oldBottles > 0 && ` (${l.oldBottles} old bottle${l.oldBottles > 1 ? "s" : ""} – no deposit)`}
          </div>
        ))}
        <b>Total {formatMoney(amount)}</b> · paid {formatMoney(paid)}
        {amount - paid > 0 && ` · ${formatMoney(amount - paid)} to the account`}
        {preview.credit.exceeds && (
          <div style={{ color: "var(--bad)" }}>
            Over the credit limit ({formatMoney(preview.credit.limit)}) – Admin approval is needed.
          </div>
        )}
      </div>
    </>
  );
}

/** "Customer registered" (prototype): code, the bills of the bottles given (Print bill), next delivery. */
function RegisteredDialog({ saved, onClose }: { saved: CustomerSaved; onClose: () => void }) {
  const c = saved.customer;
  const bills = saved.bills ?? [];
  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Customer registered"
      extraButtons={
        bills.length > 0 && (
          <button type="button" className="btn" onClick={() => bills.forEach((b) => openBillPdf(b.id))}>
            Print bill
          </button>
        )
      }
    >
      <p>
        <b>{c.name}</b> saved as <b>{c.code}</b>.
      </p>
      {bills.length ? (
        <p>
          Bottles given:
          {bills.map((b) => (
            <span key={b.id}>
              <br />
              {b.lines.map((l) => `${l.filled} × ${l.label}`).join(", ")} – bill {b.billNo} (
              {formatMoney(b.amount)})
            </span>
          ))}
        </p>
      ) : (
        <p>No bottles given yet.</p>
      )}
      {c.status === "INACTIVE" ? (
        <p>
          Status: <b>Inactive</b> from {formatDate(c.statusDate)} – {c.statusReason}
        </p>
      ) : (
        <p>
          Next delivery:{" "}
          <b>
            {dayName(isoWeekday(c.nextDeliveryDate))} {formatDate(c.nextDeliveryDate)}
          </b>
        </p>
      )}
    </FormDialog>
  );
}
