/* A customer's water price: "Rs. 270.00 agreed standard Rs. 300.00" (prototype UI.priceText). */
import { MoneyText } from "@/components/shared/MoneyText";
import { Badge } from "@/components/shared/StatusBadge";
import type { WaterPriceView } from "@/lib/api/types";
import { formatDate, formatMoney } from "@/lib/format";

export function PriceText({ price, showNext = false }: { price: WaterPriceView; showNext?: boolean }) {
  if (price.price == null) return <span className="muted">–</span>;
  return (
    <>
      <MoneyText value={price.price} />
      {price.agreed && (
        <>
          {" "}
          <Badge kind="info">agreed</Badge>{" "}
          <span className="small muted">
            standard {price.standard != null ? formatMoney(price.standard) : "–"}
          </span>
        </>
      )}
      {showNext && price.next && (
        <div className="small" style={{ color: "var(--brand)" }}>
          → {formatMoney(price.next.price)} from {formatDate(price.next.from)}
        </div>
      )}
    </>
  );
}
