"use client";

/* Billing & Payments (M11) – see features/billing/Billing.tsx. ?pay=, ?statement= and ?invoice= open the pop-ups. */
import { Suspense } from "react";
import { Billing } from "@/features/billing/Billing";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <Billing />
    </Suspense>
  );
}
