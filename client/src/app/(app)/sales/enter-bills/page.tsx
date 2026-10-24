"use client";

/* Enter Bills (M07): the daily paper-bill grid – see features/enter-bills/EnterBills.tsx. ?cust=C0017 = one row. */
import { Suspense } from "react";
import { EnterBills } from "@/features/enter-bills/EnterBills";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <EnterBills />
    </Suspense>
  );
}
