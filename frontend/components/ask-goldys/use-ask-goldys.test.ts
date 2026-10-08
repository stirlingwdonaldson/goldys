import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useAskGoldys } from "./use-ask-goldys";
import { streamChat } from "./stream";

vi.mock("./stream", () => ({
  streamChat: vi.fn(),
}));

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

describe("useAskGoldys", () => {
  beforeEach(() => {
    vi.mocked(streamChat).mockReset();
  });

  it("posts threadId and message (not history) and appends the assistant reply", async () => {
    vi.mocked(streamChat).mockImplementation(async (_threadId, _message, handlers) => {
      handlers.onText("Sales were ");
      handlers.onText("$27,650.66.");
      handlers.onAnswer({ widgets: [], trace: [], asOf: "2026-10-02T10:00:00Z", notices: [] });
    });

    const { result } = renderHook(() => useAskGoldys());

    await act(async () => {
      result.current.ask("What were sales last week?");
    });

    await waitFor(() => expect(result.current.messages).toHaveLength(2));
    expect(result.current.messages).toEqual([
      { role: "user", content: "What were sales last week?" },
      { role: "assistant", content: "Sales were $27,650.66." },
    ]);

    // Server-authoritative: streamChat receives (threadId, message, handlers) — never the
    // assistant text or a history array.
    expect(streamChat).toHaveBeenCalledTimes(1);
    const [threadId, message, handlers] = vi.mocked(streamChat).mock.calls[0];
    expect(threadId).toMatch(UUID_RE);
    expect(message).toBe("What were sales last week?");
    expect(handlers).toHaveProperty("onText");
    expect(handlers).toHaveProperty("onAnswer");
    expect(handlers).toHaveProperty("onError");
  });

  it("reuses the same threadId across turns", async () => {
    vi.mocked(streamChat).mockImplementation(async (_threadId, _message, handlers) => {
      handlers.onText("An answer.");
      handlers.onAnswer({ widgets: [], trace: [], asOf: "", notices: [] });
    });

    const { result } = renderHook(() => useAskGoldys());

    await act(async () => {
      result.current.ask("Question one?");
    });
    await act(async () => {
      result.current.ask("Question two?");
    });

    const calls = vi.mocked(streamChat).mock.calls;
    expect(calls).toHaveLength(2);
    expect(calls[0][0]).toMatch(UUID_RE);
    expect(calls[1][0]).toBe(calls[0][0]);
    expect(calls[0][1]).toBe("Question one?");
    expect(calls[1][1]).toBe("Question two?");
  });

  it("mints a fresh threadId after reset", async () => {
    vi.mocked(streamChat).mockImplementation(async (_threadId, _message, handlers) => {
      handlers.onAnswer({ widgets: [], trace: [], asOf: "", notices: [] });
    });

    const { result } = renderHook(() => useAskGoldys());

    await act(async () => {
      result.current.ask("One?");
    });
    const first = vi.mocked(streamChat).mock.calls[0][0];

    await act(async () => {
      result.current.reset();
    });
    await act(async () => {
      result.current.ask("Two?");
    });

    const second = vi.mocked(streamChat).mock.calls[1][0];
    expect(second).toMatch(UUID_RE);
    expect(second).not.toBe(first);
  });
});
