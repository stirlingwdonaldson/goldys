"use client";

import { useEffect, useRef, useState } from "react";
import { ArrowUp, MessageSquarePlus, Sparkles } from "lucide-react";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { MessageScroller, MessageScrollerButton, MessageScrollerContent, MessageScrollerProvider, MessageScrollerViewport } from "@/components/ui/message-scroller";
import { isOwner } from "@/lib/roles";
import { useApi } from "@/lib/demo-mode";
import { useAskGoldys } from "./use-ask-goldys";
import { ChatExchange } from "./chat-exchange";

interface AskGoldysDrawerProps { seniority?: string }

export const OPEN_ASK_EVENT = "goldys:open-ask";
const SUGGESTIONS = [
  "What were sales last week?",
  "Show sales for the last 7 days",
  "How much did we sell yesterday?",
];

/** Closing the sheet hides the chat; only New chat discards its local exchanges. */
export function AskGoldysDrawer({ seniority }: AskGoldysDrawerProps) {
  const [open, setOpen] = useState(false);
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

  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "j") {
        event.preventDefault();
        setOpen(previous => !previous);
      }
    };
    const onOpen = () => setOpen(true);
    window.addEventListener("keydown", onKey);
    window.addEventListener(OPEN_ASK_EVENT, onOpen);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener(OPEN_ASK_EVENT, onOpen);
    };
  }, []);

  if (!isOwner(seniority)) return null;

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-1.5" aria-label="Ask Goldy's (⌘J)" title="Ask Goldy's (⌘J)">
          <Sparkles className="h-4 w-4" aria-hidden="true" />
          <span className="hidden lg:inline">Ask Goldy&apos;s</span>
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-2xl" onOpenAutoFocus={event => { event.preventDefault(); composer.current?.focus(); }}>
        <SheetHeader className="border-b px-6 py-5 pr-12 text-left">
          <div className="flex items-center justify-between gap-4">
            <SheetTitle className="flex items-center gap-2"><Sparkles className="size-5" aria-hidden="true" />Ask Goldy&apos;s</SheetTitle>
            <Button variant="ghost" size="sm" disabled={!turns.length} onClick={() => { reset(); setQuestion(""); composer.current?.focus(); }}>
              <MessageSquarePlus aria-hidden="true" />New chat
            </Button>
          </div>
          <SheetDescription>Sales, labour and the numbers behind your pub.</SheetDescription>
        </SheetHeader>

        <MessageScrollerProvider autoScroll key={turns[0]?.id ?? "empty"}>
          <MessageScroller className="flex-1">
            <MessageScrollerViewport aria-label="Conversation">
              <MessageScrollerContent className="gap-6 px-6 py-6">
                {!turns.length ? (
                  <div className="my-auto space-y-6 py-12">
                    <div className="space-y-2">
                      <h3 className="text-2xl font-semibold tracking-tight">What would you like to know?</h3>
                      <p className="text-sm text-muted-foreground">Ask a reporting question, then follow up to explore the detail.</p>
                    </div>
                    <div className="flex flex-col items-start gap-2">
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

        <div className="border-t bg-background p-4 sm:px-6">
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
      </SheetContent>
    </Sheet>
  );
}
