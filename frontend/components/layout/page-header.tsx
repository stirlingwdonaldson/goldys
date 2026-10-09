import type { ReactNode } from "react";

interface PageHeaderProps {
  title: string;
  description?: ReactNode;
  /** Right-aligned page actions. At most one should use the `brand` button variant. */
  actions?: ReactNode;
  /** Inline status next to the title, e.g. a "3 open" pill. */
  status?: ReactNode;
}

/** Every page opens the same way: title, one-line purpose, actions on the right (heuristic 4). */
export function PageHeader({ title, description, actions, status }: PageHeaderProps) {
  return (
    <div className="flex flex-wrap items-end gap-4">
      <div className="min-w-0">
        <div className="flex items-center gap-2.5">
          <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
          {status}
        </div>
        {description ? <p className="mt-1 text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {actions ? <div className="ml-auto flex flex-wrap items-center gap-2">{actions}</div> : null}
    </div>
  );
}
