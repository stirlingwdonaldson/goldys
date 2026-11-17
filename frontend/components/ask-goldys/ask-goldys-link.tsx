"use client";

import { useEffect } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { MessageSquare } from "lucide-react";
import { Button } from "@/components/ui/button";
import { isOwner } from "@/lib/roles";

export const OPEN_ASK_EVENT = "goldys:open-ask";

export function AskGoldysLink({ seniority }: { seniority?: string }) {
  const router = useRouter();
  const params = useSearchParams();
  const allowed = isOwner(seniority);
  // Preserve links saved before Ask Goldy's moved out of the drawer.
  const investigation = params.get("investigation");
  const href = investigation ? `/ask-goldys?investigation=${encodeURIComponent(investigation)}` : "/ask-goldys";
  useEffect(() => {
    if (!allowed) return;
    const open = () => router.push(href);
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "j") {
        event.preventDefault();
        open();
      }
    };
    window.addEventListener("keydown", onKey);
    window.addEventListener(OPEN_ASK_EVENT, open);
    return () => {
      window.removeEventListener("keydown", onKey);
      window.removeEventListener(OPEN_ASK_EVENT, open);
    };
  }, [allowed, href, router]);
  if (!allowed) return null;
  return <Button asChild variant="ghost" size="sm">
    <Link href={href} aria-label="Ask Goldy's (⌘J)" title="Ask Goldy's (⌘J)">
      <MessageSquare className="size-4" aria-hidden="true" /><span className="hidden lg:inline">Ask Goldy&apos;s</span>
    </Link>
  </Button>;
}
