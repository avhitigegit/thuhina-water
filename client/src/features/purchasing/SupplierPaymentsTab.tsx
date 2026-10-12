"use client";

/*
 * Supplier payments tab (FR-21, prototype purchasing/purchasing.html): amounts owed to suppliers (goods received, paid,
 * owed in red, terms, oldest unpaid receipt, Pay) and the payments made. Record payment pop-up: supplier, date, amount,
 * method, cheque no. / bank reference (required unless cash), PO (optional), note. Admin full; Accountant view only.
 */
import { useState } from "react";
import { DataTable, type Column } from "@/components/shared/DataTable";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { MoneyText } from "@/components/shared/MoneyText";
import { Badge } from "@/components/shared/StatusBadge";
import { Can } from "@/features/auth/access";
import { useSuppliers } from "@/features/partners/api";
import type { PaymentRow, SupplierBalance } from "@/lib/api/types";
import { formatDate, formatMoney, todayIso } from "@/lib/format";
import { P } from "@/lib/permissions";
import { toast } from "@/lib/toast";
import { usePurchaseOrders, useRecordPayment, useSupplierBalances, useSupplierPayments } from "./api";
import { errorText, PAY_METHODS, type PayMethod } from "./labels";

export function SupplierPaymentsTab({ today = todayIso() }: { today?: string }) {
  const balances = useSupplierBalances();
  const payments = useSupplierPayments();
  const [paying, setPaying] = useState<number | null | undefined>(undefined);

  const owedColumns: Column<SupplierBalance>[] = [
    {
      key: "supplier",
      header: "Supplier",
      cell: (b) => (
        <>
          <b>{b.name}</b> <span className="small muted mono">{b.code}</span>
        </>
      ),
    },
    { key: "received", header: "Goods received", align: "num", cell: (b) => formatMoney(b.received) },
    { key: "paid", header: "Paid", align: "num", cell: (b) => formatMoney(b.paid) },
    {
      key: "owed",
      header: "Owed",
      align: "num",
      cell: (b) =>
        Number(b.owed) > 0 ? (
          <b style={{ color: "var(--bad)" }}>{formatMoney(b.owed)}</b>
        ) : (
          formatMoney(b.owed)
        ),
    },
    { key: "terms", header: "Terms", align: "num", cell: (b) => b.termsLabel },
    {
      key: "oldest",
      header: "Oldest unpaid",
      cell: (b) =>
        b.oldestUnpaidDate ? (
          <>
            {formatDate(b.oldestUnpaidDate)} <span className="small muted">({b.oldestUnpaidDays} days)</span>
            {b.overdue && (
              <>
                {" "}
                <Badge kind="bad">Overdue</Badge>
              </>
            )}
          </>
        ) : (
          <span className="muted">–</span>
        ),
    },
    {
      key: "pay",
      header: "",
      className: "actions",
      cell: (b) =>
        Number(b.owed) > 0 && (
          <Can permission={P.PURCHASING_EDIT}>
            <button className="btn sm" type="button" onClick={() => setPaying(b.supplierId)}>
              Pay
            </button>
          </Can>
        ),
    },
  ];

  const payColumns: Column<PaymentRow>[] = [
    { key: "ref", header: "Ref", cell: (p) => <span className="mono">{p.spNo}</span> },
    { key: "date", header: "Date", cell: (p) => formatDate(p.date) },
    { key: "supplier", header: "Supplier", cell: (p) => p.supplier.name },
    { key: "po", header: "PO", cell: (p) => <span className="mono">{p.poNo ?? ""}</span> },
    {
      key: "method",
      header: "Method",
      cell: (p) => (
        <>
          {p.methodLabel}
          {p.reference && <div className="small muted">{p.reference}</div>}
        </>
      ),
    },
    { key: "amount", header: "Amount", align: "num", cell: (p) => <MoneyText value={p.amount} /> },
    { key: "by", header: "Entered by", cell: (p) => <span className="small">{p.createdByName}</span> },
  ];

  return (
    <>
      <div className="card">
        <div className="card-head">
          <h2>Amounts owed to suppliers</h2>
          <Can permission={P.PURCHASING_EDIT}>
            <button className="btn primary" type="button" onClick={() => setPaying(null)}>
              + Record payment
            </button>
          </Can>
        </div>
        {balances.isError ? (
          <div className="banner bad">{balances.error.message}</div>
        ) : (
          <DataTable
            columns={owedColumns}
            rows={balances.data}
            loading={balances.isPending}
            rowKey={(b) => b.supplierId}
            empty="Nothing owed."
          />
        )}
      </div>
      <div className="card">
        <h2>Payments made</h2>
        {payments.isError ? (
          <div className="banner bad">{payments.error.message}</div>
        ) : (
          <DataTable
            columns={payColumns}
            rows={payments.data}
            loading={payments.isPending}
            rowKey={(p) => p.id}
            empty="No payments yet."
          />
        )}
      </div>
      {paying !== undefined && (
        <PaymentDialog
          supplierId={paying}
          balances={balances.data ?? []}
          today={today}
          onClose={() => setPaying(undefined)}
        />
      )}
    </>
  );
}

