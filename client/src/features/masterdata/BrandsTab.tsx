"use client";

/*
 * Accepted old bottles tab (FR-06, BR-02): brand, for bottle, taken in so far, note, status, Stop accepting /
 * Accept again; Add brand pop-up.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { FormDialog } from "@/components/shared/FormDialog";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import { ApiError } from "@/lib/api/client";
import type { OldBottleBrand } from "@/lib/api/types";
import { showServerErrors } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useBottleTypes, useCreateBrand, useOldBottleBrands, useUpdateBrand } from "./api";
import { brandSchema, type BrandValues } from "./schemas";

function AddBrandDialog({ onClose }: { onClose: () => void }) {
  const bottles = useBottleTypes();
  const create = useCreateBrand();
  const [error, setError] = useState("");
  const active = (bottles.data ?? []).filter((b) => b.active);
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<BrandValues, unknown, z.output<typeof brandSchema>>({
    resolver: zodResolver(brandSchema),
    defaultValues: { name: "", bottleTypeCode: "B20", note: "" },
  });

  const submit = handleSubmit((v) => {
    setError("");
    create.mutate(v, {
      onSuccess: () => {
        toast("Brand added", "ok");
        onClose();
      },
      onError: (err) =>
        setError(showServerErrors(err, ["name", "bottleTypeCode", "note"] as const, setFieldError) ?? ""),
    });
  });

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Add accepted brand"
      submitLabel="Add"
      onSubmit={() => void submit()}
      submitting={create.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req">
          <label htmlFor="br-name">Brand</label>
          <input
            id="br-name"
            autoFocus
            className={errors.name ? "invalid" : undefined}
            {...register("name")}
          />
          {errors.name && <span className="error">{errors.name.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="br-bottle">For bottle</label>
          <select
            id="br-bottle"
            className={errors.bottleTypeCode ? "invalid" : undefined}
            {...register("bottleTypeCode")}
          >
            {active.map((b) => (
              <option key={b.code} value={b.code}>
                {b.name}
              </option>
            ))}
          </select>
          {errors.bottleTypeCode && <span className="error">{errors.bottleTypeCode.message}</span>}
        </div>
        <div className="field full">
          <label htmlFor="br-note">Note</label>
          <input id="br-note" placeholder="e.g. no cracks" {...register("note")} />
          {errors.note && <span className="error">{errors.note.message}</span>}
        </div>
      </div>
    </FormDialog>
  );
}

export function BrandsTab() {
  const brands = useOldBottleBrands();
  const update = useUpdateBrand();
  const [adding, setAdding] = useState(false);

  function toggle(b: OldBottleBrand) {
    update.mutate(
      {
        id: b.id,
        body: { bottleTypeCode: b.bottleTypeCode, note: b.note ?? "", active: !b.active, version: b.version },
      },
      {
        onSuccess: (r) => toast(r.active ? `${r.name} accepted again` : `${r.name} no longer accepted`, "ok"),
        onError: (err) => toast(err instanceof ApiError ? err.message : "Something went wrong.", "bad"),
      },
    );
  }

  const columns: Column<OldBottleBrand>[] = [
    { key: "brand", header: "Brand", cell: (b) => <b>{b.name}</b> },
    { key: "for", header: "For", cell: (b) => b.bottleTypeName },
    {
      key: "taken",
      header: "Taken in so far",
      align: "num",
      cell: (b) =>
        b.takenIn ?? (
          <span className="muted" title="Comes with Sales (M06)">
            –
          </span>
        ),
    },
    { key: "note", header: "Note", cell: (b) => b.note ?? "" },
    {
      key: "status",
      header: "Status",
      cell: (b) => <StatusBadge status={b.active ? "Active" : "Inactive"} />,
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (b) => (
        <Can permission={P.MASTERDATA_EDIT}>
          <button className="btn sm" type="button" disabled={update.isPending} onClick={() => toggle(b)}>
            {b.active ? "Stop accepting" : "Accept again"}
          </button>
        </Can>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Accepted old bottles</h2>
        <Can permission={P.MASTERDATA_EDIT}>
          <button className="btn primary" type="button" onClick={() => setAdding(true)}>
            + Add brand
          </button>
        </Can>
      </div>
      <p className="small muted">
        An accepted old bottle handed in replaces the deposit for one bottle. It joins the company pool and is
        filled like any other.
      </p>
      {brands.isError ? (
        <div className="banner bad">{brands.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={brands.data}
          loading={brands.isPending}
          rowKey={(b) => b.id}
          rowClassName={(b) => (b.active ? undefined : "row-muted")}
          empty="No accepted brands."
        />
      )}
      {adding && <AddBrandDialog onClose={() => setAdding(false)} />}
    </div>
  );
}
