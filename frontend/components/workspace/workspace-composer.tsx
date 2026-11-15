"use client";

import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import type { WorkspaceCommands } from "./use-workspace-commands";

export function WorkspaceComposer({ commands, working, disabled }: { commands: WorkspaceCommands; working: boolean; disabled: boolean }) {
  const { question, setQuestion, uncertain, busy, send, sendQuestion } = commands;
  const label = working ? "Queue a follow-up" : "Reporting question";
  let button = working ? "Queue message" : "Send question";
  if (uncertain) button = "Retry sending";
  if (send.isPending) button = "Sending…";
  return <form className="space-y-3 border-t bg-background px-6 py-4" onSubmit={event => { event.preventDefault(); sendQuestion(); }}>
    <Label htmlFor="workspace-question">{label}</Label>
    <Textarea id="workspace-question" value={question} onChange={event => setQuestion(event.target.value)} maxLength={16000}
      disabled={busy || uncertain || disabled} placeholder="e.g. Compare sales for the last two weeks" className="min-h-20 resize-none" />
    <div className="flex items-center justify-between gap-3">
      <span className="text-xs text-muted-foreground">{uncertain ? "The original message and request ID will be reused." : "Up to 20 follow-ups per investigation."}</span>
      <Button type="submit" variant="brand" disabled={busy || (!question.trim() && !uncertain) || disabled}>{button}</Button>
    </div>
  </form>;
}
