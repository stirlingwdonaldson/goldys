"use client";

import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { InlineError } from "./inline-error";
import { LoadingState } from "./loading-state";
import { PermissionDenied } from "./permission-denied";

/** Keep independently fetched sections honest, including stale data after a failed refresh. */
export function QueryRegion({ query, subject, children }: {
  query: { isPending: boolean; isFetching?: boolean; error: (Error & { code?: string }) | null; data: unknown; refetch: () => unknown };
  subject: string;
  children: ReactNode;
}) {
  if (query.error?.code === "NOT_PERMITTED") return <PermissionDenied subject={subject} />;
  if (query.isPending) return <LoadingState rows={2} />;
  return <>
    {query.error ? <InlineError>{subject} unavailable. {query.error.message} {query.data !== undefined ? "Showing the last successful result; it may be out of date. " : ""}<Button variant="outline" size="sm" onClick={() => query.refetch()}>Retry {subject}</Button></InlineError> : null}
    {query.isFetching ? <p role="status" className="text-sm text-muted-foreground">Updating {subject}…</p> : null}
    {query.data !== undefined ? children : null}
  </>;
}
