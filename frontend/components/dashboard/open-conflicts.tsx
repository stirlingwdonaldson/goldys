import Link from "next/link";
import { Badge } from "@/components/ui/badge";
import type { ReconciliationException } from "@/lib/api";

function productHref(ex: ReconciliationException): string {
  const date = ex.id.split(":")[0];
  return `/reconciliation?date=${encodeURIComponent(date)}&product=${encodeURIComponent(ex.recordId)}`;
}

function statusBadge(status: ReconciliationException["status"]) {
  return (
    <Badge variant={status === "conflict" ? "secondary" : "outline"}>
      {status === "conflict" ? "Conflict" : "Missing data"}
    </Badge>
  );
}

/** The dashboard's short list of open conflicts, each deep-linking into its drill-in. */
export function OpenConflicts({
  sales,
  products,
}: {
  sales: ReconciliationException[];
  products: ReconciliationException[];
}) {
  const salesItems = sales.slice(0, 5);
  const productItems = products.slice(0, 5);

  if (salesItems.length === 0 && productItems.length === 0) {
    return <p className="text-sm text-muted-foreground">No open conflicts — everything reconciles.</p>;
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="rounded-lg border">
        {salesItems.map((ex, i) => (
          <Link
            key={ex.id}
            href={`/reconciliation?record=${encodeURIComponent(ex.recordId)}`}
            className={`flex items-center justify-between gap-4 p-3 text-left hover:bg-accent ${i > 0 ? "border-t" : ""}`}
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{ex.entity}</p>
              <p className="truncate text-xs text-muted-foreground">{ex.field}</p>
            </div>
            {statusBadge(ex.status)}
          </Link>
        ))}
        {productItems.map((ex) => (
          <Link
            key={ex.id}
            href={productHref(ex)}
            className={`flex items-center justify-between gap-4 p-3 text-left hover:bg-accent ${salesItems.length > 0 ? "border-t" : ""}`}
          >
            <div className="min-w-0">
              <p className="truncate text-sm font-medium">{ex.entity}</p>
              <p className="truncate text-xs text-muted-foreground">
                {ex.sources.map((s) => `${s.source}: ${s.value ?? "no data"}`).join(" · ")}
              </p>
            </div>
            {statusBadge(ex.status)}
          </Link>
        ))}
      </div>
      <Link
        href="/reconciliation"
        className="text-sm text-muted-foreground underline-offset-4 hover:underline"
      >
        View all →
      </Link>
    </div>
  );
}
