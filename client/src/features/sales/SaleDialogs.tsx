"use client";

/*
 * The "+ New ▾" entries of Sales & Bills (prototype sales/sales-bills.html): First purchase (FR-32, FR-33), Extra
 * order (FR-39), Product sale (FR-40), Collect empties (FR-36), Customer-damaged bottle (FR-37, BR-06) and Settle
 * bottles owed (FR-38, BR-07). Each one is a SaleDialog with its own fields and calculation; every rule and message
 * comes from the server's live calculation.
 */
import { useState } from "react";
import { useBottleTypes, useOldBottleBrands, useProducts } from "@/features/masterdata/api";
import type { CustomerDetail, CustomerLookupRow, PayMethod, SaleResult } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";
import type { SaleKind } from "./labels";
import { PAY_METHODS } from "./labels";
import { CalcRows, SaleDialog, type SaleContext } from "./SaleDialog";

export interface NewSaleProps {
  initialCustomer?: CustomerLookupRow | null;
  onClose: () => void;
  onSaved?: (r: SaleResult) => void;
}

/** Whole number typed in a field ("" = 0 for the live calculation). */
const int = (v: string) => (v.trim() === "" ? 0 : Math.trunc(Number(v)) || 0);

function useActiveBottles() {
  return (useBottleTypes().data ?? []).filter((b) => b.active);
}

function BottleField({
  value,
  onChange,
  ctx,
}: {
  value: string;
  onChange: (code: string) => void;
  ctx: SaleContext;
}) {
  const bottles = useActiveBottles();
  return (
    <div className="field req">
      <label htmlFor="sale-bottle">Bottle</label>
      <select
        id="sale-bottle"
        value={value}
        className={ctx.invalid("bottleTypeCode") ? "invalid" : undefined}
        onChange={(e) => onChange(e.target.value)}
      >
        {bottles.map((b) => (
          <option key={b.code} value={b.code}>
            {b.name}
          </option>
        ))}
      </select>
      {ctx.err("bottleTypeCode")}
    </div>
  );
}

function NumberField({
  id,
  name,
  label,
  value,
  onChange,
  ctx,
  required,
  min = 0,
}: {
  id: string;
  name: string;
  label: string;
  value: string;
  onChange: (v: string) => void;
  ctx: SaleContext;
  required?: boolean;
  min?: number;
}) {
  return (
    <div className={required ? "field req" : "field"}>
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type="number"
        min={min}
        className={ctx.invalid(name) ? "invalid num" : "num"}
        value={value}
        onChange={(e) => onChange(e.target.value)}
      />
      {ctx.err(name)}
    </div>
  );
}

/** Old-bottle brands accepted for the bottle type ("None for this size" when there are none). */
function BrandField({
  bottle,
  value,
  onChange,
  ctx,
}: {
  bottle: string;
  value: string;
  onChange: (v: string) => void;
  ctx: SaleContext;
}) {
  const brands = (useOldBottleBrands().data ?? []).filter((b) => b.active && b.bottleTypeCode === bottle);
  return (
    <div className="field">
      <label htmlFor="sale-brand">Old bottle brand</label>
      <select
        id="sale-brand"
        value={brands.some((b) => String(b.id) === value) ? value : String(brands[0]?.id ?? "")}
        className={ctx.invalid("oldBrandId") ? "invalid" : undefined}
        onChange={(e) => onChange(e.target.value)}
      >
        {brands.length === 0 && <option value="">None for this size</option>}
        {brands.map((b) => (
          <option key={b.id} value={b.id}>
            {b.name}
          </option>
        ))}
      </select>
      {ctx.err("oldBrandId")}
    </div>
  );
}

/** The brand id to send: the chosen one, or the first accepted brand of the bottle type. */
function useBrandId(bottle: string, chosen: string): number | undefined {
  const brands = (useOldBottleBrands().data ?? []).filter((b) => b.active && b.bottleTypeCode === bottle);
  const b = brands.find((x) => String(x.id) === chosen) ?? brands[0];
  return b?.id;
}

