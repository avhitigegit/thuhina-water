"use client";

/*
 * Customer types & areas pop-up (FR-03, prototype "Customer types"): each list with its active customers,
 * Activate / Deactivate (a type in use cannot be deactivated), and add a new type or area.
 */
import { useState } from "react";
import { FormDialog } from "@/components/shared/FormDialog";
import { StatusBadge } from "@/components/shared/StatusBadge";
import { ApiError } from "@/lib/api/client";
import { toast } from "@/lib/toast";
import { useAreas, useCustomerTypes, useSaveArea, useSaveCustomerType } from "./api";

function errorText(e: unknown): string {
  return e instanceof ApiError ? (Object.values(e.fieldErrors)[0] ?? e.message) : "Something went wrong.";
}

export function TypesAreasDialog({ onClose }: { onClose: () => void }) {
  const types = useCustomerTypes();
  const areas = useAreas();
  const saveType = useSaveCustomerType();
  const saveArea = useSaveArea();
  const [typeName, setTypeName] = useState("");
  const [typeDesc, setTypeDesc] = useState("");
  const [areaName, setAreaName] = useState("");

  return (
    <FormDialog open wide onOpenChange={(o) => !o && onClose()} title="Customer types and areas">
      <h3>Customer types</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Type</th>
              <th>Description</th>
              <th className="num">Customers</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {(types.data ?? []).map((t) => (
              <tr key={t.id}>
                <td>
                  <b>{t.name}</b>
                </td>
                <td>{t.description}</td>
                <td className="num">{t.activeCustomers}</td>
                <td>
                  <StatusBadge status={t.active ? "Active" : "Inactive"} />
                </td>
                <td className="actions">
                  <button
                    className="btn sm"
                    type="button"
                    disabled={saveType.isPending}
                    onClick={() =>
                      saveType.mutate(
                        { id: t.id, body: { description: t.description ?? "", active: !t.active } },
                        {
                          onSuccess: () => toast(t.active ? "Type deactivated" : "Type activated", "ok"),
                          onError: (e) => toast(errorText(e), "bad"),
                        },
                      )
                    }
                  >
                    {t.active ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="form-grid" style={{ marginTop: 12, gridTemplateColumns: "1fr 2fr auto" }}>
        <div className="field">
          <label htmlFor="tp-name">New type</label>
          <input
            id="tp-name"
            value={typeName}
            placeholder="e.g. Hotel"
            onChange={(e) => setTypeName(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="tp-desc">Description</label>
          <input id="tp-desc" value={typeDesc} onChange={(e) => setTypeDesc(e.target.value)} />
        </div>
        <button
          className="btn"
          type="button"
          style={{ alignSelf: "end" }}
          disabled={saveType.isPending}
          onClick={() =>
            saveType.mutate(
              { id: null, body: { name: typeName.trim(), description: typeDesc.trim() } },
              {
                onSuccess: () => {
                  toast("Type added", "ok");
                  setTypeName("");
                  setTypeDesc("");
                },
                onError: (e) => toast(errorText(e), "bad"),
              },
            )
          }
        >
          Add
        </button>
      </div>
      <p className="small muted">Set prices for a new type on Bottles &amp; Products → Prices.</p>

      <h3 style={{ marginTop: 18 }}>Areas (delivery routes)</h3>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Area</th>
              <th className="num">Customers</th>
              <th>Status</th>
              <th></th>
            </tr>
          </thead>
          <tbody>
            {(areas.data ?? []).map((a) => (
              <tr key={a.id}>
                <td>
                  <b>{a.name}</b>
                </td>
                <td className="num">{a.activeCustomers}</td>
                <td>
                  <StatusBadge status={a.active ? "Active" : "Inactive"} />
                </td>
                <td className="actions">
                  <button
                    className="btn sm"
                    type="button"
                    disabled={saveArea.isPending}
                    onClick={() =>
                      saveArea.mutate(
                        { id: a.id, body: { name: a.name, active: !a.active } },
                        {
                          onSuccess: () => toast(a.active ? "Area deactivated" : "Area activated", "ok"),
                          onError: (e) => toast(errorText(e), "bad"),
                        },
                      )
                    }
                  >
                    {a.active ? "Deactivate" : "Activate"}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="form-grid" style={{ marginTop: 12, gridTemplateColumns: "3fr auto" }}>
        <div className="field">
          <label htmlFor="ar-name">New area</label>
          <input
            id="ar-name"
            value={areaName}
            placeholder="e.g. Homagama"
            onChange={(e) => setAreaName(e.target.value)}
          />
        </div>
        <button
          className="btn"
          type="button"
          style={{ alignSelf: "end" }}
          disabled={saveArea.isPending}
          onClick={() =>
            saveArea.mutate(
              { id: null, body: { name: areaName.trim() } },
              {
                onSuccess: () => {
                  toast("Area added", "ok");
                  setAreaName("");
                },
                onError: (e) => toast(errorText(e), "bad"),
              },
            )
          }
        >
          Add
        </button>
      </div>
    </FormDialog>
  );
}
