import { afterEach, describe, expect, it, vi } from "vitest";
import { createWorkspaceDemo } from "./workspace-demo";
import { workspaceLive } from "./workspace-live";
import { workspaceJobSchema } from "./workspace";

afterEach(() => vi.unstubAllGlobals());
const input = () => ({ threadId: crypto.randomUUID(), requestId: crypto.randomUUID(), prompt: "Demo question" });

describe("workspace demo", () => {
  it("keeps requests offline and idempotent, and cancels pending follow-ups on Stop", async () => {
    const fetch = vi.fn(() => { throw new Error("Demo must not fetch"); });
    vi.stubGlobal("fetch", fetch);
    const api = createWorkspaceDemo();
    const submission = input();
    const job = await api.submitWorkspaceJob(submission);
    expect(await api.submitWorkspaceJob(submission)).toEqual(job);
    await expect(api.submitWorkspaceJob({ ...submission, requestId: crypto.randomUUID() })).rejects.toMatchObject({ code: "WORKSPACE_CONFLICT" });
    expect(await api.listWorkspaceFollowUps(job.id)).toEqual([]);
    const requestId = crypto.randomUUID();
    await api.enqueueWorkspaceFollowUp(job.id, requestId, "First question");
    await api.editWorkspaceFollowUp(job.id, requestId, "Edited question");
    expect((await api.enqueueWorkspaceFollowUp(job.id, requestId, "Duplicate")).prompt).toBe("Edited question");
    expect((await api.stopWorkspaceJob(job.id)).state).toBe("CANCELLED");
    expect((await api.listWorkspaceFollowUps(job.id))[0].state).toBe("CANCELLED");
    await expect(api.editWorkspaceFollowUp(job.id, requestId, "Too late")).rejects.toMatchObject({ code: "WORKSPACE_CONFLICT" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("preserves missing results, restricted history, cursor pagination and explicit retry", async () => {
    const api = createWorkspaceDemo();
    const page = await api.listWorkspaceThreads(undefined, 1);
    const job = await api.getWorkspaceJob(page.threads[0].latestJobId!);
    expect(workspaceJobSchema.parse(job).state).toBe("INTERRUPTED");
    expect(job.messages[0].reporting?.widget).toMatchObject({ value: null });
    const restricted = await api.listWorkspaceThreads(page.nextCursor!, 1);
    expect(restricted.threads[0]).toMatchObject({ access: "NOT_PERMITTED", latestJobId: null });
    expect(restricted.nextCursor).toBeNull();
    await expect(api.submitWorkspaceJob({ ...input(), threadId: restricted.threads[0].id })).rejects.toMatchObject({ code: "NOT_PERMITTED" });
    const retry = await api.submitWorkspaceJob({ ...input(), threadId: job.threadId });
    expect(retry.messages).toEqual(job.messages);
    retry.messages.length = 0;
    expect((await api.getWorkspaceJob(retry.id)).messages).toHaveLength(1);
    await expect(api.getWorkspaceJob(crypto.randomUUID())).rejects.toMatchObject({ code: "NOT_PERMITTED" });
  });
});

describe("workspace live", () => {
  it("uses the browser contract and rejects native snapshots or malformed widgets", async () => {
    const job = { id: crypto.randomUUID(), threadId: crypto.randomUUID(), prompt: "Question", state: "QUEUED", revision: 0, messages: [] };
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify(job), { headers: { "Content-Type": "application/json" } }));
    vi.stubGlobal("fetch", fetch);
    expect(await workspaceLive.getWorkspaceJob(job.id)).toEqual(job);
    expect(fetch.mock.calls[0][0]).toBe(`/api/workspace/jobs/${job.id}`);
    expect(workspaceJobSchema.safeParse({ ...job, messages: undefined, snapshot: {} }).success).toBe(false);
    expect(workspaceJobSchema.safeParse({ ...job, messages: [{ id: "bad", role: "tool", text: "", reporting: { widget: { type: "executable" }, notices: [], provenance: [], relatedMetrics: [] } }] }).success).toBe(false);
  });
});
