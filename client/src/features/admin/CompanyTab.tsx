"use client";

/*
 * Company tab (M01): the details printed on bills, invoices, receipts and other documents, and the logo
 * (PNG or JPG, at most 1 MB) with a preview. Not in the prototype page – the prototype prints its placeholders.
 */
import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { ConfirmDialog } from "@/components/shared/ConfirmDialog";
import { ApiError } from "@/lib/api/client";
import type { CompanySettings } from "@/lib/api/types";
import { toast } from "@/lib/toast";
import { useCompany, useRemoveLogo, useSaveCompany, useUploadLogo } from "./api";
import { companySchema, logoProblem, type CompanyValues } from "./schemas";

type Output = z.output<typeof companySchema>;
const FIELDS = ["name", "address", "phone", "email", "regNo"] as const;

function toValues(c: CompanySettings): CompanyValues {
  return {
    name: c.name ?? "",
    address: c.address ?? "",
    phone: c.phone ?? "",
    email: c.email ?? "",
    regNo: c.regNo ?? "",
  };
}

export function CompanyTab() {
  const company = useCompany();
  const save = useSaveCompany();
  const upload = useUploadLogo();
  const removeLogo = useRemoveLogo();
  const fileRef = useRef<HTMLInputElement>(null);
  const [logoError, setLogoError] = useState("");
  const [confirmRemove, setConfirmRemove] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isDirty },
  } = useForm<CompanyValues, unknown, Output>({ resolver: zodResolver(companySchema) });

  const data = company.data;
  useEffect(() => {
    if (data) reset(toValues(data));
  }, [data, reset]);

  const onSubmit = handleSubmit((v) => {
    save.mutate(v, {
      onSuccess: (c) => {
        reset(toValues(c));
        toast("Company details saved", "ok");
      },
      onError: (err) => {
        const fields = err instanceof ApiError ? err.fieldErrors : {};
        let shown = false;
        for (const f of FIELDS) {
          if (fields[f]) {
            setError(f, { message: fields[f] });
            shown = true;
          }
        }
        if (!shown) toast(err.message, "bad");
      },
    });
  });

  function onFile(file: File | undefined) {
    if (fileRef.current) fileRef.current.value = "";
    if (!file) return;
    const problem = logoProblem(file);
    setLogoError(problem ?? "");
    if (problem) return;
    upload.mutate(file, {
      onSuccess: () => toast("Logo uploaded", "ok"),
      onError: (err) =>
        setLogoError(err instanceof ApiError ? (err.fieldErrors.file ?? err.message) : err.message),
    });
  }

  if (company.isError) return <div className="banner bad">{company.error.message}</div>;
  if (!data) return <div className="card muted">Loading…</div>;

  const field = (name: (typeof FIELDS)[number]) => ({
    id: `company-${name}`,
    className: errors[name] ? "invalid" : undefined,
    "aria-invalid": errors[name] ? true : undefined,
    ...register(name),
  });

  return (
    <div className="grid side">
      <div className="card">
        <h2>Company details</h2>
        <p className="small muted">
          Printed on bills, receipts, invoices, statements, purchase orders and quotations.
        </p>
        <form noValidate onSubmit={onSubmit}>
          <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
            <div className="field req full">
              <label htmlFor="company-name">Company name</label>
              <input {...field("name")} />
              {errors.name && <span className="error">{errors.name.message}</span>}
            </div>
            <div className="field full">
              <label htmlFor="company-address">Address</label>
              <input {...field("address")} />
              {errors.address && <span className="error">{errors.address.message}</span>}
            </div>
            <div className="field">
              <label htmlFor="company-phone">Phone</label>
              <input {...field("phone")} />
              {errors.phone && <span className="error">{errors.phone.message}</span>}
            </div>
            <div className="field">
              <label htmlFor="company-email">Email</label>
              <input {...field("email")} type="email" />
              {errors.email && <span className="error">{errors.email.message}</span>}
            </div>
            <div className="field">
              <label htmlFor="company-regNo">Registration no.</label>
              <input {...field("regNo")} />
              {errors.regNo && <span className="error">{errors.regNo.message}</span>}
            </div>
          </div>
          <div className="form-actions">
            <button
              className="btn"
              type="button"
              disabled={!isDirty || save.isPending}
              onClick={() => reset()}
            >
              Undo changes
            </button>
            <button className="btn primary" type="submit" disabled={!isDirty || save.isPending}>
              {save.isPending ? "Saving…" : "Save"}
            </button>
          </div>
        </form>
      </div>

      <div className="card">
        <h2>Logo</h2>
        <div
          style={{
            border: "1px dashed var(--border)",
            borderRadius: "var(--radius)",
            minHeight: 120,
            display: "grid",
            placeItems: "center",
            padding: 12,
            marginBottom: 10,
            background: "#fff",
          }}
        >
          {data.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element -- served by the API with the login cookie
            <img src={data.logoUrl} alt="Company logo" style={{ maxWidth: "100%", maxHeight: 160 }} />
          ) : (
            <span className="muted small">No logo yet</span>
          )}
        </div>
        {logoError && <div className="banner bad">{logoError}</div>}
        <input
          ref={fileRef}
          type="file"
          accept="image/png,image/jpeg"
          hidden
          aria-label="Logo file"
          onChange={(e) => onFile(e.target.files?.[0])}
        />
        <div className="form-actions" style={{ justifyContent: "flex-start", marginTop: 0 }}>
          <button
            className="btn primary"
            type="button"
            disabled={upload.isPending}
            onClick={() => fileRef.current?.click()}
          >
            {upload.isPending ? "Uploading…" : data.logoUrl ? "Change logo" : "Upload logo"}
          </button>
          {data.logoUrl && (
            <button
              className="btn"
              type="button"
              disabled={removeLogo.isPending}
              onClick={() => setConfirmRemove(true)}
            >
              Remove
            </button>
          )}
        </div>
        <p className="small muted">PNG or JPG, at most 1 MB. A wide logo prints best.</p>
      </div>

      <ConfirmDialog
        open={confirmRemove}
        onOpenChange={setConfirmRemove}
        title="Remove the logo?"
        confirmLabel="Remove"
        danger
        busy={removeLogo.isPending}
        onConfirm={() =>
          removeLogo.mutate(undefined, {
            onSuccess: () => {
              setConfirmRemove(false);
              toast("Logo removed", "ok");
            },
            onError: (err) => {
              setConfirmRemove(false);
              toast(err.message, "bad");
            },
          })
        }
      >
        <p>Printed documents will show the company name without a logo.</p>
      </ConfirmDialog>
    </div>
  );
}
