import { describe, it, expect, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
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

function renderScreen(api: Api) {
  return render(
    <DemoModeProvider>
      <ConversationsScreen api={api} />
    </DemoModeProvider>,
  );
}

describe("ConversationsScreen", () => {
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

    await waitFor(() => expect(getThread).toHaveBeenCalledWith("t1"));
    expect(await screen.findByText("What were sales last week?")).toBeInTheDocument();
    expect(screen.getByText("Gross sales were $43,691.48.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Back" })).toBeInTheDocument();
  });
});
