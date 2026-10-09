import { formatCurrency } from "@/lib/format";
import type { WidgetFormat } from "./types";

/** Format a numeric value with a widget format hint, degrading safely on unknown formats. */
export function formatValue(value: number | null, format?: string | null): string {
  if (value == null || !Number.isFinite(value)) return "—";
  switch (format as WidgetFormat | undefined) {
    case "currency":
      return formatCurrency(value);
    case "percent":
      return `${value.toLocaleString("en-AU", { maximumFractionDigits: 1 })}%`;
    case "number":
      return value.toLocaleString("en-AU", { maximumFractionDigits: 2 });
    case "text":
    default:
      return String(value);
  }
}
