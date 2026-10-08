"use client";

import { useState } from "react";
import Link from "next/link";
import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import { parseWidgetSpecs } from "@/components/widgets/parse";
import { Button } from "@/components/ui/button";
import type { Api, DashboardDocument, SavedWidget, SaveDashboardInput } from "@/lib/api/types";
import type { AnswerPayload, MetricProvenance } from "./types";

interface AnswerBlockProps {
  summary: string;
  answer: AnswerPayload | null;
  error: string | null;
  api: Api;
}

/** A compact textual summary of a draft widget: render type + metric count (drafts are SavedWidgets). */
function draftWidgetSummary(widget: SavedWidget): string {
  const count = widget.queries.length;
  return `${widget.renderType} · ${count} ${count === 1 ? "metric" : "metrics"}`;
}

/** A human-readable one-liner for a metric's provenance: range + freshness + unresolved periods. */
function provenanceSummary(p: MetricProvenance): string {
  const parts = [
    `${p.range.from} → ${p.range.to}`,
    `data as of ${p.dataFreshness}`,
  ];
  if (p.missingPeriods.length) parts.push(`missing ${p.missingPeriods.join(", ")}`);
  return parts.join(" · ");
}

/** The answer anatomy: summary + widgets + "How I got this" trace + "as of" + notices + draft. */
export function AnswerBlock({ summary, answer, error, api }: AnswerBlockProps) {
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<DashboardDocument | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const draft = answer?.draft ?? null;

  async function saveDraft() {
    if (!draft || saving) return;
    setSaving(true);
    setSaveError(null);
    try {
      const input: SaveDashboardInput = {
        title: draft.title,
        description: draft.description ?? null,
        layout: "grid",
        filters: draft.filters,
        visibility: "PRIVATE",
        widgets: draft.widgets,
      };
      const doc = draft.dashboardId
        ? await api.updateDashboard(draft.dashboardId, input)
        : await api.saveDashboard(input);
      setSaved(doc);
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : "Couldn't save the dashboard.");
    } finally {
      setSaving(false);
    }
  }

  if (error) {
    return <PermissionDenied subject="this data" message={error} />;
  }
  return (
    <div className="space-y-4">
      {summary ? <p className="text-sm">{summary}</p> : null}

      {answer?.notices.length ? (
        <p className="rounded-md bg-amber-50 px-3 py-2 text-sm text-amber-800">
          {answer.notices.join(" ")}{" "}
          <a href="/reconciliation" className="underline">
            Reconcile these first.
          </a>
        </p>
      ) : null}

      {answer ? (
        parseWidgetSpecs(answer.widgets).map((w) => <WidgetRenderer key={w.id} widget={w} />)
      ) : null}

      {draft ? (
        <div className="space-y-3 rounded-lg border p-4">
          <div>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
              Dashboard draft
            </p>
            <h3 className="text-base font-semibold">{draft.title}</h3>
            {draft.description ? (
              <p className="text-sm text-muted-foreground">{draft.description}</p>
            ) : null}
          </div>

          {draft.widgets.length ? (
            <ul className="space-y-1 text-sm">
              {draft.widgets.map((w) => (
                <li key={w.id}>{draftWidgetSummary(w)}</li>
              ))}
            </ul>
          ) : null}

          {saved ? (
            <p className="text-sm text-green-700">
              Dashboard saved:{" "}
              <Link href="/dashboards" className="underline">
                {saved.title}
              </Link>
            </p>
          ) : (
            <div className="space-y-2">
              <Button onClick={saveDraft} disabled={saving}>
                {saving ? "Saving…" : "Save dashboard"}
              </Button>
              {saveError ? (
                <p role="alert" className="text-sm text-destructive">
                  {saveError}
                </p>
              ) : null}
            </div>
          )}
        </div>
      ) : null}

      {answer?.trace.length ? (
        <details className="text-xs text-muted-foreground">
          <summary className="cursor-pointer underline">How I got this</summary>
          <div className="mt-1 space-y-2">
            {answer.trace.map((t, i) => (
              <div key={i} className="space-y-1">
                <p>
                  {t.tool} — {t.description}
                </p>
                {t.provenance.length ? (
                  <ul className="ml-3 space-y-1 border-l pl-3">
                    {t.provenance.map((p, j) => (
                      <li key={j}>
                        <span className="font-medium text-foreground">{p.metric}</span>
                        <span> {provenanceSummary(p)}</span>
                      </li>
                    ))}
                  </ul>
                ) : null}
              </div>
            ))}
            <p>As of {answer.asOf}</p>
          </div>
        </details>
      ) : null}
    </div>
  );
}
