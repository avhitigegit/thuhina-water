import { findRoute } from "@/lib/routes";

/** Placeholder for a screen that a later module builds (Master_Task_Breakdown.md). */
export function ComingSoon({ path }: { path: string }) {
  const route = findRoute(path);
  return (
    <div className="card">
      <h2>Coming in {route?.buildModule ?? "a later module"}</h2>
      <p className="muted" style={{ margin: 0 }}>
        This screen is built in module {route?.buildModule} of the build plan. The layout and wording will
        follow the approved prototype page.
      </p>
    </div>
  );
}
