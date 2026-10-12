"use client";

/* Daily Delivery List (M08) – see features/delivery/DailyList.tsx. ?date=2026-10-12 opens that day. */
import { Suspense } from "react";
import { DailyList } from "@/features/delivery/DailyList";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <DailyList />
    </Suspense>
  );
}
