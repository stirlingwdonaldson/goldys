import type { AnswerPayload, DashboardDraft } from "./types";
import { answerSchema, draftSchema } from "@/lib/api/dashboard-schema";
import { readApiResponse } from "@/lib/api/response";
import { ApiError, isAbortError } from "@/lib/api/errors";

export function parseDraft(value: unknown): DashboardDraft | null {
  const parsed = draftSchema.safeParse(value);
  return parsed.success ? parsed.data : null;
}

/** Parse a single `\n\n`-delimited SSE block into its event name and data string. */
export function parseSseBlock(block: string): { event: string; data: string } {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith("event:")) event = line.slice(6).trim();
    else if (line.startsWith("data:")) dataLines.push(line.slice(5).trimStart());
  }
  return { event, data: dataLines.join("\n") };
}

/** The CSRF token Spring stores in a cookie, read back into the X-XSRF-TOKEN header. */
function csrfToken(): string | null {
  if (typeof document === "undefined") return null;
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

interface StreamHandlers {
  signal?: AbortSignal;
  onText: (delta: string) => void;
  onAnswer: (answer: AnswerPayload) => void;
  onError: (message: string) => void;
}

function dispatchEvent(raw: string, handlers: StreamHandlers) {
    const { event, data } = parseSseBlock(raw);
    if (!data) return;
    try {
      const payload = JSON.parse(data) as Record<string, unknown>;
      if (event === "text") {
        if (typeof payload.delta !== "string") throw new Error("Invalid text event");
        handlers.onText(payload.delta);
      }
      else if (event === "answer") handlers.onAnswer(parseAnswer(payload));
      else if (event === "error") {
        if (typeof payload.message !== "string") throw new Error("Invalid error event");
        handlers.onError(payload.message);
      }
    } catch (cause) {
      if (isAbortError(cause)) throw cause;
      throw new ApiError("INVALID_RESPONSE", "The chat response could not be read.", undefined, undefined,
        { kind: "protocol", cause, diagnostics: cause instanceof ApiError ? cause.diagnostics : [{ path: [event], code: "invalid_event" }] });
    }
}

/**
 * POST a single user turn to the chat endpoint and stream the SSE response. The backend owns the
 * thread and conversation history (server-authoritative), so the client only sends `threadId` and
 * the new `message` — never the assistant messages or a full history array. Native `fetch` +
 * `ReadableStream` (not `EventSource`, which cannot POST).
 */
export async function streamChat(
  threadId: string,
  message: string,
  handlers: StreamHandlers,
): Promise<void> {
  const csrf = csrfToken();
  const response = await fetch("/api/conversational/chat", {
    signal: handlers.signal,
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
    },
    credentials: "same-origin",
    body: JSON.stringify({ threadId, message }),
  });

  if (!response.ok) {
    await readApiResponse("/api/conversational/chat", response, { responseType: "void" });
    return;
  }

  if (!response.headers.get("content-type")?.toLowerCase().startsWith("text/event-stream")) {
    throw new ApiError("UNEXPECTED_CONTENT_TYPE", "The server did not return a chat stream.", response.headers.get("X-Correlation-ID") ?? undefined);
  }
  const reader = response.body?.getReader();
  if (!reader) {
    handlers.onError("Could not read the response stream.");
    return;
  }

  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      handlers.signal?.throwIfAborted();
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      const blocks = buffer.split(/\r?\n\r?\n/);
      buffer = blocks.pop() ?? "";
      for (const block of blocks) dispatchEvent(block, handlers);
    }
    buffer += decoder.decode();
    if (buffer.trim()) dispatchEvent(buffer, handlers);
  } finally {
    await reader.cancel().catch(() => {});
    reader.releaseLock();
  }
}

export function parseAnswer(payload: unknown): AnswerPayload {
  const parsed = answerSchema.safeParse(payload);
  if (!parsed.success) throw new ApiError("INVALID_RESPONSE", "The answer contains invalid reporting data.", undefined, undefined,
    { kind: "protocol", diagnostics: parsed.error.issues.map(issue => ({ path: issue.path.map(String), code: issue.code })) });
  return parsed.data;
}