function useDefaultBottle(): [string, (v: string) => void] {
  const bottles = useActiveBottles();
  const [bottle, setBottle] = useState<string | null>(null);
  return [bottle ?? bottles[0]?.code ?? "", setBottle];
}

/** "Water 2 × Rs. 350.00 (agreed price)" */
function waterLabel(qty: number, price: number | null | undefined, agreed: boolean) {
  return `Water ${qty} × ${price != null ? formatMoney(price) : "–"}${agreed ? " (agreed price)" : ""}`;
}

const held = (c: CustomerDetail | null, code: string, f: "held" | "toCollect" | "owed") =>
  c?.bottles.find((b) => b.bottleTypeCode === code)?.[f] ?? 0;

// ------------------------------------------------------------------ First purchase

export function FirstPurchaseDialog(props: NewSaleProps) {
  const bottles = useActiveBottles();
  const [bottle, setBottle] = useDefaultBottle();
  const [qty, setQty] = useState("2");
  const [old, setOld] = useState("0");
  const [brand, setBrand] = useState("");
  const brandId = useBrandId(bottle, brand);
  const brands = (useOldBottleBrands().data ?? []).filter((b) => b.active && b.bottleTypeCode === bottle);
  const deposit = bottles.find((b) => b.code === bottle)?.deposit;
  return (
    <SaleDialog
      {...props}
      kind="first"
      title="First purchase"
      okMessage="First purchase saved"
      fieldNames={["bottleTypeCode", "qty", "oldBottles", "oldBrandId"]}
      build={() => ({
        bottleTypeCode: bottle,
        qty: int(qty),
        oldBottles: int(old),
        oldBrandId: int(old) > 0 ? brandId : undefined,
      })}
      fields={(ctx) => (
        <>
          <BottleField value={bottle} onChange={setBottle} ctx={ctx} />
          <NumberField
            id="sale-qty"
            name="qty"
            label="Bottles given"
            value={qty}
            onChange={setQty}
            ctx={ctx}
            required
            min={1}
          />
          <NumberField
            id="sale-old"
            name="oldBottles"
            label="Old bottles handed in"
            value={old}
            onChange={setOld}
            ctx={ctx}
          />
          <BrandField bottle={bottle} value={brand} onChange={setBrand} ctx={ctx} />
          <div className="field full small muted">
            Deposit {deposit != null ? formatMoney(deposit) : "–"} per new bottle + water.
            {brands.length > 0 &&
              ` Each accepted old bottle (${brands.map((b) => b.name).join(", ")}) replaces one deposit.`}
          </div>
        </>
      )}
      calc={(p) => {
        const l = p.lines[0];
        if (!l) return null;
        return (
          <CalcRows
            rows={[
              [waterLabel(l.filled, l.unitPrice, l.agreedPrice), formatMoney(p.water)],
              [`Deposit ${l.depositCount} × ${formatMoney(l.depositUnit)}`, formatMoney(p.deposit)],
              ...(l.oldBottles
                ? [["Old bottles handed in (no deposit)", l.oldBottles] as [string, number]]
                : []),
              ["Total", formatMoney(p.amount), true],
            ]}
          />
        );
      }}
    />
  );
}

// ------------------------------------------------------------------ Extra order

