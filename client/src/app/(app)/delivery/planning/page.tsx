"use client";

/* Delivery Planning (M08, Admin) – see features/delivery/Planning.tsx. ?date=2026-10-13 opens that day. */
import { Suspense } from "react";
import { Planning } from "@/features/delivery/Planning";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <Planning />
    </Suspense>
  );
}
