"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { isAbortError } from "@/lib/api/errors";
import { registerThreadStream } from "@/lib/stream-lifecycle";
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
  const queryClient = useQueryClient();
  const generation = useRef(0);
  const active = useRef<AbortController | null>(null);
  const cancel = useCallback(() => {
    ++generation.current;
    active.current?.abort();
    active.current = null;
  }, []);
  useEffect(() => cancel, [cancel]);
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
      cancel();
      const current = generation.current;
      const controller = new AbortController();
      active.current = controller;
      const id = threadId ?? crypto.randomUUID();
      if (threadId === null) setThreadId(id);
      const release = registerThreadStream(id, controller);

      const next: ChatMessage[] = [...messages, { role: "user", content: question }];
      setMessages(next);
      setSummary("");
      setAnswer(null);
      setError(null);
      setWorking(true);

      let accumulated = "";
      streamChat(id, question, {
        signal: controller.signal,
        onText: (delta) => {
          if (current !== generation.current || controller.signal.aborted) return;
          accumulated += delta;
          setSummary(accumulated);
        },
        onAnswer: (payload) => { if (current === generation.current && !controller.signal.aborted) setAnswer(payload); },
        onError: (message) => { if (current === generation.current && !controller.signal.aborted) setError(message); },
      })
        .catch((error: unknown) => { if (current === generation.current && !isAbortError(error)) setError(error instanceof Error ? error.message : "Could not reach the server."); })
        .finally(() => {
          release();
          if (current !== generation.current) return;
          // Append the assistant's turn so the local history stays in sync with what the server
          // streamed back. The assistant text is authoritative — never sent back to the server.
          if (accumulated && !controller.signal.aborted) {
            setMessages((msgs) => [...msgs, { role: "assistant", content: accumulated }]);
          }
          setWorking(false);
          void queryClient.invalidateQueries({ queryKey: ["listThreads"] });
          void queryClient.invalidateQueries({ queryKey: ["getThread", id] });
        });
    },
    [messages, threadId, cancel, queryClient],
  );

  const reset = useCallback(() => {
    cancel();
    setMessages([]);
    setSummary("");
    setAnswer(null);
    setError(null);
    setWorking(false);
    setThreadId(null);
  }, [cancel]);

  return useMemo(
    () => ({ messages, summary, answer, error, working, ask, reset }),
    [messages, summary, answer, error, working, ask, reset],
  );
}
