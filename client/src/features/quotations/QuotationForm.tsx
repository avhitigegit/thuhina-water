"use client";

/*
 * New / edit / copy quotation pop-up (prototype sales/quotations.html "edit", FR-64): number, date, valid until (+30 days);
 * To – a new organisation (name, address, area, phone) or an existing customer (picker; its type is chosen); customer type
 * for prices, contact, designation, email; subject; lines (item, printed description, qty, unit, unit price, amount) with
 * a live total; delivery, payment terms, terms and conditions (one per line). A line's price starts at the customer's
 * price now (agreed or standard) or the standard price of the type, and follows it until it is changed by hand; the price
 * now is shown beside a changed price and lines below it are counted. The server checks everything again.
 */
import { useState } from "react";
import { DateField } from "@/components/shared/DateField";
import { FormDialog } from "@/components/shared/FormDialog";
import { useAreas, useCustomer, useCustomerTypes } from "@/features/customers/api";
import { CustomerPicker } from "@/features/customers/CustomerPicker";
import { TERMS } from "@/features/partners/schemas";
import type { CqItemOption, CqSaved, CqView, CustomerLookupRow } from "@/lib/api/types";
import { addDaysIso, formatMoney } from "@/lib/format";
import { errorText } from "@/lib/forms";
import { toast } from "@/lib/toast";
import { useQuoteItems, useSaveQuotation } from "./api";

export const DEFAULT_CONDITIONS = [
  "Prices include delivery within our delivery areas.",
  "A bottle deposit applies to each new bottle given, unless an accepted old bottle is handed in.",
  "Water is filled and sealed by an SLS-certified filling factory.",
  "Prices are valid until the date shown above.",
].join("\n");

interface FormLine {
  uid: number;
  /** "WATER|B20", "DEPOSIT|B20", "PRODUCT|P01", "OTHER" or "" (not chosen). */
  key: string;
  description: string;
  qty: string;
  unit: string;
  price: string;
  /** Changed by hand: no longer follows the price now. */
  touched: boolean;
}

let uid = 0;
const nextUid = () => ++uid;

function keyOf(l: { kind: string; bottleTypeCode?: string | null; productCode?: string | null }): string {
  if (l.kind === "WATER" || l.kind === "DEPOSIT") return `${l.kind}|${l.bottleTypeCode}`;
  if (l.kind === "PRODUCT") return `PRODUCT|${l.productCode}`;
  return "OTHER";
}

/** The price shown for a line: typed (or OTHER), else the item's price now. */
export function linePrice(l: FormLine, item: CqItemOption | undefined): string {
  if (l.touched || !item || item.currentPrice == null) return l.price;
  return String(item.currentPrice);
}

