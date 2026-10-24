"use client";

import { useEffect, useState } from "react";
import { Sparkles } from "lucide-react";
import {
  Sheet,
  SheetContent,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from "@/components/ui/sheet";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { isOwner } from "@/lib/roles";
import { useApi } from "@/lib/demo-mode";
import { useAskGoldys } from "./use-ask-goldys";
import { AnswerBlock } from "./answer-block";

interface AskGoldysDrawerProps {
  seniority?: string;
}

/** Window event that opens the drawer from elsewhere (the command palette). */
export const OPEN_ASK_EVENT = "goldys:open-ask";

const SUGGESTIONS = [
  "What were sales last week?",
  "Show sales for the last 7 days",
  "How much did we sell yesterday?",
];

/** The global "Ask Goldy's" drawer. Fail-closed: renders nothing for non-Owners. */
export function AskGoldysDrawer({ seniority }: AskGoldysDrawerProps) {
  const isOwnerRole = isOwner(seniority);
  const [open, setOpen] = useState(false);
  const [question, setQuestion] = useState("");
  const [submittedQuestion, setSubmittedQuestion] = useState("");
  const api = useApi();
  const { summary, answer, error, working, ask, reset } = useAskGoldys();

  useEffect(() => { if (!open) { reset(); setQuestion(""); setSubmittedQuestion(""); } }, [open, reset]);

  function submitQuestion(value: string) {
    setQuestion(value);
    setSubmittedQuestion(value);
    ask(value);
  }

  // ⌘J toggles the drawer (⌘K belongs to the command palette, which can also
  // open this drawer by dispatching OPEN_ASK_EVENT).
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "j") {
        e.preventDefault();
        setOpen((o) => !o);
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

  if (!isOwnerRole) return null;

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-1.5" aria-label="Ask Goldy's (⌘J)" title="Ask Goldy's (⌘J)">
          <Sparkles className="h-4 w-4" aria-hidden="true" />
          {/* Icon-only on narrow headers so the label never wraps. */}
          <span className="hidden lg:inline">Ask Goldy&apos;s</span>
        </Button>
      </SheetTrigger>
      <SheetContent side="right" className="flex w-full flex-col gap-4 sm:max-w-lg">
        <SheetHeader>
          <SheetTitle>Ask Goldy&apos;s</SheetTitle>
        </SheetHeader>

        <div className="flex-1 space-y-4 overflow-y-auto">
          {!summary && !answer && !error ? (
            <div className="space-y-2">
              <p className="text-sm text-muted-foreground">Ask a reporting question.</p>
              {SUGGESTIONS.map((s) => (
                <button
                  key={s}
                  onClick={() => submitQuestion(s)}
                  className="block w-full rounded-md border px-3 py-2 text-left text-sm hover:bg-muted"
                >
                  {s}
                </button>
              ))}
            </div>
          ) : null}

          {working ? <p className="text-sm text-muted-foreground">Working…</p> : null}

          {summary || answer || error ? (
            <AnswerBlock summary={summary} answer={answer} error={error} api={api} onRetry={() => ask(submittedQuestion)} />
          ) : null}
        </div>

        <Label htmlFor="ask-question">Reporting question</Label>
        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            const q = question.trim();
            if (q && !working) submitQuestion(q);
          }}
        >
          <Input id="ask-question" name="question" value={question}
            onChange={(event) => setQuestion(event.target.value)}
            placeholder="e.g. What were sales last week?" />
          <Button type="submit" disabled={working}>
            Ask
          </Button>
        </form>
      </SheetContent>
    </Sheet>
  );
}
