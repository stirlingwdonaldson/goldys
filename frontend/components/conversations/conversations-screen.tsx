"use client";

import { useState } from "react";
import { ArrowLeft, MessageSquareText, Pencil, Trash2 } from "lucide-react";
import { useApiData } from "@/lib/use-api-data";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { PermissionDenied } from "@/components/states/permission-denied";
import type {
  Api,
  ConversationMessage,
  ConversationThreadSummary,
} from "@/lib/api/types";

/** A stable, short human-readable date for a thread row. */
function formatUpdatedAt(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" });
}

/** Newest first (the backend already returns this order; sorted defensively). */
function sortThreads(list: ConversationThreadSummary[]): ConversationThreadSummary[] {
  return [...list].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

/**
 * The Ask Goldy's conversation history: a list of threads (title, last-updated, preview) with
 * open/rename/delete, and a transcript view once a thread is opened. Takes an explicit `api` so
 * tests can inject a stub; the page passes the demo/live singleton.
 */
export function ConversationsScreen({ api }: { api: Api }) {
  const list = useApiData((a) => a.listThreads(), [], api);
  const [selectedId, setSelectedId] = useState<string | null>(null);

  if (list.loading) return <LoadingState rows={3} />;
  if (list.error) {
    if (list.error.code === "NOT_PERMITTED") return <PermissionDenied subject="conversations" />;
    return (
      <ErrorState
        title="Couldn't load conversations"
        message={list.error.message}
        correlationId={list.error.correlationId}
        onRetry={list.reload}
      />
    );
  }

  if (selectedId) {
    return <ThreadTranscript api={api} threadId={selectedId} onBack={() => setSelectedId(null)} />;
  }

  const threads = sortThreads(list.data ?? []);

  if (threads.length === 0) {
    return (
      <EmptyState
        title="No conversations yet"
        description="Ask Goldy's a question and your threads will appear here."
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <h1 className="text-xl font-semibold">Conversations</h1>
        <p className="text-sm text-muted-foreground">Your Ask Goldy&apos;s history.</p>
      </div>

      <ul className="flex flex-col gap-2">
        {threads.map((t) => (
          <ThreadRow
            key={t.id}
            thread={t}
            api={api}
            onOpen={() => setSelectedId(t.id)}
            onChanged={() => list.reload()}
          />
        ))}
      </ul>
    </div>
  );
}

interface ThreadRowProps {
  thread: ConversationThreadSummary;
  api: Api;
  onOpen: () => void;
  onChanged: () => void;
}

/** One list entry: title (opens the transcript), preview, last-updated, rename and delete. */
function ThreadRow({ thread, api, onOpen, onChanged }: ThreadRowProps) {
  const [renameOpen, setRenameOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [title, setTitle] = useState(thread.title);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function rename() {
    const next = title.trim();
    if (!next || saving) return;
    setSaving(true);
    setError(null);
    try {
      await api.renameThread(thread.id, next);
      setRenameOpen(false);
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't rename this conversation.");
    } finally {
      setSaving(false);
    }
  }

  async function remove() {
    if (saving) return;
    setSaving(true);
    setError(null);
    try {
      await api.deleteThread(thread.id);
      setDeleteOpen(false);
      onChanged();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Couldn't delete this conversation.");
      setSaving(false);
    }
  }

  return (
    <li className="group flex items-start gap-3 rounded-lg border p-4">
      <MessageSquareText className="mt-0.5 h-4 w-4 shrink-0 text-muted-foreground" aria-hidden="true" />
      <div className="min-w-0 flex-1">
        <button
          type="button"
          onClick={onOpen}
          className="rounded-sm text-left text-sm font-semibold hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {thread.title}
        </button>
        {thread.lastPreview ? (
          <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{thread.lastPreview}</p>
        ) : null}
        <p className="mt-1 text-xs text-muted-foreground">Updated {formatUpdatedAt(thread.updatedAt)}</p>
        {error ? (
          <p role="alert" className="mt-1 text-xs text-destructive">
            {error}
          </p>
        ) : null}
      </div>
      <div className="flex shrink-0 items-center gap-1">
        <Button
          variant="ghost"
          size="icon"
          aria-label={`Rename ${thread.title}`}
          onClick={() => {
            setTitle(thread.title);
            setError(null);
            setRenameOpen(true);
          }}
        >
          <Pencil className="h-4 w-4" aria-hidden="true" />
        </Button>
        <Button
          variant="ghost"
          size="icon"
          aria-label={`Delete ${thread.title}`}
          onClick={() => {
            setError(null);
            setDeleteOpen(true);
          }}
        >
          <Trash2 className="h-4 w-4" aria-hidden="true" />
        </Button>
      </div>

      <Dialog open={renameOpen} onOpenChange={setRenameOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Rename conversation</DialogTitle>
            <DialogDescription>Give this thread a clearer title.</DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            <Label htmlFor="thread-title">Title</Label>
            <Input
              id="thread-title"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  rename();
                }
              }}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRenameOpen(false)}>
              Cancel
            </Button>
            <Button onClick={rename} disabled={saving || !title.trim()}>
              {saving ? "Saving…" : "Save"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={deleteOpen} onOpenChange={setDeleteOpen}>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Delete conversation?</DialogTitle>
            <DialogDescription>
              This permanently removes &ldquo;{thread.title}&rdquo; and its messages.
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <Button variant="outline" onClick={() => setDeleteOpen(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={remove} disabled={saving}>
              {saving ? "Deleting…" : "Delete"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </li>
  );
}

interface ThreadTranscriptProps {
  api: Api;
  threadId: string;
  onBack: () => void;
}

/** A thread's full transcript, reached by opening a row. */
function ThreadTranscript({ api, threadId, onBack }: ThreadTranscriptProps) {
  const thread = useApiData((a) => a.getThread(threadId), [threadId], api);

  if (thread.loading) return <LoadingState rows={3} />;
  if (thread.error) {
    if (thread.error.code === "NOT_PERMITTED") return <PermissionDenied subject="this conversation" />;
    return (
      <ErrorState
        title="Couldn't load this conversation"
        message={thread.error.message}
        correlationId={thread.error.correlationId}
        onRetry={thread.reload}
      />
    );
  }

  const doc = thread.data;

  return (
    <div className="flex flex-col gap-6">
      <div className="flex min-w-0 items-center gap-2">
        <Button variant="ghost" size="sm" onClick={onBack}>
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Back
        </Button>
        <h2 className="truncate text-lg font-semibold">{doc?.title}</h2>
      </div>

      {!doc || doc.messages.length === 0 ? (
        <EmptyState title="No messages" description="This conversation has no messages yet." />
      ) : (
        <ol className="flex flex-col gap-3">
          {doc.messages.map((m) => (
            <MessageBlock key={m.id} message={m} />
          ))}
        </ol>
      )}
    </div>
  );
}

/** One message in the transcript: role, content, and any tool trace attached to it. */
function MessageBlock({ message }: { message: ConversationMessage }) {
  const isUser = message.role === "user";
  return (
    <li className={`flex flex-col gap-1 rounded-lg border p-4 ${isUser ? "bg-muted/40" : ""}`}>
      <div className="flex items-center gap-2">
        <Badge variant={isUser ? "secondary" : "outline"}>{isUser ? "You" : "Goldy's"}</Badge>
      </div>
      <p className="whitespace-pre-wrap text-sm">{message.content}</p>
      {message.toolTrace.length ? (
        <details className="text-xs text-muted-foreground">
          <summary className="cursor-pointer underline">How I got this</summary>
          <ul className="mt-1 space-y-1 border-l pl-3">
            {message.toolTrace.map((t, i) => (
              <li key={i}>
                {t.tool}
                {t.description ? ` — ${t.description}` : ""}
              </li>
            ))}
          </ul>
        </details>
      ) : null}
    </li>
  );
}
