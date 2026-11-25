import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { describe, expect, it, vi } from "vitest";
import { WorkspacePanel } from "./workspace-panel";
import { ApiError } from "@/lib/api/errors";
import type { WorkspaceApi, WorkspaceJob } from "@/lib/api/workspace";

const job: WorkspaceJob = { id: "10000000-0000-4000-8000-000000000001", threadId: "20000000-0000-4000-8000-000000000001", state: "RUNNING", prompt: "Original question", revision: 1, messages: [{ id: "text", role: "assistant", text: "Saved private output", reporting: null }] };
function adapter(): WorkspaceApi {
  return {
    deleteWorkspaceThread: vi.fn(), listWorkspaceFiles: vi.fn().mockResolvedValue([]), uploadWorkspaceFile: vi.fn(), keepWorkspaceFile: vi.fn(), deleteWorkspaceFile: vi.fn(), workspaceFileDownloadUrl: vi.fn(),
    listWorkspaceThreads: vi.fn().mockResolvedValue({ threads: [], nextCursor: null }),
    getWorkspaceJob: vi.fn().mockResolvedValue(job),
    listWorkspaceFollowUps: vi.fn().mockResolvedValue([]),
    submitWorkspaceJob: vi.fn().mockResolvedValue(job),
    stopWorkspaceJob: vi.fn().mockResolvedValue({ ...job, state: "CANCELLED" }),
    enqueueWorkspaceFollowUp: vi.fn().mockResolvedValue({ id: crypto.randomUUID(), prompt: "Follow-up", state: "QUEUED" }),
    editWorkspaceFollowUp: vi.fn().mockResolvedValue(undefined),
    removeWorkspaceFollowUp: vi.fn().mockResolvedValue(undefined),
  };
}
function mount(api: WorkspaceApi, id: string | null = job.id, existing?: QueryClient) {
  const client = existing ?? new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  const select = vi.fn();
  const view = render(<QueryClientProvider client={client}><WorkspacePanel api={api} jobId={id} select={select} /></QueryClientProvider>);
  return { ...view, client, select };
}

