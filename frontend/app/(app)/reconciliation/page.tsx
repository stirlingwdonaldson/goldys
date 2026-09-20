import type { Metadata } from "next";
import { EmptyState } from "@/components/states/empty-state";

export const metadata: Metadata = { title: "Reconciliation" };

export default function ReconciliationPage() {
  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Reconciliation</h1>
        <p className="text-sm text-muted-foreground">
          Field-level conflicts between sources, shown exception-first.
        </p>
      </div>
      <EmptyState
        title="No conflicts to review"
        description="When two sources disagree on the same fact, the exception appears here for you to resolve."
      />
    </div>
  );
}
