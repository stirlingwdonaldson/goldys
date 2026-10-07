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
import { Input } from "@/components/ui/input";
import { isOwner } from "@/lib/roles";
import { useApi } from "@/lib/demo-mode";
import { useAskGoldys } from "./use-ask-goldys";
import { AnswerBlock } from "./answer-block";

interface AskGoldysDrawerProps {
  seniority?: string;
}

const SUGGESTIONS = [
  "What were sales last week?",
  "Show sales for the last 7 days",
  "How much did we sell yesterday?",
];

/** The global "Ask Goldy's" drawer. Fail-closed: renders nothing for non-Owners. */
export function AskGoldysDrawer({ seniority }: AskGoldysDrawerProps) {
  const isOwnerRole = isOwner(seniority);
  const [open, setOpen] = useState(false);
  const api = useApi();
  const { summary, answer, error, working, ask } = useAskGoldys();

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setOpen((o) => !o);
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  if (!isOwnerRole) return null;

  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger asChild>
        <Button variant="outline" size="sm" className="gap-1.5">
          <Sparkles className="h-4 w-4" aria-hidden="true" />
          Ask Goldy&apos;s
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
                  onClick={() => ask(s)}
                  className="block w-full rounded-md border px-3 py-2 text-left text-sm hover:bg-muted"
                >
                  {s}
                </button>
              ))}
            </div>
          ) : null}

          {working ? <p className="text-sm text-muted-foreground">Working…</p> : null}

          {summary || answer || error ? (
            <AnswerBlock summary={summary} answer={answer} error={error} api={api} />
          ) : null}
        </div>

        <form
          className="flex gap-2"
          onSubmit={(e) => {
            e.preventDefault();
            const input = e.currentTarget.elements.namedItem("question") as HTMLInputElement;
            const q = input.value.trim();
            if (q) ask(q);
            input.value = "";
          }}
        >
          <Input name="question" placeholder="e.g. What were sales last week?" />
          <Button type="submit" disabled={working}>
            Ask
          </Button>
        </form>
      </SheetContent>
    </Sheet>
  );
}
