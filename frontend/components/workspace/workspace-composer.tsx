"use client";

import type { KeyboardEvent } from "react";
import { ArrowUp, Loader2, Square } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { WorkspaceCommands } from "./use-workspace-commands";

export function WorkspaceComposer({ commands, working, disabled, stop }: { commands: WorkspaceCommands; working: boolean; disabled: boolean; stop: () => void }) {
  const { question, setQuestion, uncertain, busy, sendQuestion } = commands;
  const label = working ? "Queue a follow-up" : "Reporting question";
  const { label: button, Icon, stopping } = composerAction(commands, working);
  const hasMessage = Boolean(question.trim() || uncertain);
  return <form className="mx-auto w-full max-w-3xl shrink-0 bg-background px-4 pb-4 pt-2 md:px-6" onSubmit={event => { event.preventDefault(); sendQuestion(); }}>
    <div className="space-y-3 rounded-xl border bg-muted/30 p-3 focus-within:ring-1 focus-within:ring-ring">
    <Label className="sr-only" htmlFor="workspace-question">{label}</Label>
    <Textarea id="workspace-question" value={question} onChange={event => setQuestion(event.target.value)} maxLength={16000}
      disabled={busy || uncertain || disabled} placeholder="e.g. Compare sales for the last two weeks" className="min-h-20 max-h-40 resize-none border-0 bg-transparent p-0 shadow-none focus-visible:ring-0 focus-visible:ring-offset-0"
      onKeyDown={sendOnEnter} />
    <div className="flex items-center justify-between gap-3">
      <span className="text-xs text-muted-foreground">{uncertain ? "The original message and request ID will be reused." : working ? "Follow-ups queue while Goldy’s works." : "Enter to send · Shift + Enter for a new line"}</span>
      <Button type={stopping ? "button" : "submit"} onClick={stopping ? stop : undefined} variant="default" size="icon" className="rounded-full" aria-label={button} title={button} disabled={busy || disabled || !(stopping || hasMessage)}><Icon className={busy ? "animate-spin motion-reduce:animate-none" : "size-4"} fill={stopping ? "currentColor" : "none"} aria-hidden="true" /></Button>
    </div>
    </div>
  </form>;
}

function composerAction({ question, uncertain, busy, send }: WorkspaceCommands, working: boolean) {
  if (send.isPending) return { label: "Sending…", Icon: Loader2, stopping: false };
  if (working && !question.trim() && !uncertain) {
    return { label: busy ? "Stopping…" : "Stop investigation", Icon: busy ? Loader2 : Square, stopping: true };
  }
  return { label: uncertain ? "Retry sending" : working ? "Queue message" : "Send question", Icon: ArrowUp, stopping: false };
}

function sendOnEnter(event: KeyboardEvent<HTMLTextAreaElement>) {
  if (event.key !== "Enter" || event.shiftKey || event.nativeEvent.isComposing) return;
  event.preventDefault();
  event.currentTarget.form?.requestSubmit();
}