function PaymentDialog({
  supplierId,
  balances,
  today,
  onClose,
}: {
  supplierId: number | null;
  balances: SupplierBalance[];
  today: string;
  onClose: () => void;
}) {
  const suppliers = useSuppliers();
  const orders = usePurchaseOrders("");
  const pay = useRecordPayment();
  const owedOf = (id: number) => Number(balances.find((b) => b.supplierId === id)?.owed ?? 0);
  const firstOwed = balances.find((b) => Number(b.owed) > 0)?.supplierId;
  const start = supplierId ?? firstOwed ?? null;

  const [supplier, setSupplier] = useState<string>(start === null ? "" : String(start));
  const [date, setDate] = useState<string | null>(today);
  const [amount, setAmount] = useState(start === null ? "" : String(Math.max(0, owedOf(start))));
  const [method, setMethod] = useState<PayMethod>("BANK_TRANSFER");
  const [reference, setReference] = useState("");
  const [poId, setPoId] = useState("");
  const [note, setNote] = useState("");
  const [error, setError] = useState("");
  const list = (suppliers.data ?? []).filter((s) => s.active || owedOf(s.id) !== 0);
  const sid = supplier || (list[0] ? String(list[0].id) : "");
  const pos = (orders.data ?? []).filter((p) => String(p.supplier.id) === sid && p.status !== "CANCELLED");

  function submit() {
    setError("");
    if (!date) {
      setError("Enter the date.");
      return;
    }
    pay.mutate(
      {
        supplierId: sid ? Number(sid) : undefined,
        date,
        amount: amount.trim() === "" ? undefined : Number(amount),
        method,
        reference,
        poId: poId ? Number(poId) : undefined,
        note,
      },
      {
        onSuccess: (p) => {
          toast(`Payment recorded (${p.spNo})`, "ok");
          onClose();
        },
        onError: (err) => setError(errorText(err)),
      },
    );
  }

  return (
    <FormDialog
      open
      onOpenChange={(o) => !o && onClose()}
      title="Record supplier payment"
      onSubmit={submit}
      submitLabel="Save payment"
      submitting={pay.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid" style={{ gridTemplateColumns: "1fr 1fr" }}>
        <div className="field req full">
          <label htmlFor="sp-sup">Supplier</label>
          <select
            id="sp-sup"
            value={sid}
            onChange={(e) => {
              setSupplier(e.target.value);
              setPoId("");
              setAmount(String(Math.max(0, owedOf(Number(e.target.value)))));
            }}
          >
            {list.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} – owed {formatMoney(owedOf(s.id))}
              </option>
            ))}
          </select>
        </div>
        <div className="field req">
          <label htmlFor="sp-date">Date</label>
          <DateField id="sp-date" value={date} onChange={setDate} today={today} />
        </div>
        <div className="field req">
          <label htmlFor="sp-amount">Amount (Rs.)</label>
          <input
            id="sp-amount"
            type="number"
            min={0}
            step="0.01"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
          />
        </div>
        <div className="field req">
          <label htmlFor="sp-method">Method</label>
          <select id="sp-method" value={method} onChange={(e) => setMethod(e.target.value as PayMethod)}>
            {PAY_METHODS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
        </div>
        <div className={method === "CASH" ? "field" : "field req"}>
          <label htmlFor="sp-ref">Cheque no. / bank ref.</label>
          <input id="sp-ref" value={reference} onChange={(e) => setReference(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="sp-po">Purchase order</label>
          <select id="sp-po" value={poId} onChange={(e) => setPoId(e.target.value)}>
            <option value="">–</option>
            {pos.map((p) => (
              <option key={p.id} value={p.id}>
                {p.poNo} – {formatMoney(p.total)}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="sp-note">Note</label>
          <input id="sp-note" value={note} onChange={(e) => setNote(e.target.value)} />
        </div>
      </div>
    </FormDialog>
  );
}
