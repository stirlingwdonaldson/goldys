"use client";

import { useState } from "react";
import { ChevronDown, ShieldCheck } from "lucide-react";
import Link from "next/link";
import { useApiData } from "@/lib/use-api-data";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Textarea } from "@/components/ui/textarea";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { DisagreementExplanation } from "@/components/reconciliation/disagreement-explanation";
import { SourceLabel, sourceStyle } from "@/components/sources/source-tile";
import { needsDecision } from "@/lib/reconciliation-logic";
import { matchingRule } from "@/lib/rule-logic";
import { cn } from "@/lib/utils";
import type { Api, ReconciliationField, ReconciliationRecord, ResolutionRule } from "@/lib/api";

interface DrillInProps {
  fetchRecord: (api: Api) => Promise<ReconciliationRecord>;
  deps: unknown[];
  onSave: (field: string, source: string, reason: string, label: string) => Promise<void>;
  saving: boolean;
}

/**
 * The body of the reconciliation side sheet. Exception-first: fields that need a
 * decision are shown in full; fields where every source agrees are collapsed out
 * of the way but one click from view (docs/design-system.md §6).
 */
export function ReconciliationDrillIn({ fetchRecord, deps, onSave, saving }: DrillInProps) {
  const { data: record, loading, error, reload } = useApiData(fetchRecord, deps);
  const { data: rules } = useApiData((api) => api.listResolutionRules(), []);

  if (loading) {
    return (
      <div className="p-6">
        <SheetTitle className="sr-only">Loading record</SheetTitle>
        <LoadingState rows={3} />
      </div>
    );
  }
  if (error) {
    return (
      <div className="p-6">
        <SheetTitle className="sr-only">Couldn&apos;t load this record</SheetTitle>
        <ErrorState
          title="Couldn't load this record"
          message={error.message}
          correlationId={error.correlationId}
          onRetry={reload}
        />
      </div>
    );
  }
  if (!record) return null;

  const entityType = record.entityType === "product" ? "product_sales" : "daily_sales";
  const attention = record.fields.filter((f) => needsDecision(f) || f.overridden);
  const agreeing = record.fields.filter((f) => !needsDecision(f) && !f.overridden);
  const open = record.fields.filter(needsDecision).length;

  return (
    <>
      <SheetHeader className="space-y-1.5 border-b px-6 pb-4 pt-6 text-left">
        <div className="flex items-center gap-2">
          {open > 0 ? (
            <Badge variant="conflict">
              {open} {open === 1 ? "decision" : "decisions"} needed
            </Badge>
          ) : (
            <Badge variant="success">Resolved</Badge>
          )}
        </div>
        <SheetTitle className="text-lg tracking-tight">{record.entity}</SheetTitle>
        <SheetDescription>
          Choose which source is right. Your choice and reason are saved to the change history.
        </SheetDescription>
      </SheetHeader>

      <div className="flex flex-1 flex-col gap-6 overflow-y-auto px-6 py-5">
        {attention.map((field) => (
          <FieldDecision
            key={field.name}
            field={field}
            rule={matchingRule(rules ?? [], entityType, field.name)}
            saving={saving}
            onSave={onSave}
          />
        ))}

        {agreeing.length > 0 ? (
          <Collapsible className="group/agree border-t border-dashed pt-4">
            <CollapsibleTrigger className="flex w-full items-center justify-between text-sm text-muted-foreground hover:text-foreground">
              {agreeing.length} {agreeing.length === 1 ? "field agrees" : "fields agree"} across sources
              <span className="flex items-center gap-1 font-medium">
                View
                <ChevronDown className="size-4 transition-transform group-data-[state=open]/agree:rotate-180" />
              </span>
            </CollapsibleTrigger>
            <CollapsibleContent>
              <dl className="mt-3 divide-y rounded-lg border">
                {agreeing.map((f) => (
                  <div key={f.name} className="flex items-center justify-between gap-3 px-3 py-2 text-sm">
                    <dt className="text-muted-foreground">{f.label}</dt>
                    <dd className="font-medium tabular-nums">{f.sources[0]?.value ?? "—"}</dd>
                  </div>
                ))}
              </dl>
            </CollapsibleContent>
          </Collapsible>
        ) : null}
      </div>
    </>
  );
}

