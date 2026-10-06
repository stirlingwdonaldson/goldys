"use client";

import { PermissionDenied } from "@/components/states/permission-denied";
import { WidgetRenderer } from "@/components/widgets/widget-renderer";
import { parseWidgetSpecs } from "@/components/widgets/parse";
import type { AnswerPayload } from "./types";

interface AnswerBlockProps {
  summary: string;
  answer: AnswerPayload | null;
  error: string | null;
}

/** The answer anatomy: summary + widgets + "How I got this" trace + "as of" + notices. */
export function AnswerBlock({ summary, answer, error }: AnswerBlockProps) {
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

      {answer?.trace.length ? (
        <details className="text-xs text-muted-foreground">
          <summary className="cursor-pointer underline">How I got this</summary>
          <div className="mt-1 space-y-1">
            {answer.trace.map((t, i) => (
              <p key={i}>
                {t.tool} — {t.description}
              </p>
            ))}
            <p>As of {answer.asOf}</p>
          </div>
        </details>
      ) : null}
    </div>
  );
}
