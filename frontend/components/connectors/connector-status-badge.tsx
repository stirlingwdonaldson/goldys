import { Badge } from "@/components/ui/badge";
import type { ConnectorRunStatus } from "@/lib/api";

const STATUS_LABEL: Record<ConnectorRunStatus, string> = {
  success: "Success",
  partial: "Partial",
  failed: "Failed",
  no_new_data: "No new data",
  never_run: "Never run",
};

const STATUS_CLASS: Record<ConnectorRunStatus, string> = {
  success: "border-transparent bg-status-success text-status-success-foreground",
  partial: "border-transparent bg-status-warning text-status-warning-foreground",
  failed: "border-transparent bg-destructive text-destructive-foreground",
  no_new_data: "border-transparent bg-muted text-muted-foreground",
  never_run: "border-transparent bg-muted text-muted-foreground",
};

/** A status badge for a connector run, shared by the dashboard and connectors screens. */
export function ConnectorStatusBadge({ status }: { status: ConnectorRunStatus }) {
  return <Badge className={STATUS_CLASS[status]}>{STATUS_LABEL[status]}</Badge>;
}
