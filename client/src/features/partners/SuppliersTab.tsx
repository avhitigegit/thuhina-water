"use client";

/*
 * Suppliers tab (FR-14, prototype master-data/suppliers.html): supplier + code + address, contact, supplies,
 * payment terms, owed, status, Edit. Pop-up with the code "Generated on save", a terms drop-down and item chips to
 * tick (empty bottles and products). Admin full; Accountant view only (no New / Edit).
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { FormDialog } from "@/components/shared/FormDialog";
import { MoneyText } from "@/components/shared/MoneyText";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import { useBottleTypes, useProducts } from "@/features/masterdata/api";
import type { Supplier } from "@/lib/api/types";
import { showServerErrors } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useSaveSupplier, useSuppliers } from "./api";
import { supplierSchema, TERMS, type SupplierValues } from "./schemas";

const FIELDS = ["name", "contact", "phone", "email", "address", "termsDays", "items"] as const;

/** Items that can be ticked: active empty bottles and active products, plus any the supplier already has. */
function useItemChoices(supplier: Supplier | null) {
  const bottles = useBottleTypes();
  const products = useProducts();
  const choices: { code: string; name: string }[] = [
    ...(bottles.data ?? []).filter((b) => b.active).map((b) => ({ code: b.code, name: `Empty ${b.name}` })),
    ...(products.data ?? []).filter((p) => p.active).map((p) => ({ code: p.code, name: p.name })),
  ];
  for (const i of supplier?.items ?? []) {
    if (!choices.some((c) => c.code === i.code)) choices.push({ code: i.code, name: `${i.name} (inactive)` });
  }
  return { choices, loading: bottles.isPending || products.isPending };
}

function SupplierDialog({ supplier, onClose }: { supplier: Supplier | null; onClose: () => void }) {
  const save = useSaveSupplier();
  const { choices, loading } = useItemChoices(supplier);
  const [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<SupplierValues, unknown, z.output<typeof supplierSchema>>({
    resolver: zodResolver(supplierSchema),
    defaultValues: {
      name: supplier?.name ?? "",
      contact: supplier?.contact ?? "",
      phone: supplier?.phone ?? "",
      email: supplier?.email ?? "",
      address: supplier?.address ?? "",
      termsDays: String(supplier?.termsDays ?? 30),
      items: supplier?.items.map((i) => i.code) ?? [],
      active: supplier?.active ?? true,
    },
  });

  const submit = handleSubmit((v) => {
    setError("");
    save.mutate(
      {
        id: supplier?.id ?? null,
        body: { ...v, termsDays: Number(v.termsDays), version: supplier?.version },
      },
      {
        onSuccess: () => {
          toast("Supplier saved", "ok");
          onClose();
        },
        onError: (err) => setError(showServerErrors(err, FIELDS, setFieldError) ?? ""),
      },
    );
  });

  const text = (name: "name" | "contact" | "phone" | "email" | "address") => ({
    id: `sup-${name}`,
    className: errors[name] ? "invalid" : undefined,
    ...register(name),
  });

  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title={supplier ? `Edit ${supplier.name}` : "New supplier"}
      onSubmit={() => void submit()}
      submitting={save.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="sup-code">Supplier code</label>
          <input
            id="sup-code"
            value={supplier ? supplier.code : "Generated on save"}
            readOnly
            className="mono"
          />
        </div>
        <div className="field req span2">
          <label htmlFor="sup-name">Name</label>
          <input {...text("name")} autoFocus />
          {errors.name && <span className="error">{errors.name.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="sup-contact">Contact person</label>
          <input {...text("contact")} />
          {errors.contact && <span className="error">{errors.contact.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="sup-phone">Phone</label>
          <input {...text("phone")} />
          {errors.phone && <span className="error">{errors.phone.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="sup-email">Email</label>
          <input {...text("email")} type="email" />
          {errors.email && <span className="error">{errors.email.message}</span>}
        </div>
        <div className="field span2">
          <label htmlFor="sup-address">Address</label>
          <input {...text("address")} />
          {errors.address && <span className="error">{errors.address.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="sup-terms">Payment terms</label>
          <select id="sup-terms" {...register("termsDays")}>
            {TERMS.map((t) => (
              <option key={t.days} value={t.days}>
                {t.label}
              </option>
            ))}
          </select>
          {errors.termsDays && <span className="error">{errors.termsDays.message}</span>}
        </div>
        <div className="field req full">
          <span className="label" id="sup-items">
            Supplies (tick all that apply)
          </span>
          {loading ? (
            <span className="muted small">Loading items…</span>
          ) : (
            <div className="chips" role="group" aria-labelledby="sup-items">
              {choices.map((c) => (
                <label key={c.code}>
                  <input type="checkbox" value={c.code} {...register("items")} /> {c.name}
                </label>
              ))}
            </div>
          )}
          {errors.items && <span className="error">{errors.items.message}</span>}
        </div>
        {supplier && (
          <label className="check">
            <input type="checkbox" {...register("active")} /> Active
          </label>
        )}
      </div>
    </FormDialog>
  );
}

export function SuppliersTab() {
  const suppliers = useSuppliers();
  const [editing, setEditing] = useState<Supplier | null | undefined>(undefined);

  const columns: Column<Supplier>[] = [
    {
      key: "supplier",
      header: "Supplier",
      cell: (s) => (
        <>
          <b>{s.name}</b> <span className="small muted mono">{s.code}</span>
          {s.address && <div className="small muted">{s.address}</div>}
        </>
      ),
    },
    {
      key: "contact",
      header: "Contact",
      cell: (s) => (
        <>
          {s.contact}
          <div className="small muted">{[s.phone, s.email].filter(Boolean).join(" · ")}</div>
        </>
      ),
    },
    {
      key: "supplies",
      header: "Supplies",
      cell: (s) => <span className="small">{s.items.map((i) => i.name).join(", ")}</span>,
    },
    { key: "terms", header: "Payment terms", cell: (s) => s.termsLabel },
    {
      key: "owed",
      header: "Owed",
      align: "num",
      cell: (s) =>
        s.owed ? (
          <b>
            <MoneyText value={s.owed} />
          </b>
        ) : (
          "–"
        ),
    },
    {
      key: "status",
      header: "Status",
      cell: (s) => <StatusBadge status={s.active ? "Active" : "Inactive"} />,
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (s) => (
        <Can permission={P.PARTNERS_EDIT}>
          <button className="btn sm" type="button" onClick={() => setEditing(s)}>
            Edit
          </button>
        </Can>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Suppliers</h2>
        <Can permission={P.PARTNERS_EDIT}>
          <button className="btn primary" type="button" onClick={() => setEditing(null)}>
            + New supplier
          </button>
        </Can>
      </div>
      {suppliers.isError ? (
        <div className="banner bad">{suppliers.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={suppliers.data}
          loading={suppliers.isPending}
          rowKey={(s) => s.id}
          rowClassName={(s) => (s.active ? undefined : "row-muted")}
          empty="No suppliers yet."
        />
      )}
      {editing !== undefined && <SupplierDialog supplier={editing} onClose={() => setEditing(undefined)} />}
    </div>
  );
}
