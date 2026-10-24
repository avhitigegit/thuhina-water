/* Simple bar chart (prototype UI.bars): one bar per point, its value on hover, labels underneath. */

export interface BarPoint {
  key: string;
  label: string;
  value: number;
  /** Hover text, e.g. "05/10/2026: Rs. 5,470.00". */
  title: string;
  /** Text shown above the bar on hover. */
  shown: string;
}

export function BarChart({ points, label }: { points: BarPoint[]; label: string }) {
  const max = Math.max(1, ...points.map((p) => p.value));
  return (
    <div role="img" aria-label={label}>
      <div className="bars">
        {points.map((p) => (
          <div
            key={p.key}
            className="bar"
            style={{ height: `${Math.max(1, (Math.max(0, p.value) / max) * 100)}%` }}
            title={p.title}
          >
            <span>{p.shown}</span>
          </div>
        ))}
      </div>
      <div className="bar-labels">
        {points.map((p) => (
          <span key={p.key}>{p.label}</span>
        ))}
      </div>
    </div>
  );
}
