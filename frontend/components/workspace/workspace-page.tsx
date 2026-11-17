"use client";

import Link from "next/link";
import { useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { MessageSquarePlus, History } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useApi } from "@/lib/demo-mode";
import { WorkspacePanel } from "./workspace-panel";

export function WorkspacePage() {
  const api = useApi();
  const [newChat, setNewChat] = useState(0);
  const params = useSearchParams();
  const router = useRouter();
  const selection = params.get("investigation");
  const jobId = selection === "new" ? null : selection;
  function select(id: string | null) {
    if (!id) setNewChat(value => value + 1);
    router.push(id ? `/ask-goldys?investigation=${encodeURIComponent(id)}` : "/ask-goldys", { scroll: false });
  }
  return <section aria-label="Ask Goldy's" className="flex min-h-0 flex-1 flex-col">
    <div className="flex shrink-0 items-center justify-between gap-3 border-b px-4 py-3 md:px-6">
      <h1 className="text-lg font-semibold tracking-tight">Ask Goldy&apos;s</h1>
      <div className="flex items-center gap-1">
        <Button asChild variant="ghost" size="sm"><Link href="/conversations" aria-label="Chat history"><History aria-hidden="true" /><span className="hidden sm:inline">Chat history</span></Link></Button>
        <Button variant="outline" size="sm" onClick={() => select(null)}><MessageSquarePlus aria-hidden="true" />New chat</Button>
      </div>
    </div>
    <WorkspacePanel key={selection ?? `new-${newChat}`} api={api} jobId={jobId} select={select} />
  </section>;
}
