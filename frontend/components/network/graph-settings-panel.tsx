"use client";

import { useId, useState, type ReactNode } from "react";
import { ChevronRight, RotateCcw, Search, SlidersHorizontal, X } from "lucide-react";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Switch } from "@/components/ui/switch";
import { cn } from "@/lib/utils";

/**
 * The floating settings card in the graph's top-right corner, after Obsidian's: collapsible
 * sections of switches and sliders over the canvas. Collapses to a single icon button.
 */
export function GraphSettingsPanel({
  open,
  onOpenChange,
  onReset,
  children,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onReset: () => void;
  children: ReactNode;
}) {
  if (!open) {
    return (
      <button
        type="button"
        onClick={() => onOpenChange(true)}
        aria-label="Graph settings"
        className="absolute right-3 top-3 z-10 flex size-8 items-center justify-center rounded-md border bg-background/90 text-muted-foreground shadow-sm backdrop-blur hover:text-foreground"
      >
        <SlidersHorizontal className="size-4" />
      </button>
    );
  }
  return (
    <aside
      aria-label="Graph settings"
      className="absolute right-3 top-3 z-10 flex max-h-[calc(100%-1.5rem)] w-60 flex-col overflow-hidden rounded-lg border bg-background/95 text-sm shadow-sm backdrop-blur"
    >
      <div className="flex items-center gap-1 border-b px-3 py-2">
        <span className="flex-1 text-xs font-medium text-muted-foreground">Graph settings</span>
        <IconButton label="Reset settings" onClick={onReset}>
          <RotateCcw className="size-3.5" />
        </IconButton>
        <IconButton label="Close settings" onClick={() => onOpenChange(false)}>
          <X className="size-3.5" />
        </IconButton>
      </div>
      <div className="flex flex-col overflow-y-auto">{children}</div>
    </aside>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      className="flex size-6 items-center justify-center rounded text-muted-foreground hover:bg-muted hover:text-foreground"
    >
      {children}
    </button>
  );
}

/** A collapsible section ("Filters", "Groups", …). */
export function SettingsSection({
  title,
  defaultOpen = false,
  children,
}: {
  title: string;
  defaultOpen?: boolean;
  children: ReactNode;
}) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <Collapsible open={open} onOpenChange={setOpen} className="border-b last:border-b-0">
      <CollapsibleTrigger className="flex w-full items-center gap-1.5 px-3 py-2 text-left text-[13px] font-medium hover:bg-muted/50">
        <ChevronRight
          className={cn("size-3.5 text-muted-foreground transition-transform", open && "rotate-90")}
          aria-hidden="true"
        />
        {title}
      </CollapsibleTrigger>
      <CollapsibleContent className="flex flex-col gap-2.5 px-3 pb-3 pt-0.5">{children}</CollapsibleContent>
    </Collapsible>
  );
}

export function SettingsSearch({
  value,
  onChange,
  onSubmit,
}: {
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
}) {
  return (
    <form
      role="search"
      className="relative"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit();
      }}
    >
      <Search className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground" aria-hidden="true" />
      <input
        aria-label="Find a node"
        placeholder="Search…"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-8 w-full rounded-md border bg-background pl-8 pr-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      />
    </form>
  );
}

export function SettingsSwitch({
  label,
  checked,
  onCheckedChange,
  swatch,
  disabled,
}: {
  label: string;
  checked: boolean;
  onCheckedChange: (checked: boolean) => void;
  swatch?: string;
  disabled?: boolean;
}) {
  const id = useId();
  return (
    <div className="flex items-center gap-2">
      {swatch && <span aria-hidden="true" className="size-2 shrink-0 rounded-full" style={{ background: swatch }} />}
      <label htmlFor={id} className={cn("flex-1 text-[13px]", disabled && "text-muted-foreground")}>
        {label}
      </label>
      <Switch id={id} checked={checked} onCheckedChange={onCheckedChange} disabled={disabled} />
    </div>
  );
}

export function SettingsSlider({
  label,
  value,
  min,
  max,
  step,
  onChange,
  format = (v) => v.toFixed(2),
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  onChange: (value: number) => void;
  format?: (value: number) => string;
}) {
  const id = useId();
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-[13px]">
        {label}
      </label>
      <div className="flex items-center gap-2">
        <span className="w-9 shrink-0 text-xs tabular-nums text-muted-foreground">{format(value)}</span>
        <input
          id={id}
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={(event) => onChange(Number(event.target.value))}
          className="h-1.5 flex-1 cursor-pointer accent-foreground"
        />
      </div>
    </div>
  );
}
