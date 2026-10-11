"use client";

/*
 * Customer picker (prototype UI.custDatalist / findCustomer): one text field with a list of matches
 * "C0012 – Name – phone". Type a code (C0012 or 12), phone, or part of the name; choosing a match selects it.
 * The server lookup returns at most 10 matches.
 */
import { useQuery } from "@tanstack/react-query";
import { useEffect, useId, useState } from "react";
import { api } from "@/lib/api/client";
import type { CustomerLookupRow } from "@/lib/api/types";
import { useDebounced } from "@/lib/useDebounced";

export function customerLabel(c: Pick<CustomerLookupRow, "code" | "name" | "phone">): string {
  return `${c.code} – ${c.name} – ${c.phone}`;
}

/** The match the text means: the full label, the code (C0012 / 12) or the phone digits (9 or more). */
export function findMatch(text: string, rows: CustomerLookupRow[]): CustomerLookupRow | null {
  const t = text.trim();
  if (!t) return null;
  const exact = rows.find((r) => customerLabel(r) === t);
  if (exact) return exact;
  const code = /^C?(\d{1,4})$/i.exec(t);
  if (code) return rows.find((r) => r.code === "C" + code[1].padStart(4, "0")) ?? null;
  const digits = t.replace(/\D/g, "");
  if (digits.length >= 9 && /^[\d\s-]+$/.test(t))
    return rows.find((r) => r.phone.replace(/\D/g, "") === digits) ?? null;
  return null;
}

export function CustomerPicker({
  id,
  value,
  onChange,
  placeholder = "Code, name or phone",
  invalid,
  autoFocus,
}: {
  id: string;
  value: CustomerLookupRow | null;
  onChange: (c: CustomerLookupRow | null) => void;
  placeholder?: string;
  invalid?: boolean;
  autoFocus?: boolean;
}) {
  const listId = useId();
  const [text, setText] = useState(value ? customerLabel(value) : "");
  // A customer chosen from outside (e.g. ?cust=C0012) replaces the text.
  const [shown, setShown] = useState(value);
  if (value !== shown) {
    setShown(value);
    if (value) setText(customerLabel(value));
  }
  const search = useDebounced(value && text === customerLabel(value) ? "" : text.trim(), 250);
  const rows = useQuery({
    queryKey: ["customers", "lookup", search],
    queryFn: ({ signal }) => api<CustomerLookupRow[]>("/customers/lookup", { signal, query: { q: search } }),
    enabled: search !== "",
  });
  const options = rows.data ?? [];

  // The matches arrive after the typing stops: "C0012" or "12" then selects that customer.
  const late = value ? null : findMatch(text, rows.data ?? []);
  useEffect(() => {
    if (late) onChange(late);
  }, [late, onChange]);

  function change(next: string) {
    setText(next);
    const match = findMatch(next, options);
    if (match?.id !== value?.id) {
      setShown(match);
      onChange(match);
    }
  }

  return (
    <>
      <input
        id={id}
        list={listId}
        value={text}
        autoComplete="off"
        autoFocus={autoFocus}
        placeholder={placeholder}
        className={invalid ? "invalid" : undefined}
        onChange={(e) => change(e.target.value)}
        onBlur={() => {
          // Leaving the field with one match left picks it ("Saman" → C0017 – Saman Perera – …).
          if (!value && options.length === 1 && text.trim()) change(customerLabel(options[0]));
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
