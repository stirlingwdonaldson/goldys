import type { Metadata } from "next";
import { Badge } from "@/components/ui/badge";

export const metadata: Metadata = { title: "Connectors" };

const sources = [
  { name: "Lightspeed", kind: "POS", path: "Back-office scrape" },
  { name: "Cooking the Books", kind: "Accounting", path: "Invoice export (CSV/XLSX)" },
  { name: "OpenTable", kind: "Reservations", path: "GuestCenter report pull" },
  { name: "Deputy", kind: "Rostering", path: "OAuth REST API" },
];

export default function ConnectorsPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Connectors</h1>
        <p className="text-sm text-muted-foreground">
          In-scope Phase 1 sources. Run status appears here once ingestion starts.
        </p>
      </div>
      <div className="rounded-lg border">
        {sources.map((source, i) => (
          <div
            key={source.name}
            className={`flex items-center justify-between gap-4 p-4 ${i > 0 ? "border-t" : ""}`}
          >
            <div>
              <p className="text-sm font-medium">{source.name}</p>
              <p className="text-xs text-muted-foreground">
                {source.kind} &middot; {source.path}
              </p>
            </div>
            <Badge variant="secondary">No ingestion runs yet</Badge>
          </div>
        ))}
      </div>
    </div>
  );
}
