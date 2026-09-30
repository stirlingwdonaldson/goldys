"use client";

import { useDemoMode } from "@/lib/demo-mode";
import { AwaitingData } from "@/components/states/awaiting-data";
import { ResolutionRulesContent } from "@/components/rules/resolution-rules-content";

export default function ResolutionRulesPage() {
  const { demo } = useDemoMode();

  if (!demo) {
    return (
      <div className="flex flex-col gap-6">
        <div>
          <h1 className="text-xl font-semibold">Resolution rules</h1>
          <p className="text-sm text-muted-foreground">
            How disagreements between sources are resolved, per field.
          </p>
        </div>
        <AwaitingData
          label="Resolution rules"
          description="Author standing rules for how disagreements are resolved, per field."
          reason="Not available with live data yet — the rule engine is in progress."
        />
      </div>
    );
  }

  return <ResolutionRulesContent />;
}
