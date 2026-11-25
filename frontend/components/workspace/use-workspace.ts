"use client";

import { useEffect, useRef } from "react";
import { useInfiniteQuery, useQuery, useQueryClient } from "@tanstack/react-query";
import type { WorkspaceApi, WorkspaceJob } from "@/lib/api/workspace";

export const workspaceKey = ["workspace"] as const;
export const isWorking = (job: WorkspaceJob | undefined) => !job?.readOnly && (job?.state === "QUEUED" || job?.state === "RUNNING");

/** Query cache is scoped by the existing identity/demo provider. Errors must hide cached history. */
export function useWorkspace(api: WorkspaceApi, jobId: string | null) {
  const client = useQueryClient();
  const previous = useRef<{ id: string | null; working: boolean }>({ id: jobId, working: false });
  const history = useInfiniteQuery({
    queryKey: [...workspaceKey, "threads"],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) => api.listWorkspaceThreads(pageParam, 25, { signal }),
    getNextPageParam: page => page.nextCursor ?? undefined,
    refetchInterval: query => query.state.error ? false : 10000,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
  });
  const investigation = useQuery({
    queryKey: [...workspaceKey, "job", jobId],
    queryFn: ({ signal }) => api.getWorkspaceJob(jobId!, { signal }),
    enabled: jobId !== null,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
    // Completed history also needs periodic current-permission checks while visible.
    refetchInterval: query => query.state.error ? false : isWorking(query.state.data) ? 1000 : 10000,
  });
  const followUps = useQuery({
    queryKey: [...workspaceKey, "queue", jobId],
    queryFn: ({ signal }) => api.listWorkspaceFollowUps(jobId!, { signal }),
    enabled: jobId !== null,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
    refetchInterval: query => query.state.error ? false : isWorking(investigation.data) ? 2000 : false,
  });
  const working = isWorking(investigation.data);
  useEffect(() => {
    if (previous.current.id === jobId && previous.current.working && !working) {
      void client.invalidateQueries({ queryKey: [...workspaceKey, "queue", jobId] });
    }
    previous.current = { id: jobId, working };
  }, [client, jobId, working]);
  return { history, investigation, followUps };
}
