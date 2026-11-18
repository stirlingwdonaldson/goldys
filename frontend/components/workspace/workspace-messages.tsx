"use client";

import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { AnswerMarkdown, AnswerText } from "@/components/ask-goldys/answer-text";
import { AnswerBlock } from "@/components/ask-goldys/answer-block";
import type { DashboardDraft } from "@/components/ask-goldys/types";
import { useApi } from "@/lib/demo-mode";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import { parseWidgetSpec } from "@/components/widgets/parse";
import { TrustIndicator } from "@/components/trust/trust-indicator";
import type { WorkspaceJob } from "@/lib/api/workspace";

type WorkspaceMessage = WorkspaceJob["messages"][number];

/** A queued follow-up starts another exchange; never fold its answer into the previous one. */
function exchanges(messages: WorkspaceMessage[]) {
  const groups: WorkspaceMessage[][] = [[]];
  for (const message of messages) {
    if (message.role === "user") groups.push([]);
    groups[groups.length - 1].push(message);
  }
  return groups.filter(group => group.length);
}

export function WorkspaceMessages({ job }: { job: WorkspaceJob }) {
  return <section aria-label="Investigation history" className="space-y-6">
    <div className="ml-auto max-w-full space-y-1 rounded-2xl bg-muted px-4 py-3 sm:max-w-xl"><h3 className="text-xs font-medium text-muted-foreground">Investigation request</h3><p className="whitespace-pre-wrap break-words text-sm">{job.prompt}</p></div>
    {exchanges(job.messages).map(group => <WorkspaceExchange key={group[0].id} messages={group} />)}
  </section>;
}

function WorkspaceExchange({ messages }: { messages: WorkspaceMessage[] }) {
  const latest = messages.findLast(message => message.role === "assistant" && message.text.trim());
  const activity = messages.filter(message => message.role !== "user" && message !== latest && (message.text || message.reporting));
  const qualified = messages.filter(message => message.reporting && (message.reporting.notices.length || message.reporting.provenance.some(source => source.missingPeriods.length || source.trust)));
  return <div className="min-w-0 space-y-3">
    {messages.filter(message => message.role === "user").map(message => <p key={message.id} className="ml-auto max-w-full whitespace-pre-wrap break-words rounded-2xl bg-muted px-4 py-3 text-sm sm:max-w-xl">{message.text}</p>)}
    {activity.length ? <Collapsible className="min-w-0">
      <CollapsibleTrigger asChild><Button variant="ghost" size="sm" className="group gap-2 text-muted-foreground">Investigation activity ({activity.length})<ChevronDown aria-hidden="true" className="size-4 group-data-[state=open]:rotate-180" /></Button></CollapsibleTrigger>
      <CollapsibleContent className="space-y-4 border-l pl-4 pt-3">
        {activity.map(message => <article key={message.id} className="min-w-0 space-y-3">
          <h3 className="text-xs font-medium text-muted-foreground">{message.role === "tool" ? "Reporting result" : "Goldy’s update"}</h3>
          {message.text ? <AnswerMarkdown text={message.text} /> : null}
          {message.reporting ? <ReportingResult message={message} /> : null}
        </article>)}
      </CollapsibleContent>
    </Collapsible> : null}
    {latest ? <article key={latest.id} className="min-w-0 space-y-3"><h3 className="text-xs font-medium text-muted-foreground">Goldy’s</h3><AnswerText text={latest.text} />{latest.reporting ? <ReportingResult message={latest} /> : null}</article> : null}
    {qualified.length ? <Collapsible className="min-w-0">
      <CollapsibleTrigger asChild><Button variant="ghost" size="sm" className="group gap-2 text-muted-foreground">Data notes<ChevronDown aria-hidden="true" className="size-4 group-data-[state=open]:rotate-180" /></Button></CollapsibleTrigger>
      <CollapsibleContent className="space-y-4 border-l pl-4 pt-3">
        {qualified.map(message => <ReportingQualifications key={message.id} message={message} />)}
      </CollapsibleContent>
    </Collapsible> : null}
    {messages.map(message => message.draft ? <WorkspaceDraft key={message.id} draft={message.draft} /> : null)}
  </div>;
}

function ReportingResult({ message }: { message: WorkspaceMessage }) {
  const result = message.reporting;
  if (!result) return null;
  const widget = parseWidgetSpec(result.widget);
  return <div className="min-w-0 space-y-3">
    {widget ? <WidgetRenderer widget={widget} /> : <p role="alert">Reporting result unavailable.</p>}
    {result.provenance.map((source, index) => <div key={index} className="text-xs text-muted-foreground">
      <p>{source.metric} · {source.range.from} to {source.range.to} · {source.sourceDomain}</p>
      <p>Data as of {source.dataFreshness ?? "unknown"} · Calculation {source.calculationVersion}</p>
    </div>)}
  </div>;
}

/** Full qualifications are retained inside the exchange’s separate Data notes disclosure. */
function ReportingQualifications({ message }: { message: WorkspaceMessage }) {
  const result = message.reporting;
  if (!result) return null;
  return <div className="space-y-2 text-xs text-muted-foreground">
    {result.notices.length ? <ul aria-label="Reporting qualifications" className="list-disc space-y-1 pl-5">{result.notices.map((notice, index) => <li key={index}>{notice}</li>)}</ul> : null}
    {result.provenance.map((source, index) => <div key={index}>
      {source.missingPeriods.length ? <p>{source.metric} — Missing periods: {source.missingPeriods.join(", ")}</p> : null}
      {source.trust ? <span className="inline-flex items-center gap-2">{source.metric}<TrustIndicator trust={source.trust} value={source.trust.state === "NOT_RECEIVED" ? null : 1} /></span> : null}
    </div>)}
  </div>;
}

function WorkspaceDraft({ draft }: { draft: DashboardDraft }) {
  const api = useApi();
  return <AnswerBlock api={api} summary="" error={null} answer={{ widgets: [], notices: [], trace: [], asOf: "", draft }} />;
}
