"use client";

/*
 * Filling factories tab (FR-22, BR-11, prototype master-data/suppliers.html): factory + code, contact, charge per bottle
 * ("20L Rs. 60.00 · 10L Rs. 35.00"), terms, at factory now, owed, status, Edit. Pop-up with a charge for every
 * active bottle type – a new charge applies to batches sent after saving. Admin full; Accountant view only.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, type Resolver } from "react-hook-form";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { FormDialog } from "@/components/shared/FormDialog";
import { MoneyText } from "@/components/shared/MoneyText";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import { useBottleTypes } from "@/features/masterdata/api";
import type { BottleType, Factory } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import { showServerErrors } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useFactories, useSaveFactory } from "./api";
import { factorySchema, TERMS, type FactoryValues } from "./schemas";

const TEXT_FIELDS = ["name", "address", "licence", "contact", "phone", "email"] as const;

function sizeLabel(b: BottleType): string {
  return `${b.litres}L`;
}

function FactoryDialog({
  factory,
  bottles,
  onClose,
}: {
  factory: Factory | null;
  /** Active bottle types – one charge each. */
  bottles: BottleType[];
  onClose: () => void;
}) {
  const save = useSaveFactory();
  const [error, setError] = useState("");
  const schema = factorySchema(bottles.map((b) => ({ code: b.code, label: sizeLabel(b) })));
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<FactoryValues>({
    resolver: zodResolver(schema) as unknown as Resolver<FactoryValues>,
    defaultValues: {
      name: factory?.name ?? "",
      address: factory?.address ?? "",
      licence: factory?.licence ?? "",
      contact: factory?.contact ?? "",
      phone: factory?.phone ?? "",
      email: factory?.email ?? "",
      termsDays: String(factory?.termsDays ?? 30),
      charges: Object.fromEntries(
        bottles.map((b) => {
          const c = factory?.charges.find((x) => x.bottleTypeCode === b.code);
          return [b.code, c ? String(c.charge) : ""];
        }),
      ),
      active: factory?.active ?? true,
    },
  });

  // Server field names, e.g. "name", "termsDays", "charges.B20" – the same paths as the form fields.
  const serverFields: string[] = [...TEXT_FIELDS, "termsDays", ...bottles.map((b) => `charges.${b.code}`)];

  const submit = handleSubmit((v) => {
    setError("");
    save.mutate(
      {
        id: factory?.id ?? null,
        body: {
          ...v,
          termsDays: Number(v.termsDays),
          charges: Object.fromEntries(Object.entries(v.charges).map(([k, c]) => [k, Number(c)])),
          version: factory?.version,
        },
      },
      {
        onSuccess: () => {
          toast("Factory saved", "ok");
          onClose();
        },
        onError: (err) =>
          setError(
            showServerErrors(err, serverFields, (f, e) =>
              setFieldError(f as Parameters<typeof setFieldError>[0], e),
            ) ?? "",
          ),
      },
    );
  });

  const text = (name: (typeof TEXT_FIELDS)[number]) => ({
    id: `fac-${name}`,
    className: errors[name] ? "invalid" : undefined,
    ...register(name),
  });

  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title={factory ? `Edit ${factory.name}` : "New filling factory"}
      onSubmit={() => void submit()}
      submitting={save.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="fac-code">Factory code</label>
          <input
            id="fac-code"
            value={factory ? factory.code : "Generated on save"}
            readOnly
            className="mono"
          />
        </div>
        <div className="field req span2">
          <label htmlFor="fac-name">Name</label>
          <input {...text("name")} autoFocus />
          {errors.name && <span className="error">{errors.name.message}</span>}
        </div>
        <div className="field span2">
          <label htmlFor="fac-address">Address</label>
          <input {...text("address")} />
          {errors.address && <span className="error">{errors.address.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="fac-licence">Licence</label>
          <input {...text("licence")} />
          {errors.licence && <span className="error">{errors.licence.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="fac-contact">Contact person</label>
          <input {...text("contact")} />
          {errors.contact && <span className="error">{errors.contact.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="fac-phone">Phone</label>
          <input {...text("phone")} />
          {errors.phone && <span className="error">{errors.phone.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="fac-email">Email</label>
          <input {...text("email")} type="email" />
          {errors.email && <span className="error">{errors.email.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="fac-terms">Payment terms</label>
          <select id="fac-terms" {...register("termsDays")}>
            {TERMS.map((t) => (
              <option key={t.days} value={t.days}>
                {t.label}
              </option>
            ))}
          </select>
          {errors.termsDays && <span className="error">{errors.termsDays.message}</span>}
        </div>
        {bottles.map((b) => {
          const err = errors.charges?.[b.code];
          return (
            <div className="field req" key={b.code}>
              <label htmlFor={`fac-charge-${b.code}`}>Charge per {sizeLabel(b)} bottle (Rs.)</label>
              <input
                id={`fac-charge-${b.code}`}
                type="number"
                min="0"
                step="0.01"
                className={err ? "invalid num" : "num"}
                {...register(`charges.${b.code}`)}
              />
              {err && <span className="error">{err.message}</span>}
            </div>
          );
        })}
        {factory && (
          <label className="check">
            <input type="checkbox" {...register("active")} /> Active
          </label>
        )}
      </div>
      <p className="small muted">A new charge applies to batches sent after saving.</p>
    </FormDialog>
  );
}

export function FactoriesTab() {
  const factories = useFactories();
  const bottles = useBottleTypes();
  const active = (bottles.data ?? []).filter((b) => b.active);
  const [editing, setEditing] = useState<Factory | null | undefined>(undefined);

  const columns: Column<Factory>[] = [
    {
      key: "factory",
      header: "Factory",
      cell: (f) => (
        <>
          <b>{f.name}</b> <span className="small muted mono">{f.code}</span>
          {f.address && <div className="small muted">{f.address}</div>}
        </>
      ),
    },
    {
      key: "contact",
      header: "Contact",
      cell: (f) => (
        <>
          {f.contact}
          <div className="small muted">{f.phone}</div>
        </>
      ),
    },
    {
      key: "charges",
      header: "Charge per bottle",
      cell: (f) =>
        active
          .map((b) => {
            const c = f.charges.find((x) => x.bottleTypeCode === b.code);
            return `${sizeLabel(b)} ${c ? formatMoney(c.charge) : "–"}`;
          })
          .join(" · "),
    },
    { key: "terms", header: "Payment terms", cell: (f) => f.termsLabel },
    {
      key: "atFactory",
      header: "At factory now",
      align: "num",
      cell: (f) =>
        f.atFactory ?? (
          <span className="muted" title="Comes with Filling Factory (M10)">
            –
          </span>
        ),
    },
    {
      key: "owed",
      header: "Owed",
      align: "num",
      cell: (f) =>
        f.owed ? (
          <b>
            <MoneyText value={f.owed} />
          </b>
        ) : (
          "–"
        ),
    },
    {
      key: "status",
      header: "Status",
      cell: (f) => <StatusBadge status={f.active ? "Active" : "Inactive"} />,
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (f) => (
        <Can permission={P.PARTNERS_EDIT}>
          <button className="btn sm" type="button" disabled={bottles.isPending} onClick={() => setEditing(f)}>
            Edit
          </button>
        </Can>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Filling factories</h2>
        <Can permission={P.PARTNERS_EDIT}>
          <button
            className="btn primary"
            type="button"
            disabled={bottles.isPending}
            onClick={() => setEditing(null)}
          >
            + New factory
          </button>
        </Can>
      </div>
      <p className="small muted">
        Each factory charges a fixed price per bottle filled (not per litre). Rejected bottles are not
        charged.
      </p>
      {factories.isError ? (
        <div className="banner bad">{factories.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={factories.data}
          loading={factories.isPending}
          rowKey={(f) => f.id}
          rowClassName={(f) => (f.active ? undefined : "row-muted")}
          empty="No filling factories yet."
        />
      )}
      {editing !== undefined && (
        <FactoryDialog factory={editing} bottles={active} onClose={() => setEditing(undefined)} />
      )}
    </div>
  );
}
