"use client";

import { useMemo, useState } from "react";
import { CircleCheck, Info } from "lucide-react";
import { PageHeader } from "@/components/layout/page-header";
import { Section } from "@/components/layout/section";
import { Badge } from "@/components/ui/badge";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { FocusSwitcher, WorkList } from "@/components/my-work/work-list";
import { useFocus, useWorkQueue } from "@/components/my-work/use-work-queue";
import { CATEGORY_LABEL, filterByFocus, type WorkCategory, type WorkSeverity } from "@/lib/work-queue";

type CategoryFilter = WorkCategory | "all";

const GROUPS: { severity: WorkSeverity; title: string; description: string }[] = [
  { severity: "high", title: "Urgent", description: "Figures are wrong or missing until these are dealt with." },
  { severity: "medium", title: "Soon", description: "Worth clearing before the next report goes out." },
  { severity: "low", title: "When you can", description: "Nothing is wrong yet, but these will matter." },
];

/**
 * One queue for everything that needs a person: figures sources disagree on, failing
 * data feeds, invoice mismatches and rule changes that didn't apply. Each row links to
 * the page where it's resolved, so this page never duplicates those workflows.
 */
export default function MyWorkPage() {
  const { data, loading, error, reload } = useWorkQueue();
  const [focus, setFocus] = useFocus();
  const [category, setCategory] = useState<CategoryFilter>("all");

  const inFocus = useMemo(() => filterByFocus(data?.items ?? [], focus), [data, focus]);
  const visible = category === "all" ? inFocus : inFocus.filter((i) => i.category === category);
  const counts = useMemo(() => {
    const c: Record<CategoryFilter, number> = { all: inFocus.length, decision: 0, source: 0, invoice: 0, recompute: 0 };
    for (const i of inFocus) c[i.category] += 1;
    return c;
  }, [inFocus]);

  if (loading) return <LoadingState rows={4} />;
  if (error || !data) {
    return (
      <ErrorState
        title="Couldn't load your work"
        message={error?.message ?? "Nothing came back."}
        correlationId={error?.correlationId}
        onRetry={reload}
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="My work"
        description="Everything that needs a decision or a fix, most urgent first."
        status={inFocus.length > 0 ? <Badge variant="conflict">{inFocus.length} open</Badge> : null}
        actions={<FocusSwitcher value={focus} onChange={setFocus} />}
      />

      {data.unavailable.length > 0 ? (
        <p className="flex items-start gap-2 rounded-lg border bg-muted/40 px-3 py-2 text-xs text-muted-foreground">
          <Info className="mt-0.5 size-3.5 shrink-0" aria-hidden="true" />
          <span>
            Not included: {data.unavailable.join(", ")}. Your role may not cover them, or they couldn&apos;t be
            reached.
          </span>
        </p>
      ) : null}

      <ToggleGroup
        type="single"
        size="sm"
        variant="outline"
        attached
        value={category}
        onValueChange={(v) => v && setCategory(v as CategoryFilter)}
        aria-label="Filter by kind"
        className="flex-wrap justify-start"
      >
        <ToggleGroupItem value="all">All {counts.all}</ToggleGroupItem>
        {(Object.keys(CATEGORY_LABEL) as WorkCategory[]).map((c) => (
          <ToggleGroupItem key={c} value={c}>
            {CATEGORY_LABEL[c]} {counts[c]}
          </ToggleGroupItem>
        ))}
      </ToggleGroup>

      {visible.length === 0 ? (
        <EmptyState
          icon={CircleCheck}
          title="Nothing needs you right now"
          description={
            inFocus.length === 0
              ? "Every figure in this view reconciles and every feed is delivering."
              : "Nothing of this kind is open. Try another filter."
          }
        />
      ) : (
        GROUPS.map((g) => {
          const items = visible.filter((i) => i.severity === g.severity);
          if (items.length === 0) return null;
          return (
            <Section key={g.severity} title={`${g.title} · ${items.length}`} description={g.description} card>
              <WorkList items={items} label={`${g.title} items`} />
            </Section>
          );
        })
      )}
    </div>
  );
}