describe("persisted workspace", () => {
  it("renders recovered history read-only without requesting private files", async () => {
    const api = adapter();
    vi.mocked(api.getWorkspaceJob).mockResolvedValue({ ...job, readOnly: true, state: "INTERRUPTED" });
    mount(api);
    await screen.findByText("Saved private output");
    expect(screen.getByRole("button", { name: "Start new chat" })).toBeEnabled();
    expect(screen.queryByRole("button", { name: "Retry investigation" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(api.listWorkspaceFiles).not.toHaveBeenCalled();
  });
  it("starts the queue read without waiting for the investigation response", async () => {
    const api = adapter();
    vi.mocked(api.getWorkspaceJob).mockReturnValue(new Promise(() => {}));
    mount(api);
    await waitFor(() => expect(api.listWorkspaceFollowUps).toHaveBeenCalledTimes(1));
    expect(screen.queryByText("Saved private output")).not.toBeInTheDocument();
  });
  it("does not stop when Enter is pressed in an empty running composer", async () => {
    const api = adapter();
    mount(api);
    await screen.findByText("Saved private output");
    fireEvent.keyDown(screen.getByLabelText("Queue a follow-up"), { key: "Enter" });
    expect(api.stopWorkspaceJob).not.toHaveBeenCalled();
    expect(screen.getByRole("button", { name: "Stop investigation" })).toBeEnabled();
  });
  it("keeps Shift+Enter and composition local, and sends with Enter", async () => {
    const api = adapter();
    mount(api, null);
    const input = screen.getByLabelText("Reporting question");
    fireEvent.change(input, { target: { value: "Weekly sales" } });
    fireEvent.keyDown(input, { key: "Enter", shiftKey: true });
    fireEvent.keyDown(input, { key: "Enter", isComposing: true });
    expect(api.submitWorkspaceJob).not.toHaveBeenCalled();
    fireEvent.keyDown(input, { key: "Enter" });
    await waitFor(() => expect(api.submitWorkspaceJob).toHaveBeenCalledTimes(1));
  });
  it("reconnects through reads and never stops or replays work on unmount", async () => {
    const api = adapter();
    const view = mount(api);
    await screen.findByText("Saved private output");
    view.unmount();
    expect(api.stopWorkspaceJob).not.toHaveBeenCalled();
    expect(api.submitWorkspaceJob).not.toHaveBeenCalled();
    mount(api);
    await screen.findByText("Saved private output");
    expect(api.getWorkspaceJob).toHaveBeenCalledTimes(2);
  });
  it("withholds an old cached result until reconnect authorization finishes", async () => {
    const api = adapter();
    const client = new QueryClient();
    client.setQueryData(["workspace", "job", job.id], job);
    let resolve!: (value: WorkspaceJob) => void;
    vi.mocked(api.getWorkspaceJob).mockReturnValue(new Promise(done => { resolve = done; }));
    mount(api, job.id, client);
    expect(screen.queryByText("Saved private output")).not.toBeInTheDocument();
    await act(async () => resolve(job));
    await screen.findByText("Saved private output");
  });
  it("does not navigate back when a send finishes after unmount", async () => {
    const api = adapter();
    let resolve!: (value: WorkspaceJob) => void;
    vi.mocked(api.submitWorkspaceJob).mockReturnValue(new Promise(done => { resolve = done; }));
    const view = mount(api, null);
    fireEvent.change(screen.getByLabelText("Reporting question"), { target: { value: "Question" } });
    fireEvent.click(screen.getByRole("button", { name: "Send question" }));
    await waitFor(() => expect(api.submitWorkspaceJob).toHaveBeenCalledTimes(1));
    view.unmount();
    await act(async () => resolve(job));
    expect(view.select).not.toHaveBeenCalled();
    expect(api.stopWorkspaceJob).not.toHaveBeenCalled();
  });
  it("hides previously cached content on a permission failure", async () => {
    const api = adapter();
    const { client } = mount(api);
    await screen.findByText("Saved private output");
    vi.mocked(api.getWorkspaceJob).mockRejectedValue(new ApiError("NOT_PERMITTED", "Denied", undefined, undefined, { status: 403 }));
    await act(() => client.invalidateQueries({ queryKey: ["workspace", "job"] }));
    await screen.findByText("You don't have access to this investigation");
    expect(screen.queryByText("Saved private output")).not.toBeInTheDocument();
    expect(screen.queryByLabelText("Queue a follow-up")).not.toBeInTheDocument();
  });
  it("reuses the original request after an uncertain send and never retries automatically", async () => {
    const api = adapter();
    vi.mocked(api.submitWorkspaceJob).mockRejectedValueOnce(new ApiError("NETWORK_ERROR", "Lost response")).mockResolvedValueOnce(job);
    const { select } = mount(api, null);
    fireEvent.change(screen.getByLabelText("Reporting question"), { target: { value: "Original question" } });
    fireEvent.click(screen.getByRole("button", { name: "Send question" }));
    await screen.findByRole("button", { name: "Retry sending" });
    expect(api.submitWorkspaceJob).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("Reporting question")).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Retry sending" }));
    await waitFor(() => expect(select).toHaveBeenCalledWith(job.id));
    const calls = vi.mocked(api.submitWorkspaceJob).mock.calls;
    expect(calls[0][0]).toEqual(calls[1][0]);
  });
  it("queues follow-ups instead of creating another job and Stop targets the durable job", async () => {
    const api = adapter();
    mount(api);
    await screen.findByText("Saved private output");
    fireEvent.change(screen.getByLabelText("Queue a follow-up"), { target: { value: "Follow-up" } });
    fireEvent.click(screen.getByRole("button", { name: "Queue message" }));
    await waitFor(() => expect(api.enqueueWorkspaceFollowUp).toHaveBeenCalledWith(job.id, expect.any(String), "Follow-up"));
    expect(api.submitWorkspaceJob).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByRole("button", { name: "Stop investigation" })).not.toBeDisabled());
    fireEvent.click(screen.getByRole("button", { name: "Stop investigation" }));
    await waitFor(() => expect(api.stopWorkspaceJob).toHaveBeenCalledWith(job.id));
  });
  it("requires explicit retry for interrupted work using a new submission ID", async () => {
    const api = adapter();
    vi.mocked(api.getWorkspaceJob).mockResolvedValue({ ...job, state: "INTERRUPTED" });
    mount(api);
    await screen.findByRole("button", { name: "Retry investigation" });
    expect(api.submitWorkspaceJob).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Retry investigation" }));
    await waitFor(() => expect(api.submitWorkspaceJob).toHaveBeenCalledWith(expect.objectContaining({ threadId: job.threadId, prompt: job.prompt, requestId: expect.any(String) })));
  });
  it("refreshes the final queue when job polling observes interruption", async () => {
    const api = adapter();
    const item = { id: crypto.randomUUID(), prompt: "Pending question", state: "QUEUED" as const };
    vi.mocked(api.listWorkspaceFollowUps).mockResolvedValue([item]);
    const { client } = mount(api);
    await screen.findByRole("button", { name: "Edit message" });
    vi.mocked(api.getWorkspaceJob).mockResolvedValue({ ...job, state: "INTERRUPTED" });
    vi.mocked(api.listWorkspaceFollowUps).mockResolvedValue([{ ...item, state: "CANCELLED" }]);
    await act(() => client.invalidateQueries({ queryKey: ["workspace", "job"] }));
    await screen.findByText("Cancelled", { exact: true });
    expect(screen.queryByRole("button", { name: "Edit message" })).not.toBeInTheDocument();
  });
  it("refreshes a taken queue entry after an edit conflict without dropping its prompt", async () => {
    const api = adapter();
    const item = { id: crypto.randomUUID(), prompt: "Queued question", state: "QUEUED" as const };
    vi.mocked(api.listWorkspaceFollowUps).mockResolvedValue([item]);
    vi.mocked(api.editWorkspaceFollowUp).mockImplementation(async () => {
      vi.mocked(api.listWorkspaceFollowUps).mockResolvedValue([{ ...item, state: "TAKEN" }]);
      throw new ApiError("WORKSPACE_CONFLICT", "Follow-up is no longer editable.", undefined, undefined, { status: 409 });
    });
    mount(api);
    fireEvent.click(await screen.findByRole("button", { name: "Edit message" }));
    fireEvent.change(screen.getByLabelText("Edit queued message"), { target: { value: "Edited question" } });
    fireEvent.click(screen.getByRole("button", { name: "Save message" }));
    await screen.findByText("Taken by the worker · never automatically replayed");
    expect(screen.getByText("Queued question")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Save message" })).not.toBeInTheDocument();
  });
});
