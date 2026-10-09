import type { ReactNode } from "react";

/** The shared card chrome for every widget: title + optional description + body. */
export function WidgetShell({
  title,
  description,
  children,
  actions,
}: {
  title: string;
  description?: string | null;
  children: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="min-w-0 rounded-xl border bg-card p-5">
      <div className="flex items-start gap-3">
        <div className="min-w-0">
          <p className="text-sm font-semibold">{title}</p>
          {description ? <p className="mt-0.5 text-xs text-muted-foreground">{description}</p> : null}
        </div>
        {actions ? <div className="ml-auto flex items-center gap-1.5">{actions}</div> : null}
      </div>
      <div className="mt-4">{children}</div>
    </div>
  );
}
