"use client";

import { useId, useState, type ReactNode } from "react";
import { ChevronRight, RotateCcw, Search, SlidersHorizontal, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
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
      <Button
        type="button"
        variant="outline"
        size="icon"
        onClick={() => onOpenChange(true)}
        aria-label="Graph settings"
        className="absolute right-3 top-3 z-10 size-8 bg-popover/95 shadow-md backdrop-blur"
      >
        <SlidersHorizontal />
      </Button>
    );
  }
  return (
    <aside
      aria-label="Graph settings"
      className="absolute right-3 top-3 z-10 flex max-h-[calc(100%-1.5rem)] w-64 flex-col overflow-hidden rounded-lg border bg-popover/95 text-popover-foreground shadow-lg backdrop-blur"
    >
      <div className="flex items-center gap-1.5 border-b bg-muted/40 px-3 py-2">
        <SlidersHorizontal className="text-muted-foreground" aria-hidden="true" />
        <span className="flex-1 text-sm font-medium">Graph settings</span>
        <IconButton label="Reset settings" onClick={onReset}>
          <RotateCcw />
        </IconButton>
        <IconButton label="Close settings" onClick={() => onOpenChange(false)}>
          <X />
        </IconButton>
      </div>
      <div className="flex flex-col overflow-y-auto">{children}</div>
    </aside>
  );
}

function IconButton({ label, onClick, children }: { label: string; onClick: () => void; children: ReactNode }) {
  return (
    <Button
      type="button"
      variant="ghost"
      size="icon"
      aria-label={label}
      title={label}
      onClick={onClick}
      className="size-7 text-muted-foreground"
    >
      {children}
    </Button>
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
      <CollapsibleTrigger asChild>
        <Button
          variant="ghost"
          className="group h-9 w-full justify-start gap-2 rounded-none px-3 text-ui font-medium"
        >
          <ChevronRight
            className="text-muted-foreground transition-transform group-data-[state=open]:rotate-90"
            aria-hidden="true"
          />
          {title}
        </Button>
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
      <Search
        className="pointer-events-none absolute left-2.5 top-1/2 size-3.5 -translate-y-1/2 text-muted-foreground"
        aria-hidden="true"
      />
      <Input
        aria-label="Find a node"
        placeholder="Search…"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="h-8 pl-8 text-ui"
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
      <Label htmlFor={id} className={cn("flex-1 text-ui font-normal", disabled && "text-muted-foreground")}>
        {label}
      </Label>
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
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center justify-between gap-2">
        <Label htmlFor={id} className="text-ui font-normal">
          {label}
        </Label>
        <span className="rounded-md bg-muted px-1.5 py-0.5 text-2xs font-medium tabular-nums text-muted-foreground">
          {format(value)}
        </span>
      </div>
      <input
        id={id}
        type="range"
        min={min}
        max={max}
        step={step}
        value={value}
        onChange={(event) => onChange(Number(event.target.value))}
        className="h-1.5 w-full cursor-pointer accent-primary"
      />
    </div>
  );
}
