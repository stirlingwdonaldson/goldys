import type { TopSeller } from "@/lib/api";

function fmt(v: number | string): string {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? String(Number(n.toFixed(2))) : "—";
}

function money(v: number | string): string {
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? `$${n.toFixed(2)}` : "—";
}

/** Ranked list of the top-selling products by amount. */
export function TopSellers({ items }: { items: TopSeller[] }) {
  if (items.length === 0) {
    return <p className="text-sm text-muted-foreground">No product sales yet.</p>;
  }
  return (
    <ol className="divide-y">
      {items.map((p, i) => (
        <li key={p.name} className="flex items-baseline justify-between gap-3 py-2">
          <span className="text-sm">
            <span className="text-muted-foreground">{i + 1}.</span> {p.name}
          </span>
          <span className="text-sm tabular-nums text-muted-foreground">
            {fmt(p.quantitySold)} × {money(p.amount)}
          </span>
        </li>
      ))}
    </ol>
  );
}
