"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { isAbortError } from "@/lib/api/errors";
import { registerThreadStream } from "@/lib/stream-lifecycle";
import { streamChat } from "./stream";
import type { ChatMessage, ChatTurn } from "./types";

/** Retains every exchange, including its widgets and qualifications, across follow-up questions. */
export function useAskGoldys() {
  const queryClient = useQueryClient();
  const generation = useRef(0);
  const active = useRef<AbortController | null>(null);
  const threadId = useRef<string | null>(null);
  const [turns, setTurns] = useState<ChatTurn[]>([]);
  const cancel = useCallback(() => {
    ++generation.current;
    active.current?.abort();
    active.current = null;
  }, []);
  useEffect(() => cancel, [cancel]);

  const ask = useCallback((question: string) => {
    if (!question.trim() || active.current) return;
    const current = ++generation.current;
    const controller = new AbortController();
    active.current = controller;
    // Only the thread ID and new question go to the server, never client history.
    const id = threadId.current ?? crypto.randomUUID();
    threadId.current = id;
    const release = registerThreadStream(id, controller);
    const turnId = crypto.randomUUID();
    setTurns(previous => [...previous, {
      id: turnId, question, summary: "", answer: null, error: null, working: true,
    }]);

    function update(patch: Partial<ChatTurn>) {
      if (current !== generation.current || controller.signal.aborted) return;
      setTurns(previous => previous.map(turn => turn.id === turnId ? { ...turn, ...patch } : turn));
    }

    let summary = "";
    streamChat(id, question, {
      signal: controller.signal,
      onText: delta => { summary += delta; update({ summary }); },
      onAnswer: answer => update({ answer }),
      onError: message => update({ error: new Error(message) }),
    })
      .catch((error: unknown) => {
        if (!isAbortError(error)) update({ error: error instanceof Error ? error : new Error("Could not reach the server.") });
      })
      .finally(() => {
        release();
        if (current !== generation.current) return;
        active.current = null;
        // External thread deletion can abort this stream without resetting this hook.
        setTurns(previous => previous.map(turn => turn.id === turnId ? { ...turn, working: false } : turn));
        void queryClient.invalidateQueries({ queryKey: ["listThreads"] });
        void queryClient.invalidateQueries({ queryKey: ["getThread", id] });
      });
  }, [queryClient]);

  const reset = useCallback(() => {
    cancel();
    setTurns([]);
    threadId.current = null;
  }, [cancel]);

  // Retain the text-only view for callers that need it; presentation uses complete turns.
  const messages = useMemo<ChatMessage[]>(() => turns.flatMap(turn => [
    { role: "user" as const, content: turn.question },
    ...(!turn.working && turn.summary ? [{ role: "assistant" as const, content: turn.summary }] : []),
  ]), [turns]);
  const latest = turns.at(-1);
  return {
    turns, messages, summary: latest?.summary ?? "", answer: latest?.answer ?? null,
    error: latest?.error ?? null, working: latest?.working ?? false, ask, reset,
  };
}
