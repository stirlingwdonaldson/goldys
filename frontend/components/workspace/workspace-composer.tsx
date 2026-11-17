"use client";

import { useRef } from "react";
import { ArrowUp, Loader2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { WorkspaceCommands } from "./use-workspace-commands";

export function WorkspaceComposer({ commands, working, disabled }: { commands: WorkspaceCommands; working: boolean; disabled: boolean }) {
  const sendButton = useRef<HTMLButtonElement>(null);
  const { question, setQuestion, uncertain, busy, send, sendQuestion } = commands;
  const label = working ? "Queue a follow-up" : "Reporting question";
  let button = working ? "Queue message" : "Send question";
  if (uncertain) button = "Retry sending";
  if (send.isPending) button = "Sending…";
  return <form className="mx-auto w-full max-w-3xl shrink-0 bg-background px-4 pb-4 pt-2 md:px-6" onSubmit={event => { event.preventDefault(); sendQuestion(); }}>
    <div className="space-y-3 rounded-xl border bg-muted/30 p-3 focus-within:ring-1 focus-within:ring-ring">
    <Label className="sr-only" htmlFor="workspace-question">{label}</Label>
    <Textarea id="workspace-question" value={question} onChange={event => setQuestion(event.target.value)} maxLength={16000}
      disabled={busy || uncertain || disabled} placeholder="e.g. Compare sales for the last two weeks" className="min-h-20 max-h-40 resize-none border-0 bg-transparent p-0 shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
      onKeyDown={event => { if (event.key === "Enter" && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); sendButton.current?.click(); } }} />
    <div className="flex items-center justify-between gap-3">
      <span className="text-xs text-muted-foreground">{uncertain ? "The original message and request ID will be reused." : working ? "Follow-ups queue while Goldy’s works." : "Enter to send · Shift + Enter for a new line"}</span>
      <Button ref={sendButton} type="submit" variant="brand" size="icon" aria-label={button} title={button} disabled={busy || (!question.trim() && !uncertain) || disabled}>{send.isPending ? <Loader2 className="animate-spin motion-reduce:animate-none" aria-hidden="true" /> : <ArrowUp aria-hidden="true" />}</Button>
    </div>
    </div>
  </form>;
}
