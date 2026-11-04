"use client";

import { disclosureClass } from "./report-sections";
import { useState } from "react";
import { useApi } from "@/lib/demo-mode";
import type { WeeklyReportWeek } from "@/lib/api/types";
import { weeklyTargetIds } from "@/lib/weekly-report-targets";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";

export function TargetsEditor({ week, onSaved }: { week: WeeklyReportWeek; onSaved: () => Promise<unknown> }) {
  const api = useApi();
  const [edits, setEdits] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const fields = week.rows.filter(r => weeklyTargetIds.includes(r.id));

  async function save(event: React.FormEvent) {
    event.preventDefault();
    setMessage("");
    const values: Record<string, number> = {};
    for (const field of fields) {
      if (!(field.id in edits)) continue;
      const raw = edits[field.id];
      if (!raw.trim() || !Number.isFinite(Number(raw))) { setMessage(`Enter a number for ${field.label}.`); return; }
      values[field.id] = Number((Number(raw) / (field.unit === "PERCENT" ? 100 : 1)).toFixed(4));
    }
    if (!Object.keys(values).length) { setMessage("Change a target before saving."); return; }
    setSaving(true);
    try {
      await api.saveWeeklyTargets({ effectiveFrom: week.start, values });
      await onSaved();
      setEdits({});
      setMessage("Targets saved.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Couldn't save targets."); }
    finally { setSaving(false); }
  }

  return <details className="rounded-lg border p-4">
    <summary className={disclosureClass}>Edit weekly targets</summary>
    <p className="my-3 text-sm text-muted-foreground">Changes apply from {week.start} until the next dated target for that field. Percentages use percent units: enter 0.25 for 0.25%. Imported targets carry forward as a workbook baseline.</p>
    <form onSubmit={save} className="space-y-4">
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{fields.map(field => {
        const target = week.targets.find(t => t.id === field.id);
        const initial = target ? String(Number((target.value * (field.unit === "PERCENT" ? 100 : 1)).toFixed(4))) : "";
        return <div className="space-y-1" key={field.id}>
          <Label htmlFor={`target-${field.id}`}>{field.label} ({field.unit === "AUD" ? "$" : "%"})</Label>
          <Input id={`target-${field.id}`} type="number" step="0.01" value={edits[field.id] ?? initial} onChange={e => setEdits(old => ({ ...old, [field.id]: e.target.value }))} disabled={saving} />
          <p className="text-xs text-muted-foreground">{target ? `${target.source} · effective ${target.effectiveFrom}` : "No target configured"}</p>
        </div>;
      })}</div>
      <Button type="submit" disabled={saving}>{saving ? "Saving…" : "Save targets"}</Button>
      {message ? <p role="status" className="text-sm">{message}</p> : null}
    </form>
  </details>;
}
