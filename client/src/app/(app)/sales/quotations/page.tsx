"use client";

/* Customer Quotations (M16) – see features/quotations/Quotations.tsx. ?id= opens one, ?new=1&cust= starts one. */
import { Suspense } from "react";
import { Quotations } from "@/features/quotations/Quotations";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <Quotations />
    </Suspense>
  );
}