export function ExtraOrderDialog(props: NewSaleProps) {
  const [bottle, setBottle] = useDefaultBottle();
  const [filled, setFilled] = useState("1");
  const [empties, setEmpties] = useState("0");
  const [old, setOld] = useState("0");
  const [brand, setBrand] = useState("");
  const brandId = useBrandId(bottle, brand);
  return (
    <SaleDialog
      {...props}
      kind="extra"
      title="Extra order"
      okMessage="Extra order saved"
      fieldNames={["bottleTypeCode", "filled", "empties", "oldBottles", "oldBrandId"]}
      build={() => ({
        bottleTypeCode: bottle,
        filled: int(filled),
        empties: int(empties),
        oldBottles: int(old),
        oldBrandId: int(old) > 0 ? brandId : undefined,
      })}
      fields={(ctx) => (
        <>
          <BottleField value={bottle} onChange={setBottle} ctx={ctx} />
          <NumberField
            id="sale-filled"
            name="filled"
            label="Filled given"
            value={filled}
            onChange={setFilled}
            ctx={ctx}
            required
            min={1}
          />
          <NumberField
            id="sale-empties"
            name="empties"
            label="Empties returned"
            value={empties}
            onChange={setEmpties}
            ctx={ctx}
          />
          <NumberField
            id="sale-old"
            name="oldBottles"
            label="Old bottles handed in"
            value={old}
            onChange={setOld}
            ctx={ctx}
          />
          <BrandField bottle={bottle} value={brand} onChange={setBrand} ctx={ctx} />
          <div className="field full small muted">
            Bottles beyond the customer&apos;s holding need a deposit (or an accepted old bottle).
          </div>
        </>
      )}
      calc={(p) => {
        const l = p.lines[0];
        if (!l) return null;
        return (
          <CalcRows
            rows={[
              ["New bottles beyond holding", l.newBottles],
              [waterLabel(l.filled, l.unitPrice, l.agreedPrice), formatMoney(p.water)],
              [`Deposit ${l.depositCount} × ${formatMoney(l.depositUnit)}`, formatMoney(p.deposit)],
              ...(l.oldBottles
                ? [["Old bottles handed in (no deposit)", l.oldBottles] as [string, number]]
                : []),
              ["Total", formatMoney(p.amount), true],
            ]}
          />
        );
      }}
    />
  );
}

// ------------------------------------------------------------------ Product sale

export function ProductSaleDialog(props: NewSaleProps) {
  const products = (useProducts().data ?? []).filter((p) => p.active);
  const [code, setCode] = useState<string | null>(null);
  const [walkIn, setWalkIn] = useState("");
  const [qty, setQty] = useState("1");
  const [price, setPrice] = useState<{ code: string; text: string } | null>(null);
  const [method, setMethod] = useState<PayMethod>("CASH");
  const product = products.find((p) => p.code === code) ?? products[0];
  // The unit price starts as the selling price of the chosen product.
  const priceText = price && price.code === product?.code ? price.text : String(product?.sellingPrice ?? "");
  return (
    <SaleDialog
      {...props}
      kind="product"
      title="Product sale"
      okMessage="Product sale saved"
      customerOptional
      fieldNames={["walkInName", "items", "payMethod"]}
      afterCustomer={(ctx) => (
        <div className="field span2">
          <label htmlFor="sale-walkin">…or walk-in name</label>
          <input
            id="sale-walkin"
            value={walkIn}
            placeholder="Leave customer empty for walk-in (cash only)"
            className={ctx.invalid("walkInName") ? "invalid" : undefined}
            onChange={(e) => setWalkIn(e.target.value)}
          />
          {ctx.err("walkInName")}
        </div>
      )}
      build={(ctx) =>
        product
          ? {
              walkInName: ctx.customer ? undefined : walkIn.trim() || undefined,
              items: [
                {
                  productCode: product.code,
                  qty: int(qty),
                  unitPrice: priceText.trim() === "" ? undefined : Number(priceText),
                },
              ],
              payMethod: method,
            }
          : null
      }
      fields={(ctx) => (
        <>
          <div className="field req span2">
            <label htmlFor="sale-product">Product</label>
            <select
              id="sale-product"
              value={product?.code ?? ""}
              className={ctx.invalid("items") ? "invalid" : undefined}
              onChange={(e) => setCode(e.target.value)}
            >
              {products.map((p) => (
                <option key={p.code} value={p.code}>
                  {p.name} – {formatMoney(p.sellingPrice)} ({p.stockQty} in stock)
                </option>
              ))}
            </select>
            {ctx.err("items")}
          </div>
          <NumberField
            id="sale-qty"
            name="qty"
            label="Qty"
            value={qty}
            onChange={setQty}
            ctx={ctx}
            required
            min={1}
          />
          <div className="field">
            <label htmlFor="sale-price">Unit price (Rs.)</label>
            <input
              id="sale-price"
              type="number"
              min="0"
              className="num"
              value={priceText}
              onChange={(e) => product && setPrice({ code: product.code, text: e.target.value })}
            />
          </div>
        </>
      )}
      afterPaid={
        <div className="field">
          <label htmlFor="sale-method">Paid by</label>
          <select id="sale-method" value={method} onChange={(e) => setMethod(e.target.value as PayMethod)}>
            {PAY_METHODS.map((m) => (
              <option key={m.value} value={m.value}>
                {m.label}
              </option>
            ))}
          </select>
        </div>
      }
      calc={(p) => (
        <CalcRows
          rows={[
            ...p.lines.map((l) => [`${l.qty} × ${l.label}`, formatMoney(l.lineAmount)] as [string, string]),
            ["Total", formatMoney(p.amount), true],
          ]}
        />
      )}
    />
  );
}

