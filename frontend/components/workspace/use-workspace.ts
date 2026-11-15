"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import type { WorkspaceApi, WorkspaceJob } from "@/lib/api/workspace";

export const workspaceKey = ["workspace"] as const;
export const isWorking = (job: WorkspaceJob | undefined) => job?.state === "QUEUED" || job?.state === "RUNNING";

/** Query cache is scoped by the existing identity/demo provider. Errors must hide cached history. */
export function useWorkspace(api: WorkspaceApi, jobId: string | null) {
  const history = useInfiniteQuery({
    queryKey: [...workspaceKey, "threads"],
    initialPageParam: undefined as string | undefined,
    queryFn: ({ pageParam, signal }) => api.listWorkspaceThreads(pageParam, 25, { signal }),
    getNextPageParam: page => page.nextCursor ?? undefined,
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
    refetchInterval: query => query.state.error ? false : isWorking(query.state.data) ? 2000 : 10000,
  });
  const followUps = useQuery({
    // Refresh the final queue when a job ends, before its polling interval stops.
    queryKey: [...workspaceKey, "queue", jobId, investigation.data?.state],
    queryFn: ({ signal }) => api.listWorkspaceFollowUps(jobId!, { signal }),
    enabled: jobId !== null && investigation.isSuccess,
    staleTime: 0,
    retry: false,
    refetchOnMount: "always",
    refetchInterval: query => query.state.error ? false : isWorking(investigation.data) ? 2000 : false,
  });
  return { history, investigation, followUps };
}
