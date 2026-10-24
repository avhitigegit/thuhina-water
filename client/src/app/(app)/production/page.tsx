"use client";

/* Filling Factory (M10) – see features/production/Production.tsx. ?send=1 opens Send empties. */
import { Suspense } from "react";
import { Production } from "@/features/production/Production";

export default function Page() {
  // useSearchParams needs a Suspense boundary in a static page.
  return (
    <Suspense fallback={<div className="card muted">Loading…</div>}>
      <Production />
    </Suspense>
  );
}
