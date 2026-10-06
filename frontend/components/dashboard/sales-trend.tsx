import type { SalesTrendPoint } from "@/lib/api";

function toNumber(v: number | string | null): number | null {
  if (v == null) return null;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : null;
}

/** A dependency-free line chart of resolved daily sales, plus the window total. */
export function SalesTrend({ points }: { points: SalesTrendPoint[] }) {
  const values = points
    .map((p) => toNumber(p.total))
    .filter((v): v is number => v != null);

  if (values.length === 0) {
    return <p className="text-sm text-muted-foreground">No resolved sales yet.</p>;
  }

  const max = Math.max(1, ...values);
  const total = values.reduce((a, b) => a + b, 0);
  const polyline = values
    .map((v, i) => `${(i / Math.max(1, values.length - 1)) * 100},${100 - (v / max) * 100}`)
    .join(" ");

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-baseline justify-between">
        <span className="text-2xl font-semibold">${total.toFixed(2)}</span>
        <span className="text-xs text-muted-foreground">last {points.length} days</span>
      </div>
      <div role="img" aria-label="Resolved daily sales trend" className="h-24">
        <svg
          viewBox="0 0 100 100"
          preserveAspectRatio="none"
          className="h-full w-full"
          aria-hidden="true"
        >
          <polyline
            points={polyline}
            fill="none"
            stroke="#2563eb"
            strokeWidth="2"
            vectorEffect="non-scaling-stroke"
          />
        </svg>
      </div>
    </div>
  );
}