function FieldDecision({
  field,
  rule,
  saving,
  onSave,
}: {
  field: ReconciliationField;
  rule: ResolutionRule | null;
  saving: boolean;
  onSave: (field: string, source: string, reason: string, label: string) => Promise<void>;
}) {
  const [source, setSource] = useState<string>("");
  const [reason, setReason] = useState("");
  const [attempted, setAttempted] = useState(false);
  const decide = needsDecision(field);
  const choices = field.sources.filter((s) => s.value != null);
  const reasonMissing = reason.trim() === "";
  const idBase = `decide-${field.name}`;

  function submit() {
    setAttempted(true);
    // A reason is required: overrides change reported figures and must be explainable later (heuristic 5).
    if (!source || reasonMissing) return;
    void onSave(field.name, source, reason.trim(), field.label);
  }

  return (
    <section className="flex flex-col gap-3" aria-labelledby={`${idBase}-label`}>
      <div className="flex items-center justify-between gap-2">
        <h3 id={`${idBase}-label`} className="text-sm font-semibold">
          {field.label}
        </h3>
        {field.overridden ? (
          <Badge variant="success">Resolved · {field.authoritativeSource}</Badge>
        ) : null}
      </div>

      <DisagreementExplanation field={field} rule={rule} />

      {decide ? (
        <>
          {/* The explanation above already names any standing rule (heuristic 6); offer the
              shortcut to make this decision automatic next time. */}
          {!rule ? (
            <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
              <ShieldCheck className="size-3.5" aria-hidden="true" />
              <Link href="/resolution-rules" className="font-medium text-foreground underline underline-offset-2">
                Create a rule
              </Link>
              so this is decided automatically next time.
            </p>
          ) : null}

          <RadioGroup value={source} onValueChange={setSource} aria-label={`Authoritative source for ${field.label}`}>
            {field.sources.map((s) => {
              const id = `${idBase}-${s.source}`;
              const disabled = s.value == null;
              return (
                <Label
                  key={s.source}
                  htmlFor={id}
                  className={cn(
                    "flex cursor-pointer items-center gap-3 rounded-xl border p-3 font-normal transition-colors",
                    source === s.source && "border-primary ring-1 ring-primary",
                    disabled && "cursor-not-allowed opacity-70",
                  )}
                >
                  <RadioGroupItem
                    id={id}
                    value={s.source}
                    disabled={disabled}
                    aria-label={`${sourceStyle(s.source).label}: ${s.value ?? "no data"}`}
                  />
                  <SourceLabel source={s.source} className="text-sm">
                    <span className="font-medium">{sourceStyle(s.source).label}</span>
                  </SourceLabel>
                  <span
                    className={cn(
                      "ml-auto text-base font-semibold tabular-nums",
                      disabled && "text-sm font-normal italic text-muted-foreground",
                    )}
                  >
                    {s.value ?? `No data from ${s.source}`}
                  </span>
                </Label>
              );
            })}
          </RadioGroup>

          <div className="flex flex-col gap-1.5">
            <div className="flex items-baseline justify-between">
              <Label htmlFor={`${idBase}-reason`}>Reason</Label>
              <span className="text-xs text-muted-foreground">Required · saved to the change history</span>
            </div>
            <Textarea
              id={`${idBase}-reason`}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              placeholder="e.g. Refund voided at close; accounting journal not updated"
              aria-invalid={attempted && reasonMissing}
              className={cn(attempted && reasonMissing && "border-destructive focus-visible:ring-destructive")}
              rows={3}
            />
            {attempted && reasonMissing ? (
              <p className="text-xs text-destructive">Add a reason so others can see why this source was chosen.</p>
            ) : null}
          </div>

          <SheetFooter className="mt-1 sm:justify-end">
            <Button onClick={submit} disabled={!source || saving || choices.length === 0}>
              {saving
                ? "Saving…"
                : source
                  ? `Use ${sourceStyle(source).label} value`
                  : "Choose a source"}
            </Button>
          </SheetFooter>
        </>
      ) : null}
    </section>
  );
}
