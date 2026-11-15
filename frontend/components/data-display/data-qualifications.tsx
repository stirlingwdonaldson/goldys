"use client";

import { Suspense, useRef, useState, type ReactNode } from "react";
import Link from "next/link";
import { useIncompleteNotices } from "@/lib/incomplete-notices";
import { useInvestigationUrl } from "@/lib/use-investigation-url";
import { Button } from "@/components/ui/button";
import { HoverCard, HoverCardContent, HoverCardTrigger } from "@/components/ui/hover-card";
import { Sheet, SheetContent, SheetHeader, SheetTitle, SheetDescription } from "@/components/ui/sheet";

/** Recognise the producer's coverage notices individually; unknown interpretation warnings stay visible. */
export function isIncompleteNotice(notice: string) {
  // PeriodMetricReader prefixes evidence with its catalogue metric ID.
  const message = notice.trim().replace(/^[a-z][a-z0-9_]*(?:\.[a-z0-9_]+)+:\s*/i, "");
  return /^(no attributed line detail;|incomplete(?:\b|:)|partial coverage\b|\d+ day\(s\) unresolved\b|ratio unavailable: missing inputs\b|no shared coverage for\b|unreconciled:|missing inputs\b)/i.test(message);
}
interface Props {
  notices?: string[] | null;
  label?: string;
  scope?: string;
  /** Only the affected value/result belongs here; unaffected information stays outside. */
  children?: ReactNode;
  recovery?: { href: string; label: string };
}
export function DataQualifications({ notices, label = "Data quality", scope, children, recovery }: Props) {
  const { showIncomplete } = useIncompleteNotices();
  const unique = [...new Set(notices ?? [])];
  const incomplete = unique.filter(isIncompleteNotice);
  const visible = unique.filter(notice => !isIncompleteNotice(notice));
  return <>
    {incomplete.length ? <Suspense fallback={<span className="text-sm text-muted-foreground">{label}: incomplete</span>}>
      <IssueControl notices={incomplete} label={label} scope={scope} recovery={recovery} evidence={children} />
    </Suspense> : null}
    {(!incomplete.length || showIncomplete) ? children : null}
    {(visible.length || (showIncomplete && incomplete.length)) ? <div role="status" className="rounded-md border p-3 text-sm text-muted-foreground">
      {showIncomplete && incomplete.length ? <strong className="text-foreground">Incomplete — known subtotals only. </strong> : null}
      <ul className="list-inside list-disc">{(showIncomplete ? unique : visible).map(notice => <li key={notice}>{notice}</li>)}</ul>
    </div> : null}
  </>;
}

function IssueControl({ notices, label, scope, recovery, evidence }: { notices: string[]; label: string; scope?: string; recovery?: Props["recovery"]; evidence?: ReactNode }) {
  const panel = useInvestigationUrl();
  const trigger = useRef<HTMLButtonElement>(null);
  const [hint, setHint] = useState(false);
  const id = `${label}:${scope ?? "current scope"}`;
  const open = panel.params.get("issue") === id;
  function inspect() { setHint(false); panel.openPanel("issue", id); }
  return <>
    <HoverCard open={hint && !open} onOpenChange={setHint} openDelay={150} closeDelay={300}>
      <HoverCardTrigger asChild>
        <Button ref={trigger} variant="outline" size="sm" className="h-auto min-h-8 max-w-full whitespace-normal py-1 text-left" aria-label={`${label}: incomplete — view issue`} aria-haspopup="dialog" onFocus={() => setHint(true)} onClick={inspect} onKeyDown={event => { if (event.key === "Escape") { setHint(false); event.stopPropagation(); } }}>
          {label}: incomplete
        </Button>
      </HoverCardTrigger>
      <HoverCardContent className="w-80 text-sm" onEscapeKeyDown={() => setHint(false)}>
        <p>{notices[0]}</p>
        <Button variant="link" className="mt-1 h-auto p-0" onClick={inspect}>View full issue for {label}</Button>
      </HoverCardContent>
    </HoverCard>
    <Sheet open={open} onOpenChange={value => { if (!value) panel.closePanel("issue"); }}>
      <SheetContent className="overflow-y-auto sm:max-w-xl" onCloseAutoFocus={event => { setHint(false); panel.restoreFocus(event, trigger.current); }}>
        <SheetHeader><SheetTitle>{label}: incomplete data</SheetTitle><SheetDescription>{scope ?? "The current reporting scope shown on this page."}</SheetDescription></SheetHeader>
        <div className="mt-6 space-y-4 text-sm">
          <section><h3 className="font-medium">Available evidence</h3><ul className="mt-2 list-inside list-disc space-y-2">{notices.map(notice => <li key={notice}>{notice}</li>)}</ul></section>
          <p>Missing or disputed inputs prevent a complete result. Known observations are not a complete total; unavailable values are not zero.</p>
          {evidence ? <section className="space-y-2"><h3 className="font-medium">Affected observations — incomplete</h3>{evidence}</section> : null}
          {recovery ? <Button asChild variant="outline"><Link href={recovery.href}>{recovery.label}</Link></Button> : null}
        </div>
      </SheetContent>
    </Sheet>
  </>;
}
