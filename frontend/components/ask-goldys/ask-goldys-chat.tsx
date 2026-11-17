"use client";

import { useRef, useState } from "react";
import { ArrowUp, MessageSquarePlus } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MessageScroller, MessageScrollerButton, MessageScrollerContent, MessageScrollerProvider, MessageScrollerViewport } from "@/components/ui/message-scroller";
import { useApi } from "@/lib/demo-mode";
import { useAskGoldys } from "./use-ask-goldys";
import { ChatExchange } from "./chat-exchange";

const SUGGESTIONS = [
  "What were sales last week?",
  "Show sales for the last 7 days",
  "How much did we sell yesterday?",
];

export function AskGoldysChat() {
  const [question, setQuestion] = useState("");
  const composer = useRef<HTMLTextAreaElement>(null);
  const api = useApi();
  const { turns, working, ask, reset } = useAskGoldys();

  function submitQuestion(value: string) {
    const next = value.trim();
    if (!next || working) return;
    ask(next);
    setQuestion("");
    composer.current?.focus();
  }

  return (
    <section aria-label="Ask Goldy's" className="flex min-h-0 flex-1 flex-col">
        <div className="shrink-0 border-b px-4 py-3 md:px-6">
          <div className="flex items-center justify-between gap-4">
            <h1 className="text-lg font-semibold tracking-tight">Ask Goldy&apos;s</h1>
            <Button variant="ghost" size="sm" disabled={!turns.length} onClick={() => { reset(); setQuestion(""); composer.current?.focus(); }}>
              <MessageSquarePlus aria-hidden="true" />New chat
            </Button>
          </div>
          <Link href="/conversations" className="text-xs text-muted-foreground underline underline-offset-4">Chat history</Link>
        </div>

        <MessageScrollerProvider autoScroll key={turns[0]?.id ?? "empty"}>
          <MessageScroller className="flex-1">
            <MessageScrollerViewport aria-label="Conversation">
              <MessageScrollerContent className="mx-auto w-full max-w-3xl gap-8 px-4 py-8 md:px-6">
                {!turns.length ? (
                  <div className="my-auto space-y-6 py-12">
                    <div className="space-y-2">
                      <h3 className="text-2xl font-semibold tracking-tight">What would you like to know?</h3>
                      <p className="text-sm text-muted-foreground">Ask a reporting question, then follow up to explore the detail.</p>
                    </div>
                    <div className="flex flex-wrap gap-2">
                      {SUGGESTIONS.map(suggestion => (
                        <Button key={suggestion} variant="outline" className="h-auto whitespace-normal py-3 text-left" onClick={() => submitQuestion(suggestion)}>{suggestion}</Button>
                      ))}
                    </div>
                  </div>
                ) : null}
                {turns.map(turn => (
                  <ChatExchange key={turn.id} turn={turn} api={api} onRetry={working ? undefined : () => ask(turn.question)} />
                ))}
              </MessageScrollerContent>
            </MessageScrollerViewport>
            <MessageScrollerButton />
          </MessageScroller>
        </MessageScrollerProvider>

        <div className="mx-auto w-full max-w-3xl shrink-0 bg-background px-4 pb-4 pt-2 md:px-6">
          <form className="space-y-2 rounded-xl border bg-muted/30 p-3 focus-within:ring-1 focus-within:ring-ring" onSubmit={event => { event.preventDefault(); submitQuestion(question); }}>
            <Label htmlFor="ask-question" className="sr-only">Reporting question</Label>
            <Textarea ref={composer} id="ask-question" name="question" value={question} onChange={event => setQuestion(event.target.value)}
              placeholder={turns.length ? "Ask a follow-up question…" : "e.g. What were sales last week?"}
              className="min-h-16 max-h-40 resize-none border-0 bg-transparent p-0 shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
              onKeyDown={event => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); submitQuestion(question); } }} />
            <div className="flex items-center justify-between gap-3">
              <span className="text-xs text-muted-foreground">Enter to send · Shift + Enter for a new line</span>
              <Button type="submit" variant="brand" size="icon" aria-label="Send question" disabled={working || !question.trim()}><ArrowUp aria-hidden="true" /></Button>
            </div>
          </form>
        </div>
      </section>
  );
}
