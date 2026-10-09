/* Money as "Rs. 1,350.00", right-aligned digits (NFR-07). */
import { formatMoney } from "@/lib/format";
import { cn } from "@/lib/utils";

export function MoneyText({
  value,
  className,
}: {
  value: number | string | null | undefined;
  className?: string;
}) {
  return <span className={cn("num", className)}>{formatMoney(value)}</span>;
}
