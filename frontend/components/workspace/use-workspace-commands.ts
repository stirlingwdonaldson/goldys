"use client";

import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { isApiError } from "@/lib/api/errors";
import type { WorkspaceApi, WorkspaceJob, WorkspaceSubmission } from "@/lib/api/workspace";
import { isWorking, workspaceKey } from "./use-workspace";

type Send = WorkspaceSubmission & { jobId?: string };

/** Mutations never replay themselves. An uncertain delivery retains its original command ID. */
export function useWorkspaceCommands(api: WorkspaceApi, job: WorkspaceJob | undefined, select: (id: string) => void) {
  const client = useQueryClient();
  const [question, setQuestion] = useState("");
  const attempt = useRef<Send | null>(null);
  const [uncertain, setUncertain] = useState(false);
  const refresh = () => client.invalidateQueries({ queryKey: workspaceKey });
  const onSettled = () => { void refresh(); };
  const action = useMutation({ mutationFn: (command: () => Promise<unknown>) => command(), retry: false, onSettled });
  const send = useMutation({
    mutationFn: async (input: Send) => {
      if (input.jobId) {
        await api.enqueueWorkspaceFollowUp(input.jobId, input.requestId, input.prompt);
        return input.jobId;
      }
      return (await api.submitWorkspaceJob(input)).id;
    },
    retry: false, onSettled,
  });
  const busy = send.isPending || action.isPending;
  function submit(prompt: string, retry = false) {
    if (busy || !prompt.trim()) return;
    const input = attempt.current ?? {
      threadId: job?.threadId ?? crypto.randomUUID(), requestId: crypto.randomUUID(), prompt: prompt.trim(),
      jobId: isWorking(job) && !retry ? job?.id : undefined,
    };
    attempt.current = input;
    send.mutate(input, {
      onSuccess: id => { attempt.current = null; setUncertain(false); setQuestion(""); select(id); },
      onError: error => {
        const definitive = isApiError(error) && [400, 403, 409].includes(error.status ?? 0);
        if (definitive) attempt.current = null;
        setUncertain(!definitive);
      },
    });
  }
  return { question, setQuestion, uncertain, busy, action, send, refresh, submit,
    sendQuestion: () => submit(attempt.current?.prompt ?? question) };
}
export type WorkspaceCommands = ReturnType<typeof useWorkspaceCommands>;
