"use client";

/*
 * Other products tab (FR-02): product + code, selling price, cost, in stock (red when 3 or fewer), status, Edit.
 * The code is made on save; opening stock only when creating – later stock changes come from goods receipts,
 * sales and stock adjustments.
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
import type { Product } from "@/lib/api/types";
import { showServerErrors, toNumber } from "@/lib/forms";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { useCreateProduct, useProducts, useUpdateProduct } from "./api";
import { LOW_PRODUCT_STOCK, productSchema, type ProductValues } from "./schemas";

const FIELDS = ["name", "sellingPrice", "costPrice", "openingStock"] as const;

function ProductDialog({ product, onClose }: { product: Product | null; onClose: () => void }) {
  const create = useCreateProduct();
  const update = useUpdateProduct();
  const [error, setError] = useState("");
  const {
    register,
    handleSubmit,
    setError: setFieldError,
    formState: { errors },
  } = useForm<ProductValues, unknown, z.output<typeof productSchema>>({
    resolver: zodResolver(productSchema),
    defaultValues: product
      ? {
          name: product.name,
          sellingPrice: String(product.sellingPrice),
          costPrice: product.costPrice == null ? "" : String(product.costPrice),
          openingStock: "",
          active: product.active,
        }
      : { name: "", sellingPrice: "", costPrice: "", openingStock: "0", active: true },
  });

  const onError = (err: unknown) => setError(showServerErrors(err, FIELDS, setFieldError) ?? "");
  const submit = handleSubmit((v) => {
    setError("");
    const sellingPrice = Number(v.sellingPrice);
    const costPrice = toNumber(v.costPrice) ?? undefined;
    const done = () => {
      toast("Product saved", "ok");
      onClose();
    };
    if (product) {
      update.mutate(
        {
          id: product.id,
          body: { name: v.name, sellingPrice, costPrice, active: v.active, version: product.version },
        },
        { onSuccess: done, onError },
      );
    } else {
      create.mutate(
        {
          name: v.name,
          sellingPrice,
          costPrice,
          openingStock: toNumber(v.openingStock) ?? 0,
          active: v.active,
        },
        { onSuccess: done, onError },
      );
    }
  });

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title={product ? `Edit ${product.name}` : "New product"}
      onSubmit={() => void submit()}
      submitting={create.isPending || update.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field full">
          <label htmlFor="p-code">Product code</label>
          <input id="p-code" value={product ? product.code : "Generated on save"} readOnly className="mono" />
        </div>
        <div className="field req full">
          <label htmlFor="p-name">Product name</label>
          <input
            id="p-name"
            autoFocus
            className={errors.name ? "invalid" : undefined}
            {...register("name")}
          />
          {errors.name && <span className="error">{errors.name.message}</span>}
        </div>
        <div className="field req">
          <label htmlFor="p-price">Selling price (Rs.)</label>
          <input
            id="p-price"
            type="number"
            min="0"
            step="0.01"
            className={errors.sellingPrice ? "invalid num" : "num"}
            {...register("sellingPrice")}
          />
          {errors.sellingPrice && <span className="error">{errors.sellingPrice.message}</span>}
        </div>
        <div className="field">
          <label htmlFor="p-cost">Cost price (Rs.)</label>
          <input
            id="p-cost"
            type="number"
            min="0"
            step="0.01"
            className={errors.costPrice ? "invalid num" : "num"}
            {...register("costPrice")}
          />
          {errors.costPrice && <span className="error">{errors.costPrice.message}</span>}
        </div>
        {product ? (
          <div className="field">
            <label htmlFor="p-stock">In stock</label>
            <input id="p-stock" value={product.stockQty} readOnly className="num" />
            <span className="help">Changes through goods receipts, sales and stock adjustments.</span>
          </div>
        ) : (
          <div className="field">
            <label htmlFor="p-opening">Opening stock</label>
            <input
              id="p-opening"
              type="number"
              min="0"
              step="1"
              className={errors.openingStock ? "invalid num" : "num"}
              {...register("openingStock")}
            />
            {errors.openingStock && <span className="error">{errors.openingStock.message}</span>}
          </div>
        )}
        <label className="check" style={{ alignSelf: "end" }}>
          <input type="checkbox" {...register("active")} /> Active
        </label>
      </div>
    </FormDialog>
  );
}

export function ProductsTab() {
  const products = useProducts();
  const [editing, setEditing] = useState<Product | null | undefined>(undefined);

  const columns: Column<Product>[] = [
    {
      key: "product",
      header: "Product",
      cell: (p) => (
        <>
          <b>{p.name}</b> <span className="small muted mono">{p.code}</span>
        </>
      ),
    },
    {
      key: "price",
      header: "Selling price",
      align: "num",
      cell: (p) => <MoneyText value={p.sellingPrice} />,
    },
    {
      key: "cost",
      header: "Cost",
      align: "num",
      cell: (p) => (p.costPrice != null ? <MoneyText value={p.costPrice} /> : "–"),
    },
    {
      key: "stock",
      header: "In stock",
      align: "num",
      cell: (p) =>
        p.stockQty <= LOW_PRODUCT_STOCK ? <b style={{ color: "var(--bad)" }}>{p.stockQty}</b> : p.stockQty,
    },
    {
      key: "status",
      header: "Status",
      cell: (p) => <StatusBadge status={p.active ? "Active" : "Inactive"} />,
    },
    {
      key: "actions",
      header: "",
      className: "actions",
      cell: (p) => (
        <Can permission={P.MASTERDATA_EDIT}>
          <button className="btn sm" type="button" onClick={() => setEditing(p)}>
            Edit
          </button>
        </Can>
      ),
    },
  ];

  return (
    <div className="card">
      <div className="card-head">
        <h2>Other products</h2>
        <Can permission={P.MASTERDATA_EDIT}>
          <button className="btn primary" type="button" onClick={() => setEditing(null)}>
            + New product
          </button>
        </Can>
      </div>
      {products.isError ? (
        <div className="banner bad">{products.error.message}</div>
      ) : (
        <DataTable
          columns={columns}
          rows={products.data}
          loading={products.isPending}
          rowKey={(p) => p.id}
          rowClassName={(p) => (p.active ? undefined : "row-muted")}
          empty="No products yet."
        />
      )}
      <p className="small muted" style={{ marginTop: 8 }}>
        Stock changes through goods receipts, sales and stock adjustments.
      </p>
      {editing !== undefined && <ProductDialog product={editing} onClose={() => setEditing(undefined)} />}
    </div>
  );
}
