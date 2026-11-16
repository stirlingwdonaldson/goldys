import { ApiError } from "./errors";
import type { WorkspaceApi, WorkspaceFile, WorkspaceFollowUp, WorkspaceJob, WorkspacePage } from "./workspace";

const denied = () => new ApiError("NOT_PERMITTED", "Not permitted to access this investigation.", undefined, undefined, { status: 403 });
const conflict = () => new ApiError("WORKSPACE_CONFLICT", "The investigation or queued message is no longer editable.", undefined, undefined, { status: 409 });
const validatePrompt = (prompt: string) => {
  if (!prompt.trim() || prompt.length > 16000) throw new ApiError("VALIDATION_FAILED", "Message must contain 1–16000 characters.");
};

/** Isolated browser fixtures; no worker, provider, or live API is called. */
export function createWorkspaceDemo(): WorkspaceApi {
  const files = new Map<string, { info: WorkspaceFile; data: File }[]>();
  const jobs = new Map<string, WorkspaceJob>();
  const example: WorkspaceJob = {
    id: "10000000-0000-4000-8000-000000000001", threadId: "20000000-0000-4000-8000-000000000001",
    prompt: "Demo: compare weekly sales", state: "INTERRUPTED", revision: 1,
    messages: [{ id: "demo-result", role: "tool", text: "", draft: {
      title: "Demo weekly sales", description: "A reusable sales query, not a saved result.", dashboardId: null,
      filters: { dateRange: null, comparison: null, dimensions: [] },
      widgets: [{ id: "sales", renderType: "stat", layout: { w: 4, h: 2 }, queries: [{
        metric: "sales.net", range: { from: "2026-11-02", to: "2026-11-08", calendar: "TRADING" },
        grain: "TOTAL", dimensions: [], comparison: null,
      }] }],
    }, reporting: {
      widget: { schemaVersion: 2, id: "demo-missing", type: "stat", title: "Demo sales", value: null },
      notices: ["Demo investigation interrupted. Sales coverage is unavailable."], provenance: [], relatedMetrics: [],
    } }],
  };
  jobs.set(example.id, example);
  const threads: WorkspacePage["threads"] = [
    { id: example.threadId, createdAt: "2026-11-15T00:00:00Z", access: "AVAILABLE", latestJobId: example.id },
    { id: "20000000-0000-4000-8000-000000000002", createdAt: "2026-11-14T00:00:00Z", access: "NOT_PERMITTED", latestJobId: null },
  ];
  const submissions = new Map<string, string>();
  const originalFollowUps = new Map<string, string>();
  const queues = new Map<string, WorkspaceFollowUp[]>();
  const read = (id: string) => {
    const job = jobs.get(id);
    if (!job) throw denied();
    return job;
  };
  const active = (job: WorkspaceJob) => job.state === "QUEUED" || job.state === "RUNNING";
  const queued = (id: string, requestId: string) => {
    read(id);
    const item = queues.get(id)?.find(item => item.id === requestId && item.state === "QUEUED");
    if (!item) throw conflict();
    return item;
  };
  const pause = async (signal?: AbortSignal) => {
    signal?.throwIfAborted();
    await new Promise(resolve => setTimeout(resolve, 300));
    signal?.throwIfAborted();
  };
  return {
    async deleteWorkspaceThread(id) {
      await pause(); const index = threads.findIndex(thread => thread.id === id); if (index < 0) throw denied();
      threads.splice(index, 1); files.delete(id);
      for (const [key, job] of jobs) if (job.threadId === id) { jobs.delete(key); queues.delete(key); }
    },
    async listWorkspaceFiles(id, options) { await pause(options?.signal); read(id); return structuredClone((files.get(read(id).threadId) ?? []).map(file => file.info)); },
    async uploadWorkspaceFile(id, file) {
      await pause(); read(id);
      const current = files.get(read(id).threadId) ?? [];
      if (!file.size || file.size > 25 * 1024 * 1024 || current.length >= 5 || current.reduce((sum, item) => sum + item.info.byteLength, 0) + file.size > 100 * 1024 * 1024) throw conflict();
      const info: WorkspaceFile = { id: crypto.randomUUID(), name: file.name, kind: "UPLOAD", byteLength: file.size, kept: false, expiresAt: new Date(Date.now() + 30 * 86400000).toISOString(), available: true };
      files.set(read(id).threadId, [...current, { info, data: file }]); return structuredClone(info);
    },
    async keepWorkspaceFile(id, file, keep) { await pause(); read(id); const item = files.get(read(id).threadId)?.find(item => item.info.id === file); if (!item?.info.available) throw conflict(); item.info.kept = keep; },
    async deleteWorkspaceFile(id, file) { await pause(); read(id); const item = files.get(read(id).threadId)?.find(item => item.info.id === file); if (item) item.info.available = false; },
    async workspaceFileDownloadUrl(id, file) {
      await pause(); read(id); const item = files.get(read(id).threadId)?.find(item => item.info.id === file);
      if (!item?.info.available) throw conflict();
      return new Promise<string>((resolve, reject) => { const reader = new FileReader(); reader.onload = () => resolve(String(reader.result)); reader.onerror = () => reject(conflict()); reader.readAsDataURL(item.data); });
    },
    async listWorkspaceThreads(before, limit = 25, options) {
      await pause(options?.signal);
      if (!Number.isInteger(limit) || limit < 1 || limit > 50) throw new ApiError("VALIDATION_FAILED", "Page size must be 1–50.");
      const index = before ? threads.findIndex(thread => thread.id === before) : -1;
      if (before && index < 0) throw denied();
      const page = threads.slice(index + 1, index + 1 + limit);
      return structuredClone({ threads: page, nextCursor: index + 1 + limit < threads.length ? page.at(-1)!.id : null });
    },
    async submitWorkspaceJob(input) {
      await pause();
      validatePrompt(input.prompt);
      const key = `${input.threadId}:${input.requestId}`;
      const prior = submissions.get(key);
      if (prior) {
        const accepted = read(prior);
        if (accepted.prompt !== input.prompt) throw conflict();
        return structuredClone(accepted);
      }
      if ([...jobs.values()].some(job => job.threadId === input.threadId && active(job))) throw conflict();
      const thread = threads.find(thread => thread.id === input.threadId);
      if (thread?.access === "NOT_PERMITTED") throw denied();
      const previous = thread?.latestJobId ? read(thread.latestJobId) : null;
      const job: WorkspaceJob = { id: crypto.randomUUID(), threadId: input.threadId, prompt: input.prompt,
        state: "QUEUED", revision: previous?.revision ?? 0, messages: structuredClone(previous?.messages ?? []) };
      jobs.set(job.id, job);
      queues.set(job.id, []);
      submissions.set(key, job.id);
      if (thread) thread.latestJobId = job.id;
      else threads.unshift({ id: input.threadId, createdAt: new Date().toISOString(), access: "AVAILABLE", latestJobId: job.id });
      return structuredClone(job);
    },
    async getWorkspaceJob(id, options) { await pause(options?.signal); return structuredClone(read(id)); },
    async stopWorkspaceJob(id) {
      await pause();
      const job = read(id);
      if (active(job)) job.state = "CANCELLED";
      for (const item of queues.get(id) ?? []) if (item.state === "QUEUED") item.state = "CANCELLED";
      return structuredClone(job);
    },
    async listWorkspaceFollowUps(id, options) { await pause(options?.signal); read(id); return structuredClone(queues.get(id) ?? []); },
    async enqueueWorkspaceFollowUp(id, requestId, prompt) {
      await pause();
      validatePrompt(prompt);
      const job = read(id);
      const queue = queues.get(id)!;
      const existing = queue.find(item => item.id === requestId);
      const key = `${id}:${requestId}`;
      if (existing) {
        if (originalFollowUps.get(key) !== prompt) throw conflict();
        return structuredClone(existing);
      }
      if (!active(job) || queue.length >= 20) throw conflict();
      const item: WorkspaceFollowUp = { id: requestId, prompt, state: "QUEUED" };
      queue.push(item);
      originalFollowUps.set(key, prompt);
      return structuredClone(item);
    },
    async editWorkspaceFollowUp(id, requestId, prompt) {
      await pause(); validatePrompt(prompt);
      if (!active(read(id))) throw conflict();
      queued(id, requestId).prompt = prompt;
    },
    async removeWorkspaceFollowUp(id, requestId) { await pause(); queued(id, requestId).state = "CANCELLED"; },
  };
}
