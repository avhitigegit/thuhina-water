/* Words of the Stock page (prototype inventory/inventory.html, store.js BUCKET_LABEL / DAMAGE_LOC). */
import type { BadgeKind } from "@/components/shared/StatusBadge";
import type { BottleStock, DamageLocation } from "@/lib/api/types";

export type BucketKey = "empty" | "factory" | "filled" | "customers" | "writtenOff";

/** The five buckets in the prototype's order, with the field of BottleStock / MovementRow. */
export const BUCKETS: { key: BucketKey; label: string }[] = [
  { key: "empty", label: "Empty in store" },
  { key: "factory", label: "At factory" },
  { key: "filled", label: "Filled in store" },
  { key: "customers", label: "With customers" },
  { key: "writtenOff", label: "Written off" },
];

export const DAMAGE_LOCATIONS: { value: DamageLocation; label: string }[] = [
  { value: "EMPTY_IN_STORE", label: "Empty in store" },
  { value: "FILLED_IN_STORE", label: "Filled in store" },
  { value: "AT_FACTORY", label: "At factory" },
  { value: "DURING_DELIVERY", label: "During delivery" },
];

/** Buckets a stock adjustment can change (server: EMPTY / FILLED / FACTORY). */
export const ADJUSTABLE: { value: "EMPTY" | "FILLED" | "FACTORY"; key: BucketKey; label: string }[] = [
  { value: "EMPTY", key: "empty", label: "Empty in store" },
  { value: "FILLED", key: "filled", label: "Filled in store" },
  { value: "FACTORY", key: "factory", label: "At factory" },
];

export const KIND_LABEL: Record<string, { label: string; kind: BadgeKind }> = {
  DAMAGED: { label: "Damaged", kind: "bad" },
  LOST: { label: "Lost", kind: "warn" },
  COUNT: { label: "Count adjustment", kind: "info" },
};

/** Product stock at or below this shows in red (prototype). */
export const LOW_PRODUCT_STOCK = 3;

export function isBottleLow(b: BottleStock): boolean {
  return b.minFilled != null && b.filled < b.minFilled;
}
