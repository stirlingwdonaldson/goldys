import { describe, it, expect, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "@/lib/api/errors";
import { DemoModeProvider } from "@/lib/demo-mode";
import { ConversationsScreen } from "./conversations-screen";
import type {
  Api,
  ConversationThreadSummary,
  ConversationThreadView,
} from "@/lib/api/types";

function thread(id: string, title: string, updatedAt = "2026-10-05T09:12:00Z"): ConversationThreadSummary {
  return { id, title, updatedAt, lastPreview: `Preview of ${title}` };
}

function view(id: string, title: string): ConversationThreadView {
  return {
    id,
    title,
    messages: [
      {
        id: `${id}-m1`,
        threadId: id,
        role: "user",
        content: "What were sales last week?",
        toolTrace: [],
        createdAt: "2026-10-05T09:11:00Z",
      },
      {
        id: `${id}-m2`,
        threadId: id,
        role: "assistant",
        content: "Gross sales were $43,691.48.",
        toolTrace: [{ tool: "query_metric", description: "sales.gross", provenance: [] }],
        createdAt: "2026-10-05T09:12:00Z",
      },
    ],
  };
}

/** A stub Api with a mutable thread list so delete/rename reflect through `listThreads`. */
function stubApi(initial: ConversationThreadSummary[]): {
  api: Api;
  threads: ConversationThreadSummary[];
} {
  const threads = [...initial];
  const api = {
    listThreads: async () => [...threads],
    getThread: async (id: string) => view(id, threads.find((t) => t.id === id)?.title ?? id),
    renameThread: async (id: string, title: string) => view(id, title),
    deleteThread: async (id: string) => {
      const i = threads.findIndex((t) => t.id === id);
      if (i >= 0) threads.splice(i, 1);
    },
  } as unknown as Api;
  return { api, threads };
}

function renderScreen(api: Api, client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  return render(
    <DemoModeProvider><QueryClientProvider client={client}>
      <ConversationsScreen api={api} />
    </QueryClientProvider></DemoModeProvider>,
  );
}

describe("ConversationsScreen", () => {
  it("withholds cached titles and previews until current authorization finishes", async () => {
    const client = new QueryClient();
    client.setQueryData(["listThreads"], [thread("t1", "Private cached title")]);
    const { api } = stubApi([]);
    let resolve!: (value: ConversationThreadSummary[]) => void;
    api.listThreads = () => new Promise(done => { resolve = done; });
    renderScreen(api, client);
    expect(screen.queryByText("Private cached title")).not.toBeInTheDocument();
    await act(async () => resolve([thread("t1", "Restricted conversation")]));
    await screen.findByText("Restricted conversation");
    expect(screen.queryByText("Private cached title")).not.toBeInTheDocument();
  });

  it("withholds a cached transcript and hides it after a denied refresh", async () => {
    const client = new QueryClient();
    client.setQueryData(["getThread", "t1"], view("t1", "Sales last week"));
    const { api } = stubApi([thread("t1", "Sales last week")]);
    let resolve!: (value: ConversationThreadView) => void;
    const read = vi.fn(() => new Promise<ConversationThreadView>(done => { resolve = done; }));
    api.getThread = read;
    renderScreen(api, client);
    fireEvent.click(await screen.findByRole("button", { name: "Sales last week" }));
    expect(screen.queryByText("Gross sales were $43,691.48.")).not.toBeInTheDocument();
    await act(async () => resolve(view("t1", "Sales last week")));
    await screen.findByText("Gross sales were $43,691.48.");
    read.mockRejectedValue(new ApiError("NOT_PERMITTED", "Access changed"));
    await act(async () => { await client.invalidateQueries({ queryKey: ["getThread", "t1"] }); });
    await waitFor(() => expect(screen.queryByText("Gross sales were $43,691.48.")).not.toBeInTheDocument());
    expect(screen.getByText(/don.t have access/i)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Back" }));
    await screen.findByRole("button", { name: "Sales last week" });
  });

  it("hides cached previews on a failed reauthorization", async () => {
    const client = new QueryClient();
    const { api } = stubApi([thread("t1", "Sales last week")]);
    const read = vi.fn(api.listThreads);
    api.listThreads = read;
    renderScreen(api, client);
    await screen.findByText("Preview of Sales last week");
    read.mockRejectedValue(new ApiError("NOT_PERMITTED", "Access changed"));
    await act(async () => { await client.invalidateQueries({ queryKey: ["listThreads"] }); });
    await waitFor(() => expect(screen.queryByText("Preview of Sales last week")).not.toBeInTheDocument());
  });

  it("lists threads with their title, preview and updated date", async () => {
    const { api } = stubApi([thread("t1", "Sales last week"), thread("t2", "Weekend covers")]);
    renderScreen(api);

    expect(await screen.findByText("Sales last week")).toBeInTheDocument();
    expect(screen.getByText("Weekend covers")).toBeInTheDocument();
    expect(screen.getByText("Preview of Sales last week")).toBeInTheDocument();
    expect(screen.getByText("Preview of Weekend covers")).toBeInTheDocument();
  });

  it("shows the empty state when there are no threads", async () => {
    const { api } = stubApi([]);
    renderScreen(api);
    expect(await screen.findByText(/no conversations yet/i)).toBeInTheDocument();
  });

  it("deletes a thread and removes it from the list", async () => {
    const { api, threads } = stubApi([thread("t1", "Sales last week"), thread("t2", "Weekend covers")]);
    renderScreen(api);

    await screen.findByText("Sales last week");
    fireEvent.click(screen.getByRole("button", { name: "Delete Sales last week" }));

    // The confirmation dialog appears; confirm the delete.
    const confirm = await screen.findByRole("button", { name: "Delete" });
    fireEvent.click(confirm);

    await waitFor(() => expect(threads).toHaveLength(1));
    await waitFor(() =>
      expect(screen.queryByText("Sales last week")).not.toBeInTheDocument(),
    );
    expect(screen.getByText("Weekend covers")).toBeInTheDocument();
  });

  it("renames a thread through renameThread", async () => {
    const renameThread = vi.fn(async (id: string, title: string) => view(id, title));
    const { api } = stubApi([thread("t1", "Sales last week")]);
    (api as { renameThread: unknown }).renameThread = renameThread;
    renderScreen(api);

    await screen.findByText("Sales last week");
    fireEvent.click(screen.getByRole("button", { name: "Rename Sales last week" }));

    const input = await screen.findByLabelText("Title");
    fireEvent.change(input, { target: { value: "Weekly sales" } });
    fireEvent.click(screen.getByRole("button", { name: "Save" }));

    await waitFor(() => expect(renameThread).toHaveBeenCalledWith("t1", "Weekly sales"));
  });

  it("opens a thread and shows its transcript", async () => {
    const getThread = vi.fn(async (id: string) => view(id, "Sales last week"));
    const { api } = stubApi([thread("t1", "Sales last week")]);
    (api as { getThread: unknown }).getThread = getThread;
    renderScreen(api);

    fireEvent.click(await screen.findByRole("button", { name: "Sales last week" }));

    await waitFor(() => expect(getThread).toHaveBeenCalledWith("t1", expect.objectContaining({ signal: expect.any(AbortSignal) })));
    expect(await screen.findByText("What were sales last week?")).toBeInTheDocument();
    expect(screen.getByText("Gross sales were $43,691.48.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back" })).toBeInTheDocument();
  });
});
