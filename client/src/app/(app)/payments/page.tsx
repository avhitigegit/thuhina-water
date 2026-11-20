"use client";

/* Payments – one home for money in and out; see features/payments/Payments.tsx. ?pay=, ?supplier= and ?factory= open the pop-ups. */
import { Suspense } from "react";
import { Payments } from "@/features/payments/Payments";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <Payments />
    </Suspense>
  );
}