// ------------------------------------------------------------------ Collect empties

export function CollectEmptiesDialog(props: NewSaleProps) {
  const [bottle, setBottle] = useDefaultBottle();
  // null = not typed: the empties waiting to be collected.
  const [qty, setQty] = useState<string | null>(null);
  return (
    <SaleDialog
      {...props}
      kind="collect"
      title="Collect empties (left at door)"
      okMessage="Empties collected"
      showPaid={false}
      fieldNames={["bottleTypeCode", "qty"]}
      build={(ctx) => ({
        bottleTypeCode: bottle,
        qty: int(qty ?? String(held(ctx.customer, bottle, "toCollect") || "")),
      })}
      fields={(ctx) => (
        <>
          <BottleField value={bottle} onChange={setBottle} ctx={ctx} />
          <NumberField
            id="sale-qty"
            name="qty"
            label="Empties collected"
            value={qty ?? String(held(ctx.customer, bottle, "toCollect") || "")}
            onChange={setQty}
            ctx={ctx}
            required
            min={1}
          />
        </>
      )}
      calc={(_p, ctx) => (
        <p className="small">
          Waiting to be collected: <b>{held(ctx.customer, bottle, "toCollect")}</b>. Collected empties go to
          empty stock.
        </p>
      )}
    />
  );
}

// ------------------------------------------------------------------ Customer-damaged bottle

export function CustomerDamageDialog(props: NewSaleProps) {
  const [bottle, setBottle] = useDefaultBottle();
  const [qty, setQty] = useState("1");
  const [reason, setReason] = useState("");
  const [mode, setMode] = useState<"REPLACE" | "REDUCE">("REPLACE");
  const [giveFilled, setGiveFilled] = useState(true);
  return (
    <SaleDialog
      {...props}
      kind="damage"
      title="Customer-damaged bottle"
      okMessage="Customer damage saved – bottle written off"
      fieldNames={["bottleTypeCode", "qty", "reason", "mode"]}
      build={() => ({ bottleTypeCode: bottle, qty: int(qty), mode, giveFilled, reason: reason.trim() })}
      fields={(ctx) => (
        <>
          <BottleField value={bottle} onChange={setBottle} ctx={ctx} />
          <NumberField
            id="sale-qty"
            name="qty"
            label="Damaged"
            value={qty}
            onChange={setQty}
            ctx={ctx}
            required
            min={1}
          />
          <div className="field req full">
            <label htmlFor="sale-reason">What happened</label>
            <input
              id="sale-reason"
              value={reason}
              placeholder="e.g. Bottle cracked – dropped by customer"
              className={ctx.invalid("reason") ? "invalid" : undefined}
              onChange={(e) => setReason(e.target.value)}
            />
            {ctx.err("reason")}
          </div>
          <div className="full radio-row">
            <label className="check">
              <input
                type="radio"
                name="sale-mode"
                checked={mode === "REPLACE"}
                onChange={() => setMode("REPLACE")}
              />{" "}
              Customer buys a new bottle (keeps holding)
            </label>
            <label className="check">
              <input
                type="radio"
                name="sale-mode"
                checked={mode === "REDUCE"}
                onChange={() => setMode("REDUCE")}
              />{" "}
              Not replaced – reduce holding
            </label>
          </div>
          {mode === "REPLACE" && (
            <label className="check full">
              <input type="checkbox" checked={giveFilled} onChange={(e) => setGiveFilled(e.target.checked)} />{" "}
              Give the new bottle filled and charge water
            </label>
          )}
        </>
      )}
      calc={(p) => {
        const l = p.lines[0];
        return (
          <>
            <CalcRows
              rows={[
                ["New bottle (deposit)", formatMoney(p.deposit)],
                [
                  l?.filled ? waterLabel(l.filled, l.unitPrice, l.agreedPrice) : "Water",
                  formatMoney(p.water),
                ],
                ["Total", formatMoney(p.amount), true],
              ]}
            />
            <p className="small muted">The damaged bottle is written off.</p>
          </>
        );
      }}
    />
  );
}

