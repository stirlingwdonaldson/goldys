"use client";

import { useState } from "react";
import { CalendarIcon } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

/**
 * "2026-10-09" as a local-midnight Date. Parsing the string with `new Date(iso)` would read it
 * as UTC midnight, which is the previous day anywhere west of Greenwich.
 */
export function parseIsoDate(value: string): Date | undefined {
  const m = ISO_DATE.exec(value);
  return m ? new Date(Number(m[1]), Number(m[2]) - 1, Number(m[3])) : undefined;
}

/** A local Date back to "yyyy-mm-dd", without a UTC round-trip shifting the day. */
export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

interface DatePickerProps {
  /** "yyyy-mm-dd", or "" for no date. */
  value: string;
  onChange: (value: string) => void;
  id?: string;
  "aria-label"?: string;
  placeholder?: string;
  /** Earliest / latest selectable day ("yyyy-mm-dd"), e.g. to keep a range's ends in order. */
  min?: string;
  max?: string;
  /** Offer a "Clear" action, for optional dates (onChange receives ""). */
  clearable?: boolean;
  className?: string;
}

/**
 * The shadcn date picker (a Calendar in a Popover), in place of the browser's native date input.
 * Values stay "yyyy-mm-dd" strings, the shape the API and the old `<input type="date">` used, so
 * callers swap it in without changing their state.
 */
export function DatePicker({
  value,
  onChange,
  id,
  "aria-label": ariaLabel,
  placeholder = "Pick a date",
  min,
  max,
  clearable = false,
  className,
}: DatePickerProps) {
  const [open, setOpen] = useState(false);
  const selected = parseIsoDate(value);
  const minDate = min ? parseIsoDate(min) : undefined;
  const maxDate = max ? parseIsoDate(max) : undefined;
  const disabled = [
    ...(minDate ? [{ before: minDate }] : []),
    ...(maxDate ? [{ after: maxDate }] : []),
  ];

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          id={id}
          type="button"
          variant="outline"
          aria-label={ariaLabel}
          data-value={value}
          className={cn(
            "w-full justify-start gap-2 px-3 text-left font-normal",
            !selected && "text-muted-foreground",
            className,
          )}
        >
          <CalendarIcon className="text-muted-foreground" aria-hidden="true" />
          {selected
            ? selected.toLocaleDateString("en-AU", { day: "numeric", month: "short", year: "numeric" })
            : placeholder}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-0" align="start">
        <Calendar
          mode="single"
          selected={selected}
          defaultMonth={selected}
          disabled={disabled}
          weekStartsOn={1}
          onSelect={(date) => {
            if (!date) return; // re-clicking the selected day keeps it rather than clearing
            onChange(toIsoDate(date));
            setOpen(false);
          }}
          autoFocus
        />
        {clearable && value ? (
          <div className="border-t p-2">
            <Button
              type="button"
              variant="ghost"
              size="sm"
              className="w-full"
              onClick={() => {
                onChange("");
                setOpen(false);
              }}
            >
              Clear
            </Button>
          </div>
        ) : null}
      </PopoverContent>
    </Popover>
  );
}
