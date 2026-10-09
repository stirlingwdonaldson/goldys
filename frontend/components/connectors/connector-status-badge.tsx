import { Badge, type BadgeProps } from "@/components/ui/badge";
import type { ConnectorRunStatus } from "@/lib/api";

const STATUS_LABEL: Record<ConnectorRunStatus, string> = {
  success: "Success",
  partial: "Partial",
  failed: "Failed",
  no_new_data: "No new data",
  running: "Running",
  never_run: "Never run",
};

// "No new data" stays neutral and "Failed" stays red: an expected quiet period
// must never look like a system fault (docs/design-system.md).
const STATUS_VARIANT: Record<ConnectorRunStatus, BadgeProps["variant"]> = {
  success: "success",
  partial: "conflict",
  failed: "failed",
  no_new_data: "neutral",
  running: "info",
  never_run: "neutral",
};

/** A status badge for a connector run, shared by the dashboard and connectors screens. */
export function ConnectorStatusBadge({ status }: { status: ConnectorRunStatus }) {
  return <Badge variant={STATUS_VARIANT[status]}>{STATUS_LABEL[status]}</Badge>;
}
