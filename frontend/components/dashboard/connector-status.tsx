import { AlertCircle, CheckCircle2, Clock } from "lucide-react"

import { cn } from "@/lib/utils"
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card"
import { Badge } from "@/components/ui/badge"
import { connectorStatus } from "@/lib/mock-dashboard-data"

// "Lagging" (no new data since last check) is a neutral/expected state, not
// a fault — only "failed" gets destructive treatment. See
// docs/design-system.md §6, "Connector failure vs. no new data."
const stateMeta = {
  healthy: {
    label: "Healthy",
    icon: CheckCircle2,
    badgeClass: "bg-status-success text-status-success-foreground border-transparent",
  },
  lagging: {
    label: "Lagging",
    icon: Clock,
    badgeClass: "bg-status-missing text-status-missing-foreground border-transparent",
  },
  failed: {
    label: "Failed",
    icon: AlertCircle,
    badgeClass: "bg-destructive text-destructive-foreground border-transparent",
  },
} satisfies Record<
  string,
  { label: string; icon: typeof CheckCircle2; badgeClass: string }
>

export function ConnectorStatus() {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Connector status</CardTitle>
        <CardDescription>Ingestion health by source system</CardDescription>
      </CardHeader>
      <CardContent className="space-y-3">
        {connectorStatus.map((connector) => {
          const meta = stateMeta[connector.state]
          const Icon = meta.icon
          return (
            <div
              key={connector.name}
              className="flex items-center justify-between rounded-lg border p-3"
            >
              <div className="flex items-center gap-3">
                <Icon
                  className={cn(
                    "h-4 w-4",
                    connector.state === "failed"
                      ? "text-destructive"
                      : connector.state === "lagging"
                        ? "text-status-missing"
                        : "text-status-success"
                  )}
                />
                <div>
                  <p className="text-sm font-medium">{connector.name}</p>
                  <p className="text-xs text-muted-foreground">
                    {connector.role}
                  </p>
                </div>
              </div>
              <div className="flex flex-col items-end gap-1">
                <Badge className={meta.badgeClass}>{meta.label}</Badge>
                <span className="text-xs text-muted-foreground">
                  {connector.lastSync}
                </span>
              </div>
            </div>
          )
        })}
      </CardContent>
    </Card>
  )
}
