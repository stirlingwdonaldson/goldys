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
import type { CustomLogic, ResolutionRule, RuleStrategy, SaveResolutionRuleInput } from "@/lib/api";

const CUSTOM_OPTIONS: { value: CustomLogic; label: string }[] = [
  { value: "flag", label: "Flag unresolved" },
  { value: "highest", label: "Pick highest" },
  { value: "lowest", label: "Pick lowest" },
  { value: "newest", label: "Pick newest" },
];

interface RuleEditorProps {
  open: boolean;
  initial: ResolutionRule | null; // null => creating
  entities: string[];
  fieldsByEntity: Record<string, string[]>;
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
  onSave,
  onCancel,
}: RuleEditorProps) {
  const [entity, setEntity] = useState(initial?.entity ?? entities[0] ?? "");
  const [field, setField] = useState(initial?.field ?? fieldsByEntity[entities[0]]?.[0] ?? "");
  const [strategy, setStrategy] = useState<RuleStrategy>(initial?.strategy ?? "priority");
  const [customLogic, setCustomLogic] = useState<CustomLogic>(initial?.customLogic ?? "flag");
  const [recomputeHistory, setRecomputeHistory] = useState(false);

  useEffect(() => {
    if (open) {
      setEntity(initial?.entity ?? entities[0] ?? "");
      setField(initial?.field ?? fieldsByEntity[entities[0]]?.[0] ?? "");
      setStrategy(initial?.strategy ?? "priority");
      setCustomLogic(initial?.customLogic ?? "flag");
      setRecomputeHistory(false);
    }
  }, [open, initial, entities, fieldsByEntity]);

  const fields = fieldsByEntity[entity] ?? [];

  function submit() {
    onSave({
      id: initial?.id,
      entity,
      field,
      strategy,
      sourcePriority: strategy === "priority" ? ["Cooking the Books", "Lightspeed"] : undefined,
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
            <Select value={entity} onValueChange={setEntity}>
              <SelectTrigger id="rule-entity">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {entities.map((e) => (
                  <SelectItem key={e} value={e}>
                    {e}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-2">
            <Label htmlFor="rule-field">Field</Label>
            <Select value={field} onValueChange={setField}>
              <SelectTrigger id="rule-field">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {fields.map((f) => (
                  <SelectItem key={f} value={f}>
                    {f}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
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
