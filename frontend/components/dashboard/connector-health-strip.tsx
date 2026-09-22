import Link from "next/link";
import { ConnectorStatusBadge } from "@/components/connectors/connector-status-badge";
import type { ConnectorStatus } from "@/lib/api";

/** A compact per-source status list for the dashboard, linking to the connectors screen. */
export function ConnectorHealthStrip({ statuses }: { statuses: ConnectorStatus[] }) {
  if (statuses.length === 0) {
    return <p className="text-sm text-muted-foreground">No connector runs yet.</p>;
  }

  return (
    <div className="rounded-lg border">
      {statuses.map((c, i) => (
        <Link
          key={c.source}
          href="/connectors"
          className={`flex items-center justify-between gap-4 p-3 hover:bg-accent ${i > 0 ? "border-t" : ""}`}
        >
          <div className="min-w-0">
            <p className="truncate text-sm font-medium">{c.source}</p>
            <p className="truncate text-xs text-muted-foreground">
              {c.lastRunAt ? `Last run ${new Date(c.lastRunAt).toLocaleString()}` : "Never run"}
            </p>
          </div>
          <ConnectorStatusBadge status={c.status} />
        </Link>
      ))}
    </div>
  );
}
