import { cn } from "@/lib/utils"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"

const statusDot: Record<"success" | "warning" | "missing", string> = {
  success: "bg-status-success",
  warning: "bg-status-warning",
  missing: "bg-status-missing",
}

export function StatCard({
  label,
  value,
  delta,
  status,
}: {
  label: string
  value: string
  delta: string
  status: "success" | "warning" | "missing"
}) {
  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-sm font-medium text-muted-foreground">
          {label}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="text-2xl font-semibold tracking-tight">{value}</div>
        <p className="mt-1 flex items-center gap-1.5 text-xs text-muted-foreground">
          <span
            className={cn("h-1.5 w-1.5 rounded-full", statusDot[status])}
            aria-hidden
          />
          {delta}
        </p>
      </CardContent>
    </Card>
  )
}
