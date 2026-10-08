"use client";

import { useEffect, useState } from "react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { entityLabel, fieldLabel, SOURCES, sourceLabel } from "@/lib/rule-logic";
import type { CustomLogic, ResolutionRule, RuleStrategy, SaveResolutionRuleInput } from "@/lib/api";

const CUSTOM_OPTIONS: { value: CustomLogic; label: string }[] = [
  { value: "flag", label: "Flag unresolved" },
  { value: "highest", label: "Pick highest" },
  { value: "lowest", label: "Pick lowest" },
  { value: "newest", label: "Pick newest" },
];

const DEFAULT_PRIORITY = [...SOURCES];

interface RuleEditorProps {
  open: boolean;
  initial: ResolutionRule | null; // null => creating
  entities: string[];
  fieldsByEntity: Record<string, string[]>;
  /** True when running on demo fixtures, so the product list can be flagged as sample data. */
  demo?: boolean;
  onSave: (input: SaveResolutionRuleInput) => void;
  onCancel: () => void;
}

/**
 * The rule editor. It edits a local copy (never the live rule in place), and
 * calls onSave with a SaveResolutionRuleInput on submit.
 */
export function RuleEditor({
  open,
  initial,
  entities,
  fieldsByEntity,
  demo = false,
  onSave,
  onCancel,
}: RuleEditorProps) {
  const [entityType, setEntityType] = useState(initial?.entityType ?? entities[0] ?? "");
  const [fieldKey, setFieldKey] = useState(
    initial?.fieldKey ?? fieldsByEntity[entities[0]]?.[0] ?? "",
  );
  const [strategy, setStrategy] = useState<RuleStrategy>(initial?.strategy ?? "priority");
  const [customLogic, setCustomLogic] = useState<CustomLogic>(initial?.customLogic ?? "flag");
  const [priority, setPriority] = useState<string[]>(initial?.sourcePriority ?? DEFAULT_PRIORITY);
  const [recomputeHistory, setRecomputeHistory] = useState(false);

  useEffect(() => {
    if (open) {
      setEntityType(initial?.entityType ?? entities[0] ?? "");
      setFieldKey(initial?.fieldKey ?? fieldsByEntity[entities[0]]?.[0] ?? "");
      setStrategy(initial?.strategy ?? "priority");
      setCustomLogic(initial?.customLogic ?? "flag");
      setPriority(initial?.sourcePriority ?? DEFAULT_PRIORITY);
      setRecomputeHistory(false);
    }
  }, [open, initial, entities, fieldsByEntity]);

  const fields = fieldsByEntity[entityType] ?? [];

  function move(index: number, delta: number) {
    setPriority((prev) => {
      const next = [...prev];
      const j = index + delta;
      if (j < 0 || j >= next.length) return prev;
      [next[index], next[j]] = [next[j], next[index]];
      return next;
    });
  }

  function submit() {
    onSave({
      id: initial?.id,
      entityType,
      fieldKey,
      strategy,
      sourcePriority: strategy === "priority" ? priority : undefined,
      customLogic: strategy === "custom" ? customLogic : undefined,
    });
  }

  return (
    <Dialog open={open} onOpenChange={(o) => !o && onCancel()}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle>{initial ? "Edit rule" : "New rule"}</DialogTitle>
          <DialogDescription>
            Choose a field and how disagreements on it should resolve.
          </DialogDescription>
        </DialogHeader>

        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-2">
            <Label htmlFor="rule-entity">Entity</Label>
            <Select
              value={entityType}
              onValueChange={(e) => {
                setEntityType(e);
                setFieldKey(fieldsByEntity[e]?.[0] ?? "");
              }}
            >
              <SelectTrigger id="rule-entity">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {entities.map((e) => (
                  <SelectItem key={e} value={e}>
                    {entityLabel(e)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="rule-field">Field</Label>
            <Select value={fieldKey} onValueChange={setFieldKey}>
              <SelectTrigger id="rule-field">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {fields.map((f) => (
                  <SelectItem key={f} value={f}>
                    {fieldLabel(entityType, f)}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {demo && entityType === "product_sales" ? (
              <p className="text-xs text-muted-foreground">
                Demo product list — sample data, not your live menu.
              </p>
            ) : null}
          </div>

          <div className="flex flex-col gap-2">
            <Label>Strategy</Label>
            <RadioGroup value={strategy} onValueChange={(v) => setStrategy(v as RuleStrategy)}>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="priority" id="strategy-priority" />
                <Label htmlFor="strategy-priority">Priority by source</Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="manual" id="strategy-manual" />
                <Label htmlFor="strategy-manual">Manual override</Label>
              </div>
              <div className="flex items-center gap-2">
                <RadioGroupItem value="custom" id="strategy-custom" />
                <Label htmlFor="strategy-custom">Custom logic</Label>
              </div>
            </RadioGroup>
          </div>

          {strategy === "priority" ? (
            <div className="flex flex-col gap-2">
              <Label>Source priority</Label>
              <p className="text-xs text-muted-foreground">
                First source wins when they disagree.
              </p>
              <ol className="flex flex-col gap-1">
                {priority.map((code, i) => (
                  <li key={code} className="flex items-center gap-2 rounded-md border px-3 py-2">
                    <span className="w-4 text-xs text-muted-foreground">{i + 1}</span>
                    <span className="flex-1 text-sm">{sourceLabel(code)}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={i === 0}
                      onClick={() => move(i, -1)}
                      aria-label={`Move ${sourceLabel(code)} up`}
                    >
                      ↑
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      disabled={i === priority.length - 1}
                      onClick={() => move(i, 1)}
                      aria-label={`Move ${sourceLabel(code)} down`}
                    >
                      ↓
                    </Button>
                  </li>
                ))}
              </ol>
            </div>
          ) : null}

          {strategy === "custom" ? (
            <div className="flex flex-col gap-2">
              <Label htmlFor="rule-custom">Custom logic</Label>
              <Select value={customLogic} onValueChange={(v) => setCustomLogic(v as CustomLogic)}>
                <SelectTrigger id="rule-custom">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {CUSTOM_OPTIONS.map((o) => (
                    <SelectItem key={o.value} value={o.value}>
                      {o.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}

          <div className="flex items-center gap-2">
            <Checkbox
              id="rule-recompute"
              checked={recomputeHistory}
              onCheckedChange={(c) => setRecomputeHistory(c === true)}
            />
            <Label htmlFor="rule-recompute">Recompute history with this rule</Label>
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button onClick={submit}>Save rule</Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
