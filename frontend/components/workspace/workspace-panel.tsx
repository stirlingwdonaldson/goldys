"use client";

import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { InlineError } from "@/components/states/inline-error";
import { PermissionDenied } from "@/components/states/permission-denied";
import { isApiError } from "@/lib/api/errors";
import type { WorkspaceApi, WorkspaceFollowUp, WorkspaceJob } from "@/lib/api/workspace";
import { isWorking, useWorkspace } from "./use-workspace";
import { useWorkspaceCommands, type WorkspaceCommands } from "./use-workspace-commands";
import { WorkspaceMessages } from "./workspace-messages";
import { WorkspaceQueue } from "./workspace-queue";
import { WorkspacePicker } from "./workspace-picker";
import { WorkspaceComposer } from "./workspace-composer";

const states = { QUEUED: "Waiting for a worker", RUNNING: "Investigating", COMPLETED: "Completed", CANCELLED: "Stopped", INTERRUPTED: "Interrupted" };

/** Mounted only while visible; unmount cancels reads, never server work. */
export function WorkspacePanel({ api, jobId, select }: { api: WorkspaceApi; jobId: string | null; select: (id: string | null) => void }) {
  const queries = useWorkspace(api, jobId);
  const { history, investigation, followUps } = queries;
  const job = investigation.data;
  const commands = useWorkspaceCommands(api, job, select);
  const errors = [history.error, investigation.error, followUps.error, commands.send.error, commands.action.error];
  const denied = errors.some(error => isApiError(error) && (error.status === 403 || error.code === "NOT_PERMITTED"));
  const signedOut = errors.some(error => isApiError(error) && (error.status === 401 || error.code === "AUTH_REQUIRED"));
  if (signedOut) return <div className="p-6"><InlineError>Sign in again to read this investigation.</InlineError><Link href="/login" className="underline">Sign in</Link></div>;
  if (denied) return <div className="space-y-4 p-6"><PermissionDenied subject="this investigation" message="Access has changed. Restricted history is unavailable." /><Button variant="outline" onClick={() => select(null)}>Start a new conversation</Button></div>;
  const unavailable = Boolean(jobId && (!job || !investigation.isFetchedAfterMount || !followUps.isFetchedAfterMount || investigation.error || followUps.error));
  return <div className="flex min-h-0 flex-1 flex-col">
    <div className="flex-1 space-y-6 overflow-y-auto px-6 py-5">
      {jobId ? <InvestigationContent api={api} queries={queries} commands={commands} /> : <WorkspacePicker history={history} select={select} />}
      {commands.action.error ? <InlineError className={commands.action.error.code === "WORKSPACE_CONFLICT" ? "bg-status-conflict-soft text-status-conflict" : undefined}>{commands.action.error.message} Refresh to see the current queue before trying again.</InlineError> : null}
      {commands.send.error ? <InlineError className={commands.send.error.code === "WORKSPACE_CONFLICT" ? "bg-status-conflict-soft text-status-conflict" : undefined}>{commands.uncertain ? "Delivery is uncertain. Retry sending the same message to check acceptance without duplicating it." : commands.send.error.message}</InlineError> : null}
    </div>
    <WorkspaceComposer commands={commands} working={isWorking(job)} disabled={unavailable} />
  </div>;
}

function InvestigationContent({ api, queries, commands }: { api: WorkspaceApi; queries: ReturnType<typeof useWorkspace>; commands: WorkspaceCommands }) {
  const { investigation, followUps } = queries;
  if (investigation.error || followUps.error) return <InlineError>Couldn’t refresh this investigation. Saved content is hidden until access is checked. <Button variant="link" onClick={() => void commands.refresh()}>Reconnect</Button></InlineError>;
  if (!investigation.isFetchedAfterMount || !followUps.isFetchedAfterMount || !investigation.data) return <p role="status">Loading investigation…</p>;
  return <InvestigationResult api={api} job={investigation.data} queue={followUps.data ?? []} commands={commands} />;
}

function InvestigationResult({ api, job, queue, commands }: { api: WorkspaceApi; job: WorkspaceJob; queue: WorkspaceFollowUp[]; commands: WorkspaceCommands }) {
  const { busy, uncertain, action, submit } = commands;
  const working = isWorking(job);
  let status = "Completed output is preserved. Findings may be incomplete; queued messages are cancelled.";
  if (working) status = "Work continues if you close this panel. Follow-ups wait for the current turn to finish.";
  if (job.state === "COMPLETED") status = "Saved investigation. Data qualifications remain part of each result.";
  return <>
    <div className="sticky top-0 z-10 flex items-center justify-between gap-3 bg-background py-2">
      <Badge variant={working ? "info" : job.state === "COMPLETED" ? "success" : "missing"}>{states[job.state]}</Badge>
      {working ? <Button variant="outline" disabled={busy} onClick={() => action.mutate(() => api.stopWorkspaceJob(job.id))}>Stop investigation</Button> : null}
    </div>
    <p role="status" className="text-xs text-muted-foreground">{status}</p>
    <WorkspaceMessages job={job} />
    {job.state === "INTERRUPTED" ? <div className="space-y-2"><p className="text-sm text-muted-foreground">Retry starts a new attempt with the original question. Taken follow-ups are not replayed.</p><Button variant="outline" disabled={busy || uncertain} onClick={() => submit(job.prompt, true)}>Retry investigation</Button></div> : null}
    <WorkspaceQueue items={queue} busy={busy || uncertain}
      edit={(id, prompt, done) => action.mutate(() => api.editWorkspaceFollowUp(job.id, id, prompt), { onSuccess: done })}
      remove={id => action.mutate(() => api.removeWorkspaceFollowUp(job.id, id))} />
  </>;
}
