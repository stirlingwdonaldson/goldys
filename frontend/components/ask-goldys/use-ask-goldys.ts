"use client";

import { useCallback, useMemo, useState } from "react";
import { streamChat } from "./stream";
import type { AnswerPayload, ChatMessage } from "./types";

export interface UseAskGoldys {
  messages: ChatMessage[];
  summary: string;
  answer: AnswerPayload | null;
  error: string | null;
  working: boolean;
  ask: (question: string) => void;
  reset: () => void;
}

/** Drives the "Ask Goldy's" conversation: message history + the streamed answer. */
export function useAskGoldys(): UseAskGoldys {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [summary, setSummary] = useState("");
  const [answer, setAnswer] = useState<AnswerPayload | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [working, setWorking] = useState(false);
  const [threadId, setThreadId] = useState<string | null>(null);

  const ask = useCallback(
    (question: string) => {
      // The backend owns the thread; the client mints a UUID for a new conversation and reuses it
      // for every subsequent turn, avoiding any id-discovery round-trip.
      const id = threadId ?? crypto.randomUUID();
      if (threadId === null) setThreadId(id);

      const next: ChatMessage[] = [...messages, { role: "user", content: question }];
      setMessages(next);
      setSummary("");
      setAnswer(null);
      setError(null);
      setWorking(true);

      let accumulated = "";
      streamChat(id, question, {
        onText: (delta) => {
          accumulated += delta;
          setSummary(accumulated);
        },
        onAnswer: (payload) => setAnswer(payload),
        onError: (message) => setError(message),
      })
        .catch(() => setError("Could not reach the server. Check your connection and try again."))
        .finally(() => {
          // Append the assistant's turn so the local history stays in sync with what the server
          // streamed back. The assistant text is authoritative — never sent back to the server.
          if (accumulated) {
            setMessages((msgs) => [...msgs, { role: "assistant", content: accumulated }]);
          }
          setWorking(false);
        });
    },
    [messages, threadId],
  );

  const reset = useCallback(() => {
    setMessages([]);
    setSummary("");
    setAnswer(null);
    setError(null);
    setWorking(false);
    setThreadId(null);
  }, []);

  return useMemo(
    () => ({ messages, summary, answer, error, working, ask, reset }),
    [messages, summary, answer, error, working, ask, reset],
  );
}
