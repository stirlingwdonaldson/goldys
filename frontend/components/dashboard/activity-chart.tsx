import type { ActivityPoint } from "@/lib/api";

function dayLabel(date: string): string {
  return date.slice(8, 10).replace(/^0/, "");
}

/**
 * A dependency-free stacked bar chart of connector-run activity: one column per
 * day, clean runs in green under failed runs in red, height scaled to the busiest day.
 */
export function ActivityChart({ points }: { points: ActivityPoint[] }) {
  if (points.length === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        No activity yet — connector runs will appear here once ingestion starts.
      </p>
    );
  }

  const max = Math.max(1, ...points.map((p) => p.clean + p.failed));

  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-4 text-xs text-muted-foreground">
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm bg-status-success" aria-hidden="true" />
          Clean
        </span>
        <span className="flex items-center gap-1.5">
          <span className="h-2.5 w-2.5 rounded-sm bg-destructive" aria-hidden="true" />
          Failed
        </span>
      </div>

      <div
        role="img"
        aria-label="Connector runs per day, last 14 days"
        className="flex h-32 items-end gap-1"
      >
        {points.map((p) => {
          const total = p.clean + p.failed;
          const barLabel = `${p.date}: ${p.clean} clean, ${p.failed} failed`;
          if (total === 0) {
            return (
              <div
                key={p.date}
                title={barLabel}
                className="flex h-full flex-1 flex-col justify-end"
              >
                <div className="w-full rounded-sm bg-muted/40" style={{ height: 4 }} />
              </div>
            );
          }
          return (
            <div
              key={p.date}
              title={barLabel}
              className="flex h-full flex-1 flex-col justify-end"
            >
              <div
                className="flex w-full flex-col justify-end overflow-hidden rounded-sm"
                style={{ height: `${Math.max(6, (total / max) * 100)}%` }}
              >
                <div
                  className="w-full bg-destructive"
                  style={{ height: `${(p.failed / total) * 100}%` }}
                />
                <div
                  className="w-full bg-status-success"
                  style={{ height: `${(p.clean / total) * 100}%` }}
                />
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex gap-1 text-[10px] text-muted-foreground">
        {points.map((p) => (
          <div key={p.date} className="flex-1 text-center">
            {dayLabel(p.date)}
          </div>
        ))}
      </div>
    </div>
  );
}
