"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import type { WorkspaceApi } from "@/lib/api/workspace";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { InlineError } from "@/components/states/inline-error";
import { formatDateTime } from "@/lib/format";
import type { useWorkspace } from "./use-workspace";

export function WorkspacePicker({ api, history, select }: { api: WorkspaceApi; history: ReturnType<typeof useWorkspace>["history"]; select: (id: string | null) => void }) {
  const client = useQueryClient();
  const remove = useMutation({ mutationFn: (id: string) => api.deleteWorkspaceThread(id), retry: false,
    onSuccess: async () => { client.removeQueries({ queryKey: ["workspace", "job"] }); client.removeQueries({ queryKey: ["workspace", "files"] }); await client.invalidateQueries({ queryKey: ["workspace"] }); } });
  if (history.error) return <InlineError>Couldn’t load investigations. <Button variant="link" onClick={() => void history.refetch()}>Retry loading</Button></InlineError>;
  if (!history.isFetchedAfterMount || !history.data) return <p role="status">Loading investigations…</p>;
  const threads = history.data.pages.flatMap(page => page.threads);
  return <section aria-label="Your investigations" className="space-y-3">
    <h3 className="text-lg font-semibold">Your investigations</h3>
    <p className="text-sm text-muted-foreground">Start with a reporting question, or return to an earlier investigation.</p>
    {threads.map(thread => <div key={thread.id} className="flex items-center justify-between gap-3 rounded-lg border p-3">
      <Button variant="ghost" size="sm" disabled={remove.isPending} aria-label="Delete investigation" onClick={() => {
        if (window.confirm("Delete this conversation, all attempts and retained files? Running work will stop. This cannot be undone in the preview.")) remove.mutate(thread.id);
      }}>Delete</Button>
      <span id={`thread-date-${thread.id}`} className="text-sm tabular-nums">{formatDateTime(thread.createdAt)}</span>
      {thread.access === "NOT_PERMITTED" ? <Badge variant="missing">Not permitted</Badge> : thread.latestJobId ? <Button variant="outline" size="sm" aria-describedby={`thread-date-${thread.id}`} onClick={() => select(thread.latestJobId)}>Open investigation</Button> : <span className="text-sm text-muted-foreground">No saved attempt</span>}
    </div>)}
    {remove.error ? <InlineError>{remove.error.message}</InlineError> : null}
    {history.hasNextPage ? <Button variant="outline" disabled={history.isFetchingNextPage} onClick={() => void history.fetchNextPage()}>Load older investigations</Button> : null}
    {!threads.length ? <p className="text-sm text-muted-foreground">No investigations yet.</p> : null}
  </section>;
}
