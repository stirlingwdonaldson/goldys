import type { ReactNode } from "react";

/** The shared card chrome for every widget: title + optional description + body. */
export function WidgetShell({
  title,
  description,
  children,
}: {
  title: string;
  description?: string | null;
  children: ReactNode;
}) {
  return (
    <div className="rounded-lg border bg-card p-4">
      <p className="text-sm font-medium">{title}</p>
      {description ? <p className="mt-1 text-xs text-muted-foreground">{description}</p> : null}
      <div className="mt-3">{children}</div>
    </div>
  );
}
