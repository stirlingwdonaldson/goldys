import type { ReactNode } from "react";
import { cn } from "@/lib/utils";

interface SectionProps {
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  /** Wrap the section in a bordered card (charts, graphs) rather than leaving it open (tables). */
  card?: boolean;
  className?: string;
  children: ReactNode;
}

/** A titled block within a page. One heading style for every sub-section (heuristic 4). */
export function Section({ title, description, actions, card = false, className, children }: SectionProps) {
  return (
    <section className={cn("flex min-w-0 flex-col gap-3", card && "rounded-xl border bg-card p-5", className)}>
      <div className="flex items-start gap-3">
        <div className="min-w-0">
          <h2 className="text-sm font-semibold">{title}</h2>
          {description ? <p className="mt-0.5 text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {actions ? <div className="ml-auto flex shrink-0 items-center gap-2">{actions}</div> : null}
      </div>
      {children}
    </section>
  );
}
