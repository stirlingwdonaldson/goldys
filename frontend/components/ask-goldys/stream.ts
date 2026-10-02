import type { AnswerPayload, ChatMessage } from "./types";

/** Parse a single `\n\n`-delimited SSE block into its event name and data string. */
export function parseSseBlock(block: string): { event: string; data: string } {
  let event = "message";
  const dataLines: string[] = [];
  for (const line of block.split("\n")) {
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
  onText: (delta: string) => void;
  onAnswer: (answer: AnswerPayload) => void;
  onError: (message: string) => void;
}

/**
 * POST the message history to the chat endpoint and stream the SSE response. Native
 * `fetch` + `ReadableStream` (not `EventSource`, which cannot POST).
 */
export async function streamChat(messages: ChatMessage[], handlers: StreamHandlers): Promise<void> {
  const csrf = csrfToken();
  const response = await fetch("/api/conversational/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Accept: "text/event-stream",
      ...(csrf ? { "X-XSRF-TOKEN": csrf } : {}),
    },
    credentials: "same-origin",
    body: JSON.stringify({ messages }),
  });

  if (!response.ok) {
    let message = `Request failed (HTTP ${response.status}).`;
    try {
      const body = (await response.json()) as { message?: string };
      if (body?.message) message = body.message;
    } catch {
      // keep the default message
    }
    handlers.onError(message);
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    handlers.onError("Could not read the response stream.");
    return;
  }

  const decoder = new TextDecoder();
  let buffer = "";
  const dispatch = (raw: string) => {
    const { event, data } = parseSseBlock(raw);
    if (!data) return;
    try {
      const payload = JSON.parse(data) as Record<string, unknown>;
      if (event === "text" && typeof payload.delta === "string") handlers.onText(payload.delta);
      else if (event === "answer") handlers.onAnswer(payload as unknown as AnswerPayload);
      else if (event === "error" && typeof payload.message === "string")
        handlers.onError(payload.message);
    } catch {
      // a malformed event is dropped, not fatal
    }
  };

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const blocks = buffer.split("\n\n");
    buffer = blocks.pop() ?? "";
    for (const block of blocks) dispatch(block);
  }
  buffer += decoder.decode();
  if (buffer.trim()) dispatch(buffer);
}
