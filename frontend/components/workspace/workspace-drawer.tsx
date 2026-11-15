"use client";

import { useRef } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useApi } from "@/lib/demo-mode";
import { isOwner } from "@/lib/roles";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from "@/components/ui/sheet";
import { WorkspacePanel } from "./workspace-panel";

/** Additive preview entry point. Existing Ask and legacy history remain available. */
export function WorkspaceDrawer({ seniority }: { seniority?: string }) {
  const api = useApi();
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const pushed = useRef(false);
  const selection = params.get("investigation");
  const open = selection !== null;
  function select(id: string | null, push = false) {
    const next = new URLSearchParams(params.toString());
    next.set("investigation", id ?? "new");
    const url = `${pathname}?${next}`;
    if (push) { pushed.current = true; router.push(url, { scroll: false }); }
    else router.replace(url, { scroll: false });
  }
  function close() {
    if (pushed.current) { pushed.current = false; router.back(); return; }
    const next = new URLSearchParams(params.toString());
    next.delete("investigation");
    router.replace(`${pathname}${next.size ? `?${next}` : ""}`, { scroll: false });
  }
  if (!isOwner(seniority)) return null;
  return <Sheet open={open} onOpenChange={value => value ? select(null, true) : close()}>
    <SheetTrigger asChild><Button variant="outline" size="sm" data-workspace-trigger>Investigations</Button></SheetTrigger>
    <SheetContent side="right" className="flex w-full flex-col gap-0 p-0 sm:max-w-2xl"
      onCloseAutoFocus={event => { event.preventDefault(); document.querySelector<HTMLButtonElement>("[data-workspace-trigger]")?.focus(); }}>
      <SheetHeader className="space-y-3 border-b px-6 py-5 pr-12 text-left">
        <SheetTitle>Ask Goldy’s investigations</SheetTitle>
        <SheetDescription>Reporting questions, saved progress and queued follow-ups.</SheetDescription>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="outline" size="sm" onClick={() => select(null)}>All investigations</Button>
          <Link href="/conversations" className="text-sm underline">Chat history</Link>
        </div>
      </SheetHeader>
      {open ? <WorkspacePanel key={selection} api={api} jobId={selection === "new" ? null : selection} select={select} /> : null}
    </SheetContent>
  </Sheet>;
}
