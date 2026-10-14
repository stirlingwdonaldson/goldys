"use client";

import { useState } from "react";
import { CalendarIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { parseIsoDate, toIsoDate } from "@/components/ui/date-picker";
import { cn } from "@/lib/utils";

/** An inclusive trading-date range as "yyyy-mm-dd" strings. */
export interface DateRange {
  from: string;
  to: string;
}

type Preset = "today" | "week" | "month" | "year";

const PRESETS: { id: Preset; label: string; daysBack: number }[] = [
  { id: "today", label: "Today", daysBack: 0 },
  { id: "week", label: "Past week", daysBack: 6 },
  { id: "month", label: "Past month", daysBack: 29 },
  { id: "year", label: "Past year", daysBack: 364 },
];

/** An inclusive range ending today for the given preset, in local time (no UTC shift). */
export function presetRange(preset: Preset, now = new Date()): DateRange {
  const from = new Date(now);
  from.setDate(from.getDate() - (PRESETS.find((p) => p.id === preset)?.daysBack ?? 0));
  return { from: toIsoDate(from), to: toIsoDate(now) };
}

/** Trailing 30-day window ("Past month"), so a screen renders without touching a control. */
export function defaultRange(now = new Date()): DateRange {
  return presetRange("month", now);
}

/** The preset whose range equals the value today, or "custom". */
function matchPreset(value: DateRange): Preset | "custom" {
  for (const p of PRESETS) {
    const r = presetRange(p.id);
    if (r.from === value.from && r.to === value.to) return p.id;
  }
  return "custom";
}

function formatRangeLabel(value: DateRange): string {
  const from = parseIsoDate(value.from);
  const to = parseIsoDate(value.to);
  if (!from || !to) return "Custom range";
  const fmt = (d: Date) => d.toLocaleDateString("en-AU", { day: "numeric", month: "short" });
  if (from.getTime() === to.getTime()) return fmt(from);
  return `${fmt(from)} – ${fmt(to)}`;
}

interface TimeRangeSelectorProps {
  value: DateRange;
  onChange: (range: DateRange) => void;
  className?: string;
}

/**
 * The shared time-range control: quick presets (Today / Past week / month / year) plus a
 * custom calendar range. Values stay "yyyy-mm-dd" strings, the shape the API and the old
 * `<input type="date">` used, so callers swap it in without changing their state.
 */
export function TimeRangeSelector({ value, onChange, className }: TimeRangeSelectorProps) {
  const [open, setOpen] = useState(false);
  const active = matchPreset(value);

  return (
    <div className={cn("flex flex-wrap items-center gap-1.5", className)}>
      {PRESETS.map((p) => (
        <Button
          key={p.id}
          type="button"
          size="sm"
          variant={active === p.id ? "default" : "outline"}
          onClick={() => onChange(presetRange(p.id))}
        >
          {p.label}
        </Button>
      ))}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            size="sm"
            variant={active === "custom" ? "default" : "outline"}
            aria-label="Custom date range"
            className="gap-1.5"
          >
            <CalendarIcon className="size-4" aria-hidden="true" />
            {formatRangeLabel(value)}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            mode="range"
            selected={{ from: parseIsoDate(value.from), to: parseIsoDate(value.to) }}
            defaultMonth={parseIsoDate(value.from)}
            weekStartsOn={1}
            onSelect={(range) => {
              if (range?.from && range.to) {
                onChange({ from: toIsoDate(range.from), to: toIsoDate(range.to) });
                setOpen(false);
              }
            }}
            autoFocus
          />
        </PopoverContent>
      </Popover>
    </div>
  );
}
