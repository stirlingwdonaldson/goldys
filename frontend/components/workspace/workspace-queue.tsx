"use client";

import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import type { WorkspaceFollowUp } from "@/lib/api/workspace";

export function WorkspaceQueue({ items, busy, edit, remove }: {
  items: WorkspaceFollowUp[]; busy: boolean;
  edit: (id: string, prompt: string, done: () => void) => void;
  remove: (id: string) => void;
}) {
  return items.length ? <section aria-label="Follow-up queue" className="space-y-3 border-t pt-4">
    <h3 className="text-sm font-semibold">Follow-up messages</h3>
    <p className="text-xs text-muted-foreground">Queued messages run in order within this investigation’s time and charge limits.</p>
    {items.map(item => <QueueItem key={item.id} item={item} busy={busy} edit={edit} remove={remove} />)}
  </section> : null;
}

function QueueItem({ item, busy, edit, remove }: {
  item: WorkspaceFollowUp; busy: boolean;
  edit: (id: string, prompt: string, done: () => void) => void;
  remove: (id: string) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(item.prompt);
  return <div className="space-y-2 rounded-lg border p-3">
    <p className="text-xs font-medium text-muted-foreground">{item.state === "QUEUED" ? "Queued" : item.state === "TAKEN" ? "Taken by the worker · never automatically replayed" : "Cancelled"}</p>
    {editing && item.state === "QUEUED" ? <>
      <Label htmlFor={`queue-${item.id}`}>Edit queued message</Label>
      <Textarea id={`queue-${item.id}`} maxLength={16000} value={text} onChange={event => setText(event.target.value)} disabled={busy} />
      <Button size="sm" disabled={busy || !text.trim()} onClick={() => edit(item.id, text.trim(), () => setEditing(false))}>Save message</Button>
      <Button size="sm" variant="ghost" disabled={busy} onClick={() => setEditing(false)}>Cancel edit</Button>
    </> : <p className="whitespace-pre-wrap break-words text-sm">{item.prompt}</p>}
    {item.state === "QUEUED" && !editing ? <div className="flex gap-2">
      <Button variant="outline" size="sm" disabled={busy} onClick={() => { setText(item.prompt); setEditing(true); }}>Edit message</Button>
      <Button variant="ghost" size="sm" disabled={busy} onClick={() => remove(item.id)}>Remove message</Button>
    </div> : null}
  </div>;
}
