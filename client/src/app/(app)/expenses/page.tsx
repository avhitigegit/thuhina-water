"use client";

/* Expenses (M15) – see features/expenses/Expenses.tsx. ?new=1 opens Add expense, ?month=2026-09 opens that month. */
import { Suspense } from "react";
import { Expenses } from "@/features/expenses/Expenses";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <Expenses />
    </Suspense>
  );
}
