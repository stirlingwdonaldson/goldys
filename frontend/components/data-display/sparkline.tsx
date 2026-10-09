/**
 * A tiny trend line for KPI tiles. Nulls (unresolved days) break the line rather
 * than being drawn as zero, so a gap reads as "unknown", never as "nothing sold".
 */
export function Sparkline({ values, className }: { values: (number | null)[]; className?: string }) {
  const known = values.filter((v): v is number => v != null);
  if (known.length < 2) return null;
  const w = 200;
  const h = 36;
  const pad = 3;
  const min = Math.min(...known);
  const max = Math.max(...known);
  const x = (i: number) => pad + (i * (w - pad * 2)) / (values.length - 1);
  const y = (v: number) => h - pad - ((v - min) / (max - min || 1)) * (h - pad * 2);

  // Split into runs of consecutive known points.
  const runs: string[] = [];
  let current: string[] = [];
  values.forEach((v, i) => {
    if (v == null) {
      if (current.length) runs.push(current.join(" "));
      current = [];
    } else {
      current.push(`${x(i)},${y(v)}`);
    }
  });
  if (current.length) runs.push(current.join(" "));

  const lastIndex = values.length - 1;
  const last = values[lastIndex];

  return (
    <svg
      viewBox={`0 0 ${w} ${h}`}
      preserveAspectRatio="none"
      className={className ?? "h-9 w-full"}
      aria-hidden="true"
    >
      {runs.map((pts) => (
        <polyline
          key={pts}
          points={pts}
          fill="none"
          stroke="hsl(var(--chart-1))"
          strokeWidth={1.5}
          vectorEffect="non-scaling-stroke"
          strokeLinejoin="round"
        />
      ))}
      {last != null ? <circle cx={x(lastIndex)} cy={y(last)} r={2.5} fill="hsl(var(--chart-1))" /> : null}
    </svg>
  );
}