export function QuotationForm({
  source,
  mode,
  initialCustomer,
  today,
  onClose,
  onSaved,
}: {
  /** The quotation to edit or copy; null = new. */
  source: CqView | null;
  mode: "new" | "edit" | "copy";
  initialCustomer?: CustomerLookupRow | null;
  today: string;
  onClose: () => void;
  onSaved: (saved: CqSaved) => void;
}) {
  const save = useSaveQuotation();
  const types = useCustomerTypes();
  const areas = useAreas();
  const editing = mode === "edit" ? source : null;
  const [quoteDate, setQuoteDate] = useState<string | null>(editing?.quoteDate ?? today);
  const [validUntil, setValidUntil] = useState<string | null>(editing?.validUntil ?? addDaysIso(today, 30));
  const fromCustomer = initialCustomer ?? null;
  const [to, setTo] = useState<"new" | "cust">(source?.customerId || fromCustomer ? "cust" : "new");
  const [customer, setCustomer] = useState<CustomerLookupRow | null>(
    fromCustomer ??
      (source?.customerId
        ? {
            id: source.customerId,
            code: source.customerCode ?? "",
            name: source.orgName,
            phone: source.phone ?? "",
            areaName: source.areaName ?? "",
            status: "ACTIVE",
          }
        : null),
  );
  const detail = useCustomer(to === "cust" ? (customer?.id ?? null) : null);
  const [org, setOrg] = useState(source && !source.customerId ? source.orgName : "");
  const [address, setAddress] = useState(source && !source.customerId ? (source.address ?? "") : "");
  const [areaId, setAreaId] = useState(
    source && !source.customerId && source.areaId ? String(source.areaId) : "",
  );
  const [phone, setPhone] = useState(source && !source.customerId ? (source.phone ?? "") : "");
  const [typeId, setTypeId] = useState(source ? String(source.customerTypeId) : "");
  const [contact, setContact] = useState(source?.contact ?? "");
  const [designation, setDesignation] = useState(source?.designation ?? "");
  const [email, setEmail] = useState(source?.email ?? "");
  const [subject, setSubject] = useState(source?.subject ?? "");
  const [delivery, setDelivery] = useState(source?.deliveryText ?? "");
  const [terms, setTerms] = useState(String(source?.termsDays ?? 30));
  const [conditions, setConditions] = useState(source ? (source.conditions ?? "") : DEFAULT_CONDITIONS);
  const [lines, setLines] = useState<FormLine[]>(() =>
    source
      ? source.lines.map((l) => ({
          uid: nextUid(),
          key: keyOf(l),
          description: l.description,
          qty: String(l.qty),
          unit: l.unit ?? "",
          price: String(l.unitPrice),
          // Lines at the price of the day keep following it; prices changed by hand stay.
          touched: l.currentPrice == null || Number(l.currentPrice) !== Number(l.unitPrice),
        }))
      : [],
  );
  const [error, setError] = useState("");

  // A chosen customer brings its type (prototype); the default type for a new organisation is Office.
  const [typeFor, setTypeFor] = useState<number | null>(null);
  if (to === "cust" && detail.data && typeFor !== detail.data.id) {
    setTypeFor(detail.data.id);
    if (!source || source.customerId !== detail.data.id) setTypeId(String(detail.data.customerTypeId));
  }
  const activeTypes = (types.data ?? []).filter((t) => t.active || String(t.id) === typeId);
  if (!typeId && activeTypes.length) {
    setTypeId(String((activeTypes.find((t) => t.name === "Office") ?? activeTypes[0]).id));
  }
  const customerId = to === "cust" ? (customer?.id ?? null) : null;
  const items = useQuoteItems(typeId ? Number(typeId) : null, customerId, quoteDate);
  const byKey = new Map((items.data ?? []).map((i) => [i.key, i]));

  // A new quotation starts with water and deposits of the main bottle type (prototype), once the items load.
  const firstWater = items.data?.find((i) => i.kind === "WATER");
  if (!source && lines.length === 0 && firstWater) {
    const dep = byKey.get(`DEPOSIT|${firstWater.bottleTypeCode}`);
    setLines([
      {
        uid: nextUid(),
        key: firstWater.key,
        description: firstWater.description,
        qty: "40",
        unit: firstWater.unit,
        price: "",
        touched: false,
      },
      ...(dep
        ? [
            {
              uid: nextUid(),
              key: dep.key,
              description: dep.description,
              qty: "10",
              unit: dep.unit,
              price: "",
              touched: false,
            },
          ]
        : []),
    ]);
  }

  function changeLine(i: number, patch: Partial<FormLine>) {
    setLines((ls) => ls.map((l, j) => (j === i ? { ...l, ...patch } : l)));
  }

  function chooseItem(i: number, key: string) {
    const item = byKey.get(key);
    changeLine(i, {
      key,
      description: item?.description ?? "",
      unit: item?.unit ?? "",
      price: key === "OTHER" ? "" : lines[i].price,
      touched: key === "OTHER",
    });
  }

  const shown = lines.map((l) => {
    const item = byKey.get(l.key);
    const price = linePrice(l, item);
    const now = item?.currentPrice == null ? null : Number(item.currentPrice);
    return { line: l, item, price, now, amount: (Number(price) || 0) * (Number(l.qty) || 0) };
  });
  const total = shown.reduce((sum, s) => sum + s.amount, 0);
  const below = shown.filter((s) => s.now != null && (Number(s.price) || 0) < s.now).length;
  const typeName = activeTypes.find((t) => String(t.id) === typeId)?.name ?? "";

  function submit() {
    setError("");
    if (to === "cust" && !customer) {
      setError("Select the customer from the list.");
      return;
    }
    const body = {
      customerId: to === "cust" ? customer?.id : undefined,
      orgName: to === "new" ? org : undefined,
      contact,
      designation,
      address: to === "new" ? address : undefined,
      areaId: to === "new" && areaId ? Number(areaId) : undefined,
      phone: to === "new" ? phone : undefined,
      email: email.trim(),
      customerTypeId: typeId ? Number(typeId) : undefined,
      quoteDate: quoteDate ?? undefined,
      validUntil: validUntil ?? undefined,
      subject,
      deliveryText: delivery,
      termsDays: Number(terms),
      conditions,
      lines: shown
        .filter((s) => s.line.key || s.line.description.trim())
        .map((s) => {
          const [kind, code] = (s.line.key || "OTHER").split("|");
          return {
            kind,
            bottleTypeCode: kind === "WATER" || kind === "DEPOSIT" ? code : undefined,
            productCode: kind === "PRODUCT" ? code : undefined,
            description: s.line.description,
            qty: s.line.qty.trim() === "" ? undefined : Number(s.line.qty),
            unit: s.line.unit,
            unitPrice: s.price.trim() === "" ? undefined : Number(s.price),
          };
        }),
      ...(mode === "copy" && source ? { copyOf: source.id } : {}),
      ...(editing ? { version: editing.version } : {}),
    };
    save.mutate(
      { id: editing?.id ?? null, body },
      {
        onSuccess: (saved) => {
          toast(saved.message, "ok");
          onSaved(saved);
        },
        onError: (err) => setError(errorText(err)),
      },
    );
  }

  const title = editing
    ? `Edit quotation ${editing.cqNo}`
    : mode === "copy" && source
      ? `New quotation – copy of ${source.cqNo}`
      : "New quotation";

  return (
    <FormDialog
      open
      wide
      onOpenChange={(o) => !o && onClose()}
      title={title}
      onSubmit={submit}
      submitLabel={editing ? "Save" : "Save as draft"}
      submitting={save.isPending}
    >
      {error && <div className="banner bad">{error}</div>}
      <div className="form-grid">
        <div className="field">
          <label htmlFor="cq-no">Quotation no.</label>
          <input id="cq-no" value={editing?.cqNo ?? "Generated on save"} readOnly />
        </div>
        <div className="field req">
          <label htmlFor="cq-date">Date</label>
          <DateField id="cq-date" value={quoteDate} onChange={setQuoteDate} today={today} />
        </div>
        <div className="field req">
          <label htmlFor="cq-valid">Valid until</label>
          <DateField id="cq-valid" value={validUntil} onChange={setValidUntil} today={today} />
        </div>

        <div className="section-title full">To</div>
        <div className="field full">
          <div style={{ display: "flex", gap: 18 }}>
            <label className="check">
              <input type="radio" name="cq-to" checked={to === "new"} onChange={() => setTo("new")} /> New
              organisation (government office, factory, hotel…)
            </label>
            <label className="check">
              <input type="radio" name="cq-to" checked={to === "cust"} onChange={() => setTo("cust")} />{" "}
              Existing customer
            </label>
          </div>
        </div>
        {to === "cust" ? (
          <div className="field req span2">
            <label htmlFor="cq-cust">Customer</label>
            <CustomerPicker id="cq-cust" value={customer} onChange={setCustomer} />
          </div>
        ) : (
          <div className="field req span2">
            <label htmlFor="cq-org">Organisation / name</label>
            <input
              id="cq-org"
              placeholder="e.g. Divisional Secretariat – Maharagama"
              value={org}
              onChange={(e) => setOrg(e.target.value)}
            />
          </div>
        )}
        <div className="field req">
          <label htmlFor="cq-type">Customer type (for prices)</label>
          <select id="cq-type" value={typeId} onChange={(e) => setTypeId(e.target.value)}>
            {activeTypes.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="cq-contact">Contact person</label>
          <input id="cq-contact" value={contact} onChange={(e) => setContact(e.target.value)} />
        </div>
        <div className="field">
          <label htmlFor="cq-desig">Designation</label>
          <input
            id="cq-desig"
            placeholder="e.g. Administrative Officer"
            value={designation}
            onChange={(e) => setDesignation(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="cq-email">Email</label>
          <input id="cq-email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        {to === "new" && (
          <>
            <div className="field span2">
              <label htmlFor="cq-address">Address</label>
              <input id="cq-address" value={address} onChange={(e) => setAddress(e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="cq-area">Area</label>
              <select id="cq-area" value={areaId} onChange={(e) => setAreaId(e.target.value)}>
                <option value="">Outside our areas / not known</option>
                {(areas.data ?? [])
                  .filter((a) => a.active)
                  .map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
              </select>
            </div>
            <div className="field">
              <label htmlFor="cq-phone">Phone</label>
              <input
                id="cq-phone"
                placeholder="011 234 5678"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
              />
            </div>
          </>
        )}
        <div className="section-title full">Quotation</div>
        <div className="field full">
          <label htmlFor="cq-subject">Subject</label>
          <input
            id="cq-subject"
            placeholder="e.g. Supply of drinking water – 20L bottles, monthly (tender ref. …)"
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
          />
        </div>
      </div>

      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Item</th>
              <th>Description (printed)</th>
              <th>Qty</th>
              <th>Unit</th>
              <th>Unit price (Rs.)</th>
              <th className="num">Amount</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {shown.map(({ line: l, item, price, now, amount }, i) => (
              <tr key={l.uid}>
                <td>
                  <select
                    aria-label={`Item ${i + 1}`}
                    value={l.key}
                    onChange={(e) => chooseItem(i, e.target.value)}
                  >
                    <option value="">Select item</option>
                    {(items.data ?? []).map((o) => (
                      <option key={o.key} value={o.key}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                </td>
                <td>
                  <input
                    aria-label={`Description ${i + 1}`}
                    style={{ minWidth: 220 }}
                    value={l.description}
                    onChange={(e) => changeLine(i, { description: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    aria-label={`Qty ${i + 1}`}
                    type="number"
                    min={1}
                    style={{ width: 80 }}
                    value={l.qty}
                    onChange={(e) => changeLine(i, { qty: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    aria-label={`Unit ${i + 1}`}
                    style={{ width: 90 }}
                    value={l.unit}
                    onChange={(e) => changeLine(i, { unit: e.target.value })}
                  />
                </td>
                <td>
                  <input
                    aria-label={`Unit price ${i + 1}`}
                    type="number"
                    min={0}
                    step="0.01"
                    style={{ width: 110 }}
                    value={price}
                    onChange={(e) => changeLine(i, { price: e.target.value, touched: true })}
                  />
                  {now != null && (
                    <div className="small muted" data-testid={`now-${i + 1}`}>
                      {Number(price) !== now ? (
                        <span style={Number(price) < now ? { color: "var(--warn)" } : undefined}>
                          {item?.agreed ? "agreed now " : "standard "}
                          {formatMoney(now)}
                        </span>
                      ) : item?.agreed ? (
                        "agreed price now"
                      ) : (
                        "standard price"
                      )}
                    </div>
                  )}
                </td>
                <td className="num">{amount ? formatMoney(amount) : ""}</td>
                <td>
                  <button
                    type="button"
                    className="btn sm danger"
                    aria-label={`Remove line ${i + 1}`}
                    onClick={() => setLines((ls) => ls.filter((x) => x.uid !== l.uid))}
                  >
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr>
              <td colSpan={5} className="num">
                Total
              </td>
              <td className="num" data-testid="cq-total">
                {formatMoney(total)}
              </td>
              <td />
            </tr>
          </tfoot>
        </table>
      </div>
      <button
        type="button"
        className="btn sm"
        style={{ marginTop: 6 }}
        onClick={() =>
          setLines((ls) => [
            ...ls,
            { uid: nextUid(), key: "", description: "", qty: "", unit: "", price: "", touched: false },
          ])
        }
      >
        + Add line
      </button>

      <div className="form-grid" style={{ marginTop: 12 }}>
        <div className="field span2">
          <label htmlFor="cq-delivery">Delivery</label>
          <input
            id="cq-delivery"
            placeholder="e.g. Every Monday, about 10 bottles a week"
            value={delivery}
            onChange={(e) => setDelivery(e.target.value)}
          />
        </div>
        <div className="field">
          <label htmlFor="cq-terms">Payment terms</label>
          <select id="cq-terms" value={terms} onChange={(e) => setTerms(e.target.value)}>
            {TERMS.map((t) => (
              <option key={t.days} value={t.days}>
                {t.label}
              </option>
            ))}
          </select>
        </div>
        <div className="field full">
          <label htmlFor="cq-conditions">Terms and conditions (one per line)</label>
          <textarea
            id="cq-conditions"
            rows={4}
            value={conditions}
            onChange={(e) => setConditions(e.target.value)}
          />
        </div>
      </div>
      <p className="small muted" data-testid="cq-note">
        {to === "cust" && customer
          ? `Prices start at what ${customer.name} pays now.`
          : `Prices start at the standard ${typeName} price list.`}{" "}
        They can be changed for this quotation; when it is accepted, the water prices become the
        customer&apos;s agreed prices.
        {below > 0 && (
          <b>
            {" "}
            {below} line{below > 1 ? "s are" : " is"} below the current price.
          </b>
        )}
      </p>
    </FormDialog>
  );
}