// ------------------------------------------------------------------ Settle bottles owed

export function SettleOwedDialog(props: NewSaleProps) {
  const [bottle, setBottle] = useDefaultBottle();
  // null = not typed: the bottles owed.
  const [qty, setQty] = useState<string | null>(null);
  const [method, setMethod] = useState<"RETURN" | "DEPOSIT">("RETURN");
  const qtyOf = (c: CustomerDetail | null) => qty ?? String(held(c, bottle, "owed") || "");
  return (
    <SaleDialog
      {...props}
      kind="owed"
      title="Settle bottles owed"
      okMessage="Bottles owed settled"
      fieldNames={["bottleTypeCode", "qty", "method"]}
      build={(ctx) => ({ bottleTypeCode: bottle, qty: int(qtyOf(ctx.customer)), method })}
      fields={(ctx) => (
        <>
          <BottleField value={bottle} onChange={setBottle} ctx={ctx} />
          <NumberField
            id="sale-qty"
            name="qty"
            label="Bottles"
            value={qtyOf(ctx.customer)}
            onChange={setQty}
            ctx={ctx}
            required
            min={1}
          />
          <div className="full radio-row">
            <label className="check">
              <input
                type="radio"
                name="sale-method"
                checked={method === "RETURN"}
                onChange={() => setMethod("RETURN")}
              />{" "}
              Bottles returned
            </label>
            <label className="check">
              <input
                type="radio"
                name="sale-method"
                checked={method === "DEPOSIT"}
                onChange={() => setMethod("DEPOSIT")}
              />{" "}
              Customer pays the deposit and keeps them
            </label>
          </div>
        </>
      )}
      calc={(p, ctx) => {
        const l = p.lines[0];
        return (
          <>
            <p className="small">
              Owed now: <b>{held(ctx.customer, bottle, "owed")}</b>.
            </p>
            {method === "DEPOSIT" && l && (
              <CalcRows
                rows={[
                  [`Deposit ${l.depositCount} × ${formatMoney(l.depositUnit)}`, formatMoney(p.amount), true],
                ]}
              />
            )}
          </>
        );
      }}
    />
  );
}

/** The pop-up of a "+ New ▾" entry. */
export function NewSaleDialog({ kind, ...props }: NewSaleProps & { kind: SaleKind }) {
  switch (kind) {
    case "first":
      return <FirstPurchaseDialog {...props} />;
    case "extra":
      return <ExtraOrderDialog {...props} />;
    case "product":
      return <ProductSaleDialog {...props} />;
    case "collect":
      return <CollectEmptiesDialog {...props} />;
    case "damage":
      return <CustomerDamageDialog {...props} />;
    case "owed":
      return <SettleOwedDialog {...props} />;
  }
}
