/*
 * Document status steps (prototype .steps-bar), e.g. Draft → Approved → Sent → Received.
 * Steps before `current` are done (green), `current` is highlighted.
 */
import { cn } from "@/lib/utils";

export function StepBar({ steps, current }: { steps: string[]; current: string }) {
  const at = steps.indexOf(current);
  return (
    <div className="steps-bar" role="list">
      {steps.map((s, i) => (
        <span
          key={s}
          role="listitem"
          aria-current={i === at ? "step" : undefined}
          className={cn(i < at && "done", i === at && "now")}
        >
          {s}
        </span>
      ))}
    </div>
  );
}
