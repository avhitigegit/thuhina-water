"use client";

/* Reports (M12) – see features/reports/Reports.tsx. ?r=profit opens a report. */
import { Suspense } from "react";
import { Reports } from "@/features/reports/Reports";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <Reports />
    </Suspense>
  );
}
