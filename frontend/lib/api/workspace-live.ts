import { z } from "zod";
import { fetchApi } from "./client";
import { type WorkspaceApi, workspaceFileSchema, workspaceFollowUpSchema, workspaceJobSchema, workspacePageSchema } from "./workspace";

const jobPath = (id: string) => `/api/workspace/jobs/${encodeURIComponent(id)}`;
const command = (body: unknown, method = "POST"): RequestInit => ({ method, headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
export const workspaceLive: WorkspaceApi = {
  deleteWorkspaceThread: id => fetchApi<void>(`/api/workspace/threads/${encodeURIComponent(id)}`, { method: "DELETE" }, { responseType: "void" }),
  listWorkspaceFiles: (id, options) => fetchApi(`${jobPath(id)}/files`, { signal: options?.signal }, { schema: z.array(workspaceFileSchema) }),
  uploadWorkspaceFile: (id, file) => fetchApi(`${jobPath(id)}/files?name=${encodeURIComponent(file.name)}`, { method: "POST", headers: { "Content-Type": "application/octet-stream" }, body: file }, { schema: workspaceFileSchema }),
  keepWorkspaceFile: (id, file, keep) => fetchApi<void>(`${jobPath(id)}/files/${encodeURIComponent(file)}/keep?keep=${keep}`, { method: "PUT" }, { responseType: "void" }),
  deleteWorkspaceFile: (id, file) => fetchApi<void>(`${jobPath(id)}/files/${encodeURIComponent(file)}`, { method: "DELETE" }, { responseType: "void" }),
  workspaceFileDownloadUrl: async (id, file) => `${jobPath(id)}/files/${encodeURIComponent(file)}`,

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
