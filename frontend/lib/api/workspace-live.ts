import { z } from "zod";
import { fetchApi } from "./client";
import { type WorkspaceApi, workspaceFollowUpSchema, workspaceJobSchema, workspacePageSchema } from "./workspace";

const jobPath = (id: string) => `/api/workspace/jobs/${encodeURIComponent(id)}`;
const command = (body: unknown, method = "POST"): RequestInit => ({ method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
export const workspaceLive: WorkspaceApi = {
  listWorkspaceThreads: (before, limit = 25, options) => {
    const query = new URLSearchParams({ limit: String(limit) });
    if (before) query.set("before", before);
    return fetchApi(`/api/workspace/threads?${query}`, { signal: options?.signal }, { schema: workspacePageSchema });
  },
  submitWorkspaceJob: input => fetchApi("/api/workspace/jobs", command(input), { schema: workspaceJobSchema }),
  getWorkspaceJob: (id, options) => fetchApi(jobPath(id), { signal: options?.signal }, { schema: workspaceJobSchema }),
  stopWorkspaceJob: id => fetchApi(`${jobPath(id)}/stop`, command({}), { schema: workspaceJobSchema }),
  listWorkspaceFollowUps: (id, options) => fetchApi(`${jobPath(id)}/follow-ups`, { signal: options?.signal }, { schema: z.array(workspaceFollowUpSchema) }),
  enqueueWorkspaceFollowUp: (id, requestId, prompt) => fetchApi(`${jobPath(id)}/follow-ups`, command({ requestId, prompt }), { schema: workspaceFollowUpSchema }),
  editWorkspaceFollowUp: (id, requestId, prompt) => fetchApi<void>(`${jobPath(id)}/follow-ups/${encodeURIComponent(requestId)}`, command({ prompt }, "PUT"), { responseType: "void" }),
  removeWorkspaceFollowUp: (id, requestId) => fetchApi<void>(`${jobPath(id)}/follow-ups/${encodeURIComponent(requestId)}`, { method: "DELETE" }, { responseType: "void" }),
};
