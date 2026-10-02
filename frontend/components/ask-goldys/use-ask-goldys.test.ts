import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { useAskGoldys } from "./use-ask-goldys";
import { streamChat } from "./stream";

vi.mock("./stream", () => ({
  streamChat: vi.fn(),
}));

describe("useAskGoldys", () => {
  beforeEach(() => {
    vi.mocked(streamChat).mockReset();
  });

  it("appends the assistant's reply to the history after the stream", async () => {
    vi.mocked(streamChat).mockImplementation(async (_messages, handlers) => {
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
  });
});
