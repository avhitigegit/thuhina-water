"use client";

/*
 * Enter Bills (M07, prototype sales/enter-bills.html, FR-34 – FR-48, NFR-04): the keyboard grid where the Admin types
 * the day's paper bills. Since M17 the delivery staff enter most visits on the phone, so the grid starts with one empty
 * row; the info line shows who is entered on the phone / in the office / not yet, and "Fill in the N not entered" lists
 * the customers still without a bill with their usual quantities (paper-bill days). A row for a customer who already has
 * a bill that day warns. Rows typed by hand are kept when the date changes. Each row shows its live result (TypeScript copy of ExchangeRules); "Save bills"
 * posts each row on its own – good rows are saved and locked, bad rows show the server's message.
 */
import { useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import {
  Fragment,
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent,
  type ReactNode,
} from "react";
import { PageActions } from "@/components/layout/PageActions";
import { DateField } from "@/components/shared/DateField";
import { useCan } from "@/features/auth/access";
import { useCustomer } from "@/features/customers/api";
import { findMatch } from "@/features/customers/CustomerPicker";
import { useBottleTypes } from "@/features/masterdata/api";
import { invalidateAfterSale } from "@/features/sales/api";
import { CustomerCard } from "@/features/sales/CustomerCard";
import { api, ApiError } from "@/lib/api/client";
import type { BottlesAfter, CustomerLookupRow, DueList, EnteredBill } from "@/lib/api/types";
import { autoSlashDate, formatDate, formatMoney, parseDmy, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { fetchCustomer, fetchDueList, postBill, useNextPaperBill } from "./api";
import { exceedsCredit } from "./exchangeRules";
import {
  billRequest,
  bottleCols,
  customerText,
  emptyRow,
  fromDetail,
  freePaper,
  fromDue,
  paidNumber,
  rowFor,
  rowView,
  savedText,
  shownPaid,
  suggestPaper,
  totals,
  usualQty,
  type BillKind,
  type BottleCol,
  type GridCustomer,
  type Row,
  type RowView,
} from "./grid";
import { CustomerCell, PaperCheck } from "./cells";

const NOT_DELETABLE = "Saved bills cannot be deleted. Use Sales & Bills → Reverse.";

type Info = { kind: "day"; list: DueList; listed: number } | { kind: "customer"; name: string } | null;

/** "Kasun Silva 5, Ruwan Jayasena 3" – who saved the phone bills. */
function byWho(bills: EnteredBill[]): string {
  const counts = new Map<string, number>();
  bills.forEach((b) => counts.set(b.enteredBy, (counts.get(b.enteredBy) ?? 0) + 1));
  return [...counts].map(([who, n]) => `${who} ${n}`).join(", ");
}

function DayInfo({ info, waiting, onFill }: { info: Info; waiting: number; onFill: () => void }) {
  if (!info) return null;
  if (info.kind === "customer")
    return (
      <>
        Entering a bill for <b>{info.name}</b>. Change the bill date to list all customers due that day.
      </>
    );
  const { list } = info;
  const entered = list.items.filter((x) => x.entered);
  const phoneBills = entered.flatMap((x) => x.enteredBills.filter((b) => b.onPhone));
  const onPhone = entered.filter((x) => x.enteredBills.some((b) => b.onPhone));
  const inOffice = entered.filter((x) => !x.enteredBills.some((b) => b.onPhone));
  const notEntered = list.items.filter((x) => x.listed);
  const none = list.items.filter((x) => !x.entered && x.noBottles);
  const late = list.items.filter((x) => x.overdue && !x.entered);
  const names = (xs: typeof entered) => xs.map((x) => x.name).join(", ");
  return (
    <>
      <b>
        {list.dayName} {formatDate(list.date)}:
      </b>{" "}
      {list.items.length} customer(s) due
      {onPhone.length > 0 && (
        <>
          {" · "}
          <b>{onPhone.length} entered on the phone</b> ({byWho(phoneBills)})
        </>
      )}
      {inOffice.length > 0 && ` · ${inOffice.length} entered in the office (${names(inOffice)})`}
      {notEntered.length > 0 && (
        <>
          {" · "}
          <b>{notEntered.length} not entered yet</b> ({names(notEntered)})
        </>
      )}
      {none.length > 0 &&
        ` · ${none.length} new customer(s) without bottles – use First purchase: ${names(none)}`}
      {late.length > 0 && ` · ${late.length} missed an earlier delivery (${names(late)})`}
      {list.items.length === 0 && " – nobody is due. You can still add rows by hand."}
      <div className="small" style={{ marginTop: 4 }}>
        The delivery staff enter their visits on the phone (My Deliveries). Type only the paper bills here.
        {waiting > 0 && (
          <>
            {" "}
            <button type="button" className="btn sm" onClick={onFill} data-testid="fill-not-entered">
              Fill in the {waiting} not entered (paper bills)
            </button>
          </>
        )}
      </div>
    </>
  );
}

function ResultCell({ row, view, already }: { row: Row; view: RowView | null; already: string | null }) {
  if (row.status === "saved" && row.result) {
    const [head, ...rest] = savedText(row.result).split(" · ");
    return (
      <>
        <span className="ok">{head}</span>
        <br />
        <span className="muted">{rest.join(" · ")}</span>
      </>
    );
  }
  if (row.status === "saving") return <span className="muted">Saving…</span>;
  const lines: ReactNode[] = [];
  if (already) lines.push(<span className="warn">{already}</span>);
  if (row.status === "failed" && row.error) lines.push(<span className="err">{row.error.message}</span>);
  view?.msgs.forEach((m) =>
    lines.push(
      m.kind === "amount" ? (
        <b>{m.text}</b>
      ) : (
        <span className={m.kind === "muted" ? "muted" : m.kind}>{m.text}</span>
      ),
    ),
  );
  return (
    <>
      {lines.map((l, i) => (
        <Fragment key={i}>
          {i > 0 && <br />}
          {l}
        </Fragment>
      ))}
    </>
  );
}

export function EnterBills() {
  const qc = useQueryClient();
  const params = useSearchParams();
  const canApprove = useCan(P.SALES_APPROVE_OVER_LIMIT);
  const today = todayIso();
  const bottles = useBottleTypes();
  const cols = useMemo<BottleCol[]>(() => bottleCols(bottles.data ?? []), [bottles.data]);
  const nextPaper = useNextPaperBill();

  const [billDate, setBillDate] = useState<string>(today);
  const [rows, setRows] = useState<Row[]>([]);
  // The latest rows, also between renders (Save all runs several posts in a row): every change goes through commit.
  const rowsRef = useRef<Row[]>([]);
  const commit = useCallback((change: (rs: Row[]) => Row[]) => {
    rowsRef.current = change(rowsRef.current);
    setRows(rowsRef.current);
  }, []);
  const [info, setInfo] = useState<Info>(null);
  const [activeKey, setActiveKey] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [dbDuplicates, setDbDuplicates] = useState<Record<number, string | null>>({});
  const focusRef = useRef<{ key: number; field: string } | null>(null);

  const update = useCallback(
    (key: number, change: (r: Row) => Row) => {
      commit((rs) => rs.map((r) => (r.key === key ? change(r) : r)));
    },
    [commit],
  );

  /** Edits by the user: the row is no longer an automatic one. */
  const edit = useCallback(
    (key: number, change: (r: Row) => Row) =>
      update(key, (r) => (r.status === "saved" ? r : { ...change(r), source: "typed" })),
    [update],
  );

  const focus = (key: number, field = "paper") => {
    focusRef.current = { key, field };
  };
  useEffect(() => {
    const f = focusRef.current;
    if (!f) return;
    const el = document.querySelector<HTMLInputElement>(`[data-row="${f.key}"] [data-f="${f.field}"]`);
    if (el) {
      focusRef.current = null;
      el.focus();
      el.select?.();
    }
  });

  // ------------------------------------------------------------------ loading rows (C2)

  /** Reads the customers due on the date for the info line; drops unsaved automatic or empty rows, keeps rows the user typed. */
  const loadDate = useCallback(
    async (date: string) => {
      if (!cols.length) return;
      let list: DueList;
      try {
        list = await fetchDueList(qc, date);
      } catch {
        return;
      }
      const kept = rowsRef.current.filter(
        (r) => r.status === "saved" || (r.source === "typed" && r.customerText.trim()),
      );
      const next = [...kept];
      if (!next.some((r) => r.status !== "saved"))
        next.push(emptyRow(date, suggestPaper(next, -1, nextPaper.data?.next ?? null)));
      commit(() => next);
      setInfo({ kind: "day", list, listed: 0 });
      const first = next.find((r) => r.status !== "saved");
      if (first) {
        setActiveKey(first.key);
        focus(first.key);
      }
    },
    [cols, qc, nextPaper.data, commit],
  );

  // On open: ?cust=C0017 → one row for that customer; otherwise today's due list.
  const started = useRef(false);
  const custParam = params.get("cust");
  useEffect(() => {
    if (started.current || !cols.length) return;
    started.current = true;
    if (!custParam) {
      // loadDate sets state only after the due list has been read (async), not during the effect.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      void loadDate(today);
      return;
    }
    void (async () => {
      const found = await api<CustomerLookupRow[]>("/customers/lookup", { query: { q: custParam } }).catch(
        () => [],
      );
      const match =
        findMatch(custParam, found) ?? found.find((x) => x.code === custParam.toUpperCase()) ?? null;
      if (!match) {
        void loadDate(today);
        return;
      }
      const c = fromDetail(await fetchCustomer(qc, match.id));
      const r = rowFor(c, cols, today, "typed");
      commit(() => [r]);
      setActiveKey(r.key);
      setInfo({ kind: "customer", name: c.name });
      focus(r.key);
    })();
  }, [cols, commit, custParam, loadDate, qc, today]);

  function changeBillDate(iso: string | null) {
    if (!iso) return;
    if (iso > today) {
      toast("Bills cannot be entered for a future date", "bad");
      setBillDate(today);
      void loadDate(today);
      return;
    }
    setBillDate(iso);
    void loadDate(iso);
  }

  function addRow(after?: number): number {
    const rs = rowsRef.current;
    const r = emptyRow(billDate, suggestPaper(rs, -1, nextPaper.data?.next ?? null));
    const i = after === undefined ? rs.length : rs.findIndex((x) => x.key === after) + 1;
    commit(() => [...rs.slice(0, i), r, ...rs.slice(i)]);
    setActiveKey(r.key);
    focus(r.key);
    return r.key;
  }

  function removeRow(key: number) {
    const r = rowsRef.current.find((x) => x.key === key);
    if (!r) return;
    if (r.status === "saved") {
      toast(NOT_DELETABLE, "warn");
      return;
    }
    const rest = rowsRef.current.filter((x) => x.key !== key);
    if (!rest.length) rest.push(emptyRow(billDate, suggestPaper(rest, -1, nextPaper.data?.next ?? null)));
    commit(() => rest);
  }

  /** Paper-bill day: list the due customers still without a bill, with their usual quantities (empty rows go). */
  function fillNotEntered() {
    if (info?.kind !== "day") return;
    const inGrid = new Set(rowsRef.current.map((r) => r.customer?.id));
    const added = info.list.items
      .filter((x) => x.listed && !inGrid.has(x.id))
      .map((x) => rowFor(fromDue(x), cols, info.list.date, "auto"));
    if (!added.length) return;
    const kept = rowsRef.current.filter(
      (r) =>
        r.status === "saved" || r.customer || r.customerText.trim() || (r.paper.trim() && !r.paperSuggested),
    );
    commit(() => [...kept, ...added]);
    setInfo({ ...info, listed: info.listed + added.length });
    setActiveKey(added[0].key);
    focus(added[0].key);
  }

  // ------------------------------------------------------------------ customers (C3)

  /** A customer chosen in a row: load bottles, prices and usual quantities (unless already filled in for them). */
  const pick = useCallback(
    async (key: number, match: CustomerLookupRow) => {
      const row = rowsRef.current.find((x) => x.key === key);
      const detail = await fetchCustomer(qc, match.id, parseDmy(row?.date)).catch(() => null);
      if (!detail) return;
      const c = fromDetail(detail);
      update(key, (r) => {
        if (r.status === "saved") return r;
        const same = r.customer?.id === c.id;
        return {
          ...r,
          source: "typed",
          customer: c,
          customerText: customerText(c),
          qty: same ? r.qty : usualQty(c, cols, r.kind),
          paidTouched: same ? r.paidTouched : false,
        };
      });
    },
    [cols, qc, update],
  );

  /** The row date changed: load the customer's water prices in force on the new date (BR-08). */
  async function refreshPrices(key: number, date: string) {
    const iso = parseDmy(date);
    const r = rowsRef.current.find((x) => x.key === key);
    if (!iso || !r?.customer) return;
    const detail = await fetchCustomer(qc, r.customer.id, iso).catch(() => null);
    if (!detail) return;
    const prices = fromDetail(detail).prices;
    update(key, (x) =>
      x.status === "saved" || x.customer?.id !== detail.id || parseDmy(x.date) !== iso
        ? x
        : { ...x, customer: { ...x.customer, prices } },
    );
  }

  /** Enter on the customer cell before the matches arrived: look the text up now. */
  async function resolveNow(r: Row): Promise<void> {
    const t = r.customerText.trim();
    if (!t || r.customer) return;
    const found = await api<CustomerLookupRow[]>("/customers/lookup", { query: { q: t } }).catch(() => []);
    const match = findMatch(t, found) ?? (found.length === 1 ? found[0] : null);
    if (match) await pick(r.key, match);
  }

  // ------------------------------------------------------------------ live results (C4)

  const views = useMemo(() => {
    const out: Record<number, RowView | null> = {};
    for (const r of rows)
      out[r.key] = r.status === "saved" ? null : rowView(r, rows, cols, today, dbDuplicates[r.key] ?? null);
    return out;
  }, [rows, cols, today, dbDuplicates]);

  const setDuplicate = useCallback(
    (key: number, message: string | null) => {
      const r = rowsRef.current.find((x) => x.key === key);
      if (message && r && r.status !== "saved" && r.paperSuggested && /^\d{1,15}$/.test(r.paper.trim())) {
        // A suggested number that is already entered: take the next free one instead of warning (the check runs again).
        update(key, (x) => ({ ...x, paper: freePaper(rowsRef.current, key, Number(x.paper.trim()) + 1) }));
        return;
      }
      setDbDuplicates((d) => (d[key] === message ? d : { ...d, [key]: message }));
    },
    [update],
  );

  // ------------------------------------------------------------------ saving (C6)

  async function saveRow(r: Row, approve: boolean): Promise<boolean> {
    if (!r.customer) {
      update(r.key, (x) => ({
        ...x,
        status: "failed",
        error: { message: "Customer not found", code: "", creditLimit: false },
      }));
      return false;
    }
    update(r.key, (x) => ({ ...x, status: "saving", error: null }));
    try {
      const result = await postBill(billRequest(r, cols, approve));
      update(r.key, (x) => ({ ...x, status: "saved", result, error: null }));
      return true;
    } catch (e) {
      const err =
        e instanceof ApiError
          ? {
              message: Object.values(e.fieldErrors).join(" ") || e.message,
              code: e.code,
              creditLimit: e.code === "CREDIT_LIMIT",
            }
          : { message: e instanceof Error ? e.message : String(e), code: "", creditLimit: false };
      update(r.key, (x) => ({ ...x, status: "failed", error: err }));
      return false;
    }
  }

  async function saveAll() {
    if (saving) return;
    const pending = rowsRef.current.filter(
      (r) => r.status !== "saved" && (r.customer || r.customerText.trim()),
    );
    if (!pending.length) {
      toast("Nothing to save yet.", "warn");
      return;
    }
    setSaving(true);
    let ok = 0;
    let bad = 0;
    for (const r of pending) {
      if (await saveRow(r, false)) ok++;
      else bad++;
    }
    setSaving(false);
    invalidateAfterSale(qc);
    toast(`${ok} bill(s) saved${bad ? `, ${bad} need attention (see Result)` : ""}`, bad ? "warn" : "ok");
    if (!bad) {
      // All saved: continue in an empty row (the one already at the end, or a new one).
      let r = rowsRef.current.find((x) => x.status === "open" && !x.customerText.trim());
      if (!r) {
        const added = emptyRow(billDate, suggestPaper(rowsRef.current, -1, null));
        commit((rs) => [...rs, added]);
        r = added;
      }
      setActiveKey(r.key);
      focus(r.key);
    }
  }

  async function approveAndSave(r: Row) {
    setSaving(true);
    const ok = await saveRow(r, true);
    setSaving(false);
    if (ok) {
      invalidateAfterSale(qc);
      toast("Saved with Admin approval", "ok");
    }
  }

  // ------------------------------------------------------------------ keyboard (C5)

  const saveAllRef = useRef(saveAll);
  saveAllRef.current = saveAll;
  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && (e.key === "s" || e.key === "S")) {
        e.preventDefault();
        void saveAllRef.current();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, []);

  async function onGridKey(e: KeyboardEvent<HTMLTableSectionElement>) {
    const target = e.target as HTMLElement;
    const tr = target.closest("tr");
    const key = Number(tr?.dataset.row);
    const r = rowsRef.current.find((x) => x.key === key);
    if (!tr || !r) return;
    if ((e.ctrlKey || e.metaKey) && e.key === "Delete") {
      e.preventDefault();
      removeRow(key);
      return;
    }
    if (e.key !== "Enter" || target.tagName === "BUTTON") return;
    e.preventDefault();
    if (target.dataset.f === "cust") await resolveNow(r);
    const fields = Array.from(
      tr.querySelectorAll<HTMLInputElement>("input:not(:disabled), select:not(:disabled)"),
    );
    const i = fields.indexOf(target as HTMLInputElement);
    if (i >= 0 && i < fields.length - 1) {
      fields[i + 1].focus();
      fields[i + 1].select?.();
      return;
    }
    const rs = rowsRef.current;
    const next = rs[rs.findIndex((x) => x.key === key) + 1];
    if (next) {
      setActiveKey(next.key);
      const el = document.querySelector<HTMLInputElement>(`[data-row="${next.key}"] [data-f="paper"]`);
      el?.focus();
      el?.select();
    } else addRow(key);
  }

  function paperFocus(r: Row) {
    if (r.status === "saved" || r.paper.trim()) return;
    const s = suggestPaper(rowsRef.current, r.key, nextPaper.data?.next ?? null);
    if (s) {
      // A suggestion is not typing: an automatic row stays automatic.
      update(r.key, (x) => ({ ...x, paper: s, paperSuggested: true }));
      focus(r.key);
    }
  }

  function kindKey(e: KeyboardEvent<HTMLSelectElement>, r: Row) {
    const k = e.key.toLowerCase();
    if (k === "e" || k === "l") {
      e.preventDefault();
      setKind(r, k === "e" ? "EXCHANGE" : "LEFT_AT_DOOR");
    }
  }

  function setKind(r: Row, kind: BillKind) {
    edit(r.key, (x) => {
      // Left at door: empties cleared; back to Exchange: usual empties again.
      const qty = { ...x.qty };
      for (const b of cols) {
        const q = qty[b.code] ?? { filled: "", empties: "" };
        qty[b.code] = {
          ...q,
          empties:
            kind === "LEFT_AT_DOOR"
              ? ""
              : x.customer
                ? (usualQty(x.customer, cols, kind)[b.code]?.empties ?? "")
                : q.empties,
        };
      }
      return {
        ...x,
        kind,
        qty,
        paidTouched: false,
        paid: "",
        phone: kind === "LEFT_AT_DOOR" ? x.phone : false,
      };
    });
  }

  // ------------------------------------------------------------------ render

  const active = rows.find((r) => r.key === activeKey) ?? null;
  const sum = totals(rows, cols);
  // Bills already saved on the info date (phone or office), by customer – a new row for them warns.
  const enteredOn = info?.kind === "day" ? info.list.date : null;
  const enteredBy = new Map<number, EnteredBill[]>();
  if (info?.kind === "day") info.list.items.forEach((x) => x.entered && enteredBy.set(x.id, x.enteredBills));
  const inGrid = new Set(rows.map((r) => r.customer?.id));
  const waiting =
    info?.kind === "day" ? info.list.items.filter((x) => x.listed && !inGrid.has(x.id)).length : 0;
  const alreadyText = (r: Row): string | null => {
    if (r.status === "saved" || !r.customer || parseDmy(r.date) !== enteredOn) return null;
    const bills = enteredBy.get(r.customer.id);
    if (!bills?.length) return null;
    return (
      "Already entered " +
      bills
        .map((b) => (b.onPhone ? `on the phone by ${b.enteredBy}` : `by ${b.enteredBy}`) + ` – ${b.billNo}`)
        .join("; ") +
      ". Save only if this is another delivery."
    );
  };

  return (
    <>
      <PageActions>
        <button type="button" className="btn primary" disabled={saving} onClick={() => void saveAll()}>
          {saving ? "Saving…" : "Save bills (Ctrl+S)"}
        </button>
      </PageActions>
      <div className="card no-print">
        <div className="toolbar" style={{ marginBottom: 6 }}>
          <div className="field">
            <label htmlFor="bdate">Bill date</label>
            <DateField id="bdate" value={billDate} onChange={changeBillDate} />
          </div>
          <button
            type="button"
            className="btn"
            title="List the customers due on the bill date again"
            onClick={() => void loadDate(billDate)}
          >
            Refresh list
          </button>
          <button type="button" className="btn" onClick={() => addRow()}>
            + Add row
          </button>
        </div>
        <div className="banner info" style={{ margin: "6px 0" }} data-testid="day-info">
          {info ? (
            <DayInfo info={info} waiting={waiting} onFill={fillNotEntered} />
          ) : (
            "Loading the customers due…"
          )}
        </div>
        <div className="small muted keys">
          <span>
            <span className="kbd">Enter</span> next field
          </span>
          <span>
            <span className="kbd">Enter</span> on the last field → new row
          </span>
          <span>
            <span className="kbd">Ctrl</span>+<span className="kbd">S</span> save all
          </span>
          <span>
            <span className="kbd">Ctrl</span>+<span className="kbd">Del</span> remove row
          </span>
          <span>
            Customer: type code (e.g. <b>17</b>), phone or name
          </span>
          <span>
            Type: <b>E</b> Exchange · <b>L</b> Left at door
          </span>
        </div>
      </div>

      <div className="card" style={{ padding: 10 }}>
        <div className="table-wrap">
          <table className="bill-grid">
            <thead>
              <tr>
                <th rowSpan={2}>#</th>
                <th
                  rowSpan={2}
                  title="Number printed on the paper bill book – used to stop the same bill being entered twice"
                >
                  Paper bill no.
                </th>
                <th rowSpan={2}>Date</th>
                <th rowSpan={2}>Customer</th>
                <th rowSpan={2}>Type</th>
                {cols.map((b) => (
                  <th key={b.code} colSpan={2} className="bt-head">
                    {b.label}
                  </th>
                ))}
                <th rowSpan={2} className="bl">
                  Cash paid
                </th>
                <th rowSpan={2} title="Left at door – customer agreed by phone">
                  Phone ✓
                </th>
                <th rowSpan={2}>Note</th>
                <th rowSpan={2}>Result</th>
                <th rowSpan={2}></th>
              </tr>
              <tr>
                {cols.map((b) => (
                  <Fragment key={b.code}>
                    <th className="num bl">Filled</th>
                    <th className="num">Empties</th>
                  </Fragment>
                ))}
              </tr>
            </thead>
            <tbody onKeyDown={(e) => void onGridKey(e)}>
              {rows.map((r, i) => {
                const view = views[r.key];
                const locked = r.status === "saved" || r.status === "saving";
                const left = r.kind === "LEFT_AT_DOOR";
                const cls = [
                  r.key === activeKey && "active-row",
                  r.status === "saved" && "posted",
                  r.status !== "saved" && (r.status === "failed" || view?.hasError) && "err",
                ]
                  .filter(Boolean)
                  .join(" ");
                return (
                  <tr key={r.key} data-row={r.key} className={cls} onFocus={() => setActiveKey(r.key)}>
                    <td className="calc idx">{i + 1}</td>
                    <td>
                      <input
                        data-f="paper"
                        className={dbDuplicates[r.key] || view?.dupInGrid ? "w-bill invalid" : "w-bill"}
                        aria-label={`Paper bill no. row ${i + 1}`}
                        inputMode="numeric"
                        value={r.paper}
                        disabled={locked}
                        onFocus={() => paperFocus(r)}
                        onChange={(e) =>
                          edit(r.key, (x) => ({ ...x, paper: e.target.value, paperSuggested: false }))
                        }
                      />
                      {r.status !== "saved" && (
                        <PaperCheck rowKey={r.key} paper={r.paper} onResult={setDuplicate} />
                      )}
                    </td>
                    <td>
                      <input
                        data-f="date"
                        className={view?.badDate && r.date.trim() ? "w-date invalid" : "w-date"}
                        aria-label={`Date row ${i + 1}`}
                        maxLength={10}
                        inputMode="numeric"
                        placeholder="DD/MM/YYYY"
                        value={r.date}
                        disabled={locked}
                        onChange={(e) => {
                          const date = autoSlashDate(e.target.value);
                          edit(r.key, (x) => ({ ...x, date }));
                          void refreshPrices(r.key, date);
                        }}
                      />
                    </td>
                    <td>
                      <CustomerCell
                        row={r}
                        index={i}
                        disabled={locked}
                        missing={!!view?.customerMissing}
                        onText={(text) =>
                          edit(r.key, (x) => ({
                            ...x,
                            customerText: text,
                            customer: x.customer && text === customerText(x.customer) ? x.customer : null,
                          }))
                        }
                        onPick={(m) => void pick(r.key, m)}
                      />
                    </td>
                    <td>
                      <select
                        data-f="kind"
                        aria-label={`Type row ${i + 1}`}
                        value={r.kind}
                        disabled={locked}
                        onKeyDown={(e) => kindKey(e, r)}
                        onChange={(e) => setKind(r, e.target.value as BillKind)}
                      >
                        <option value="EXCHANGE">Exchange</option>
                        <option value="LEFT_AT_DOOR">Left at door</option>
                      </select>
                    </td>
                    {cols.map((b) => {
                      const q = r.qty[b.code] ?? { filled: "", empties: "" };
                      const set = (f: "filled" | "empties", v: string) =>
                        edit(r.key, (x) => ({
                          ...x,
                          qty: { ...x.qty, [b.code]: { ...q, ...x.qty[b.code], [f]: v } },
                        }));
                      return (
                        <Fragment key={b.code}>
                          <td className="bl">
                            <input
                              data-f={`f_${b.code}`}
                              className="w-qty"
                              aria-label={`${b.label} filled row ${i + 1}`}
                              type="number"
                              min={0}
                              value={q.filled}
                              disabled={locked}
                              onChange={(e) => set("filled", e.target.value)}
                            />
                          </td>
                          <td>
                            <input
                              data-f={`e_${b.code}`}
                              className="w-qty"
                              aria-label={`${b.label} empties row ${i + 1}`}
                              type="number"
                              min={0}
                              value={left ? "" : q.empties}
                              disabled={locked || left}
                              onChange={(e) => set("empties", e.target.value)}
                            />
                          </td>
                        </Fragment>
                      );
                    })}
                    <td className="bl">
                      <input
                        data-f="paid"
                        className="w-paid"
                        aria-label={`Cash paid row ${i + 1}`}
                        type="number"
                        min={0}
                        step={10}
                        value={
                          r.status === "saved" && r.result
                            ? String(r.result.txn.paid)
                            : shownPaid(r, view?.calc ?? null)
                        }
                        disabled={locked}
                        onChange={(e) =>
                          edit(r.key, (x) => ({ ...x, paid: e.target.value, paidTouched: true }))
                        }
                      />
                    </td>
                    <td className="center">
                      <input
                        data-f="phone"
                        type="checkbox"
                        aria-label={`Phone agreement row ${i + 1}`}
                        checked={left && r.phone}
                        disabled={locked || !left}
                        onChange={(e) => edit(r.key, (x) => ({ ...x, phone: e.target.checked }))}
                      />
                    </td>
                    <td>
                      <input
                        data-f="note"
                        className="w-note"
                        aria-label={`Note row ${i + 1}`}
                        maxLength={500}
                        placeholder={left ? "Who agreed, where left" : ""}
                        value={r.note}
                        disabled={locked}
                        onChange={(e) => edit(r.key, (x) => ({ ...x, note: e.target.value }))}
                      />
                    </td>
                    <td className="st" data-testid={`result-${i + 1}`}>
                      <ResultCell row={r} view={view} already={alreadyText(r)} />
                      {r.status === "failed" && r.error?.creditLimit && canApprove && (
                        <div>
                          <button
                            type="button"
                            className="btn sm"
                            disabled={saving}
                            onClick={() => void approveAndSave(r)}
                          >
                            Approve &amp; save
                          </button>
                        </div>
                      )}
                    </td>
                    <td>
                      <button
                        type="button"
                        className="btn sm danger"
                        title="Remove row (Ctrl+Del)"
                        aria-label={`Remove row ${i + 1}`}
                        tabIndex={-1}
                        onClick={() => removeRow(r.key)}
                      >
                        ×
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
            <tfoot>
              <tr>
                <td></td>
                <td colSpan={4} data-testid="grid-totals">
                  {sum.bills} bill(s) · {sum.saved} saved
                </td>
                {cols.map((b) => (
                  <Fragment key={b.code}>
                    <td className="num bl">{sum.filled[b.code] ?? 0}</td>
                    <td className="num">{sum.empties[b.code] ?? 0}</td>
                  </Fragment>
                ))}
                <td className="num bl">{formatMoney(sum.cash)}</td>
                <td></td>
                <td colSpan={3}>Total {formatMoney(sum.charged)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>

      <div className="grid side" style={{ gridTemplateColumns: "minmax(0,3fr) minmax(280px,2fr)" }}>
        <ActiveCustomer row={active} view={active ? views[active.key] : null} cols={cols} />
        <div className="card">
          <h3>Rules</h3>
          <ul className="small" style={{ paddingLeft: 18, margin: 0 }}>
            <li>Exchange: the customer pays only the water price.</li>
            <li>
              Fewer empties than filled → the difference becomes <b>bottles owed</b> (note required).
            </li>
            <li>More empties than filled → clears empties left at the door, then bottles owed.</li>
            <li>
              <b>Left at door</b>: tick the phone agreement and add a note. The empties are collected next
              trip – not a shortage.
            </li>
            <li>
              The system gives every bill its own number (B000…). The paper bill number is kept only to stop
              the same paper bill being entered twice.
            </li>
            <li>Saving moves the customer&apos;s next delivery date forward.</li>
          </ul>
        </div>
      </div>
    </>
  );
}

/** Customer card for the active row: balances after this bill (or "Saved – balances updated"). */
function ActiveCustomer({ row, view, cols }: { row: Row | null; view: RowView | null; cols: BottleCol[] }) {
  const detail = useCustomer(row?.customer?.id ?? null);
  if (!row) return <div className="card muted">Click a row to see the customer&apos;s bottle balances.</div>;
  if (!row.customer) return <div className="card muted">Select a customer to see bottle balances.</div>;
  if (!detail.data) return <div className="card muted">Loading…</div>;
  const saved = row.status === "saved";
  const c: GridCustomer = row.customer;
  const calc = view?.calc ?? null;
  let after: BottlesAfter[] | undefined;
  let credit = null;
  if (!saved && calc) {
    after = cols.map((b) => {
      const now = c.bottles[b.code] ?? { held: 0, toCollect: 0, owed: 0 };
      const a = calc.after[b.code] ?? now;
      return {
        bottleTypeCode: b.code,
        label: b.label,
        held: a.held,
        toCollect: a.toCollect,
        owed: a.owed,
        heldChange: a.held - now.held,
        toCollectChange: a.toCollect - now.toCollect,
        owedChange: a.owed - now.owed,
      };
    });
    const paid = paidNumber(row, calc);
    const chk = exceedsCredit(c.payType, c.creditLimit, Number(detail.data.outstanding), calc.amount, paid);
    credit = {
      balance: Number(detail.data.outstanding),
      after: chk.after,
      limit: c.creditLimit,
      exceeds: chk.exceeds,
    };
  }
  return (
    <div className="card">
      {saved ? (
        <div className="banner ok">Saved – balances updated.</div>
      ) : (
        <div className="small muted" style={{ marginBottom: 6 }}>
          Balances <b>after</b> this bill:
        </div>
      )}
      <CustomerCard customer={detail.data} after={after} credit={credit} />
      {saved && row.result && (
        <div className="small muted" style={{ marginTop: 6 }}>
          Bill {row.result.txn.billNo} of {formatDate(row.result.txn.date)}.
        </div>
      )}
    </div>
  );
}
