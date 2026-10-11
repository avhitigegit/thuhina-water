"use client";

/* Cells of the Enter Bills grid that talk to the server: the customer lookup and the paper bill duplicate check. */
import { useQuery } from "@tanstack/react-query";
import { useEffect, useId, useRef } from "react";
import { customerLabel, findMatch } from "@/features/customers/CustomerPicker";
import { usePaperBillCheck } from "@/features/sales/api";
import { api } from "@/lib/api/client";
import type { CustomerLookupRow } from "@/lib/api/types";
import { formatDate } from "@/lib/format";
import { useDebounced } from "@/lib/useDebounced";
import type { Row } from "./grid";

/**
 * Customer cell: code (17 / C0017), phone or name with a list of matches. When the text means one customer (or a
 * match is chosen), {@code onPick} loads that customer into the row.
 */
export function CustomerCell({
  row,
  index,
  disabled,
  missing,
  onText,
  onPick,
}: {
  row: Row;
  index: number;
  disabled: boolean;
  missing: boolean;
  onText: (text: string) => void;
  onPick: (match: CustomerLookupRow) => void;
}) {
  const listId = useId();
  const text = row.customerText;
  const search = useDebounced(row.customer ? "" : text.trim(), 250);
  const rows = useQuery({
    queryKey: ["customers", "lookup", search],
    queryFn: ({ signal }) => api<CustomerLookupRow[]>("/customers/lookup", { signal, query: { q: search } }),
    enabled: search !== "" && !disabled,
  });
  const options = rows.data ?? [];

  // The matches arrive after the typing stops: "17" or "C0017" then selects that customer (once per text).
  const picked = useRef("");
  const match = row.customer ? null : findMatch(text, options);
  useEffect(() => {
    if (match && picked.current !== text) {
      picked.current = text;
      onPick(match);
    }
  }, [match, text, onPick]);

  return (
    <>
      <input
        data-f="cust"
        className={missing ? "w-cust invalid" : "w-cust"}
        aria-label={`Customer row ${index + 1}`}
        list={listId}
        value={text}
        autoComplete="off"
        placeholder="Code / phone / name"
        disabled={disabled}
        onChange={(e) => onText(e.target.value)}
        onBlur={() => {
          // Leaving the field with one match left picks it ("Saman" → C0017 – Saman Perera – …).
          if (!row.customer && options.length === 1 && text.trim()) onPick(options[0]);
        }}
      />
      <datalist id={listId}>
        {options.map((r) => (
          <option key={r.id} value={customerLabel(r)}>
            {r.status === "ACTIVE" ? r.areaName : "Inactive"}
          </option>
        ))}
      </datalist>
    </>
  );
}

/**
 * Paper bill number checked against the database while typing (BR-13): reports "Paper bill 41275 already entered
 * (02/10/2026, Dilshan Fernando)" or null. Renders nothing.
 */
export function PaperCheck({
  rowKey,
  paper,
  onResult,
}: {
  rowKey: number;
  paper: string;
  onResult: (key: number, message: string | null) => void;
}) {
  const no = useDebounced(paper.trim(), 300);
  const check = usePaperBillCheck(no);
  const d = no ? check.data : undefined;
  const message =
    d && d.entered && d.paperBillNo.toUpperCase() === no.toUpperCase()
      ? `Paper bill ${d.paperBillNo} already entered (${d.txn ? `${formatDate(d.txn.date)}, ${d.txn.customerName}` : ""})`
      : null;
  useEffect(() => {
    onResult(rowKey, message);
  }, [rowKey, message, onResult]);
  return null;
}
