"use client";

import { useMemo, useState } from "react";
import Markdown, { type Components } from "react-markdown";
import remarkGfm from "remark-gfm";
import remarkParse from "remark-parse";
import { unified } from "unified";
import { toString } from "mdast-util-to-string";
import { ChevronDown } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

const parser = unified().use(remarkParse).use(remarkGfm);
const plugins = [remarkGfm];

/** Preserve the workspace citation policy: no remote media or arbitrary navigation. */
function citationUrl(value: string) {
  try {
    const url = new URL(value);
    return url.protocol === "https:" && url.hostname.endsWith(".gov.au") && !url.username && !url.password && !url.port ? url.href : undefined;
  } catch { return undefined; }
}

const components: Components = {
  a: ({ href, children }) => {
    const safe = href ? citationUrl(href) : undefined;
    return safe ? <a href={safe} target="_blank" rel="noopener noreferrer" referrerPolicy="no-referrer" className="underline underline-offset-2">{children}</a> : <span>{children}</span>;
  },
  img: ({ alt }) => <span>{alt}</span>,
  table: ({ children }) => <Table>{children}</Table>,
  thead: ({ children }) => <TableHeader>{children}</TableHeader>,
  tbody: ({ children }) => <TableBody>{children}</TableBody>,
  tr: ({ children }) => <TableRow>{children}</TableRow>,
  th: ({ children, style }) => <TableHead style={style}>{children}</TableHead>,
  td: ({ children, style }) => <TableCell style={style}>{children}</TableCell>,
};

export function AnswerMarkdown({ text }: { text: string }) {
  return <div className="min-w-0 space-y-3 break-words text-sm leading-relaxed tabular-nums [&_p]:whitespace-pre-wrap [&_ul]:list-disc [&_ul]:space-y-1 [&_ul]:pl-5 [&_ol]:list-decimal [&_ol]:space-y-1 [&_ol]:pl-5 [&_li>p]:my-1 [&_h1]:text-lg [&_h1]:font-semibold [&_h2]:text-base [&_h2]:font-semibold [&_h3]:font-semibold [&_h4]:font-semibold [&_h5]:font-semibold [&_h6]:font-semibold [&_blockquote]:border-l-2 [&_blockquote]:pl-3 [&_blockquote]:text-muted-foreground [&_pre]:overflow-x-auto [&_pre]:rounded-md [&_pre]:bg-muted [&_pre]:p-3 [&_code]:font-mono [&_code]:text-xs [&_hr]:border-border">
    <Markdown remarkPlugins={plugins} components={components}>{text}</Markdown>
  </div>;
}

/** The full original response is retained; the preview never reparses a cut Markdown fragment. */
export function AnswerText({ text }: { text: string }) {
  const [open, setOpen] = useState(false);
  const preview = useMemo(() => {
    const tree = parser.parse(text);
    const first = tree.children.find(node => node.type === "paragraph");
    const plain = toString(first ?? tree).replace(/\s+/g, " ").trim();
    return plain.length > 400 ? `${plain.slice(0, 400).replace(/\s+\S*$/, "")}…` : plain;
  }, [text]);
  if (text.length <= 600 && text.split("\n").length <= 8) return <AnswerMarkdown text={text} />;
  return <Collapsible open={open} onOpenChange={setOpen} className="min-w-0 space-y-2">
    {!open ? <p className="break-words text-sm leading-relaxed tabular-nums">{preview}</p> : null}
    <CollapsibleTrigger asChild><Button variant="ghost" size="sm" className="group gap-2 text-muted-foreground">
      {open ? "Show less" : "Read full answer"}<ChevronDown aria-hidden="true" className="size-4 group-data-[state=open]:rotate-180" />
    </Button></CollapsibleTrigger>
    <CollapsibleContent><AnswerMarkdown text={text} /></CollapsibleContent>
  </Collapsible>;
}
