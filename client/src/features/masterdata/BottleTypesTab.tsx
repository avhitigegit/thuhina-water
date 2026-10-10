"use client";

/*
 * Bottle types tab (FR-01, prototype master-data/bottles-products.html): code, name, size, deposit, in circulation,
 * status, Edit. New / Edit pop-up – the code is made from the size; the size cannot change after creation.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import type { z } from "zod";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { FormDialog } from "@/components/shared/FormDialog";
import { MoneyText } from "@/components/shared/MoneyText";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import type { BottleType } from "@/lib/api/types";
import { showServerErrors } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useBottleTypes, useCreateBottleType, useUpdateBottleType } from "./api";
import { bottleCodeFor, bottleTypeSchema, type BottleTypeValues } from "./schemas";

const FIELDS = ["name", "litres"] as const;

function BottleTypeDialog({
  bottle,
  onClose,
}: {
  /** null = new bottle type. */
  bottle: BottleType | null;
  onClose: () => void;
}) {
  const create = useCreateBottleType();
  const update = useUpdateBottleType();
  const [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    control,
    setError: setFieldError,
    formState: { errors },
  } = useForm<BottleTypeValues, unknown, z.output<typeof bottleTypeSchema>>({
    resolver: zodResolver(bottleTypeSchema),
    defaultValues: bottle
      ? { name: bottle.name, litres: String(bottle.litres), active: bottle.active }
      : { name: "", litres: "", active: true },
  });
  const litres = useWatch({ control, name: "litres" });
  const code = bottle ? bottle.code : (bottleCodeFor(litres ?? "") ?? "Generated from the size");

  const onError = (err: unknown) => setError(showServerErrors(err, FIELDS, setFieldError) ?? "");
  const submit = handleSubmit((v) => {
    setError("");
    if (bottle) {
      update.mutate(
        { code: bottle.code, body: { name: v.name, active: v.active, version: bottle.version } },
        {
          onSuccess: () => {
            toast("Bottle type saved", "ok");
            onClose();
          },
          onError,
        },
      );
    } else {
      create.mutate(
        { name: v.name, litres: Number(v.litres), active: v.active },
        {
          onSuccess: (b) => {
            toast(`Bottle type ${b.code} saved`, "ok");
            onClose();
          },
          onError,
        },
      );
    }
  });

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={bottle ? `Edit ${bottle.name}` : "New bottle type"}
      onSubmit={() => void submit()}
      submitting={create.isPending || update.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field">
          <label htmlFor="bt-code">Code</label>
          <input id="bt-code" value={code} readOnly className="mono" />
        </div>
        <div className="field req">
          <label htmlFor="bt-litres">Size (litres)</label>
          <input
            id="bt-litres"
            type="number"
            min="0"
            step="any"
            readOnly={!!bottle}
            autoFocus={!bottle}
            className={errors.litres ? "invalid" : undefined}
            {...register("litres")}
          />
          {errors.litres ? (
            <span className="error">{errors.litres.message}</span>
          ) : (
            bottle && <span className="help">The size cannot be changed.</span>
          )}
        </div>
        <div className="field req full">
          <label htmlFor="bt-name">Name</label>
          <input
            id="bt-name"
            placeholder="e.g. 19L Bottle"
            autoFocus={!!bottle}
            className={errors.name ? "invalid" : undefined}
            {...register("name")}
          />
          {errors.name && <span className="error">{errors.name.message}</span>}
        </div>
        <label className="check">
          <input type="checkbox" {...register("active")} /> Active
        </label>
      </div>
    </FormDialog>
  );
}

export function BottleTypesTab() {
  const bottles = useBottleTypes();
  // undefined = closed, null = new, otherwise the bottle being edited.
  const [editing, setEditing] = useState<BottleType | null | undefined>(undefined);

  const columns: Column<BottleType>[] = [
    { key: "code", header: "Code", cell: (b) => <b className="mono">{b.code}</b> },
    { key: "name", header: "Name", cell: (b) => b.name },
    { key: "size", header: "Size", align: "num", cell: (b) => `${b.litres} L` },
    {
      key: "deposit",
      header: "Deposit",
      align: "num",
      cell: (b) => (b.deposit != null ? <MoneyText value={b.deposit} /> : "–"),
    },
    {
      key: "circulation",
      header: "In circulation",
      align: "num",
      cell: (b) =>
        b.inCirculation ?? (
          <span className="muted" title="Comes with Stock (M04)">
            –
          </span>
        ),
    },
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
          <button className="btn sm" type="button" onClick={() => setEditing(b)}>
            Edit
          </button>
        </Can>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Bottle types</h2>
        <Can permission={P.MASTERDATA_EDIT}>
          <button className="btn primary" type="button" onClick={() => setEditing(null)}>
            + New bottle type
          </button>
        </Can>
      </div>
      {bottles.isError ? (
        <div className="banner bad">{bottles.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={bottles.data}
          loading={bottles.isPending}
          rowKey={(b) => b.code}
          rowClassName={(b) => (b.active ? undefined : "row-muted")}
          empty="No bottle types yet."
        />
      )}
      <p className="small muted" style={{ marginTop: 8 }}>
        Inactive types are hidden from sales and purchasing.
      </p>
      {editing !== undefined && <BottleTypeDialog bottle={editing} onClose={() => setEditing(undefined)} />}
    </div>
  );
}
