"use client";

import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import { parseWidgetSpec } from "@/components/widgets/parse";
import { TrustIndicator } from "@/components/trust/trust-indicator";
import type { WorkspaceJob } from "@/lib/api/workspace";

export function WorkspaceMessages({ job }: { job: WorkspaceJob }) {
  return <section aria-label="Investigation history" className="space-y-6">
    <div className="space-y-1"><h3 className="text-xs font-medium text-muted-foreground">Investigation request</h3><p className="whitespace-pre-wrap break-words text-sm">{job.prompt}</p></div>
    {job.messages.map(message => {
      const result = message.reporting;
      const widget = result ? parseWidgetSpec(result.widget) : null;
      return <article key={message.id} className="space-y-3">
        <h3 className="text-xs font-medium text-muted-foreground">{message.role === "user" ? "You" : message.role === "tool" ? "Reporting result" : "Goldy’s"}</h3>
        {message.text ? <p className="whitespace-pre-wrap break-words text-sm leading-relaxed">{message.text}</p> : null}
        {result ? <>
          {widget ? <WidgetRenderer widget={widget} /> : <p role="alert">Reporting result unavailable.</p>}
          {result.notices.length ? <ul aria-label="Reporting qualifications" className="list-disc space-y-1 pl-5 text-sm text-muted-foreground">{result.notices.map((notice, index) => <li key={index}>{notice}</li>)}</ul> : null}
          {result.provenance.map((source, index) => <div key={index} className="space-y-1 text-xs text-muted-foreground">
            <p>{source.metric} · {source.range.from} to {source.range.to} · {source.sourceDomain}</p>
            <p>Data as of {source.dataFreshness ?? "unknown"} · Calculation {source.calculationVersion}</p>
            {source.missingPeriods.length ? <p>Missing periods: {source.missingPeriods.join(", ")}</p> : null}
            {source.trust ? <TrustIndicator trust={source.trust} value={source.trust.state === "NOT_RECEIVED" ? null : 1} /> : null}
          </div>)}
        </> : null}
      </article>;
    })}
  </section>;
}
