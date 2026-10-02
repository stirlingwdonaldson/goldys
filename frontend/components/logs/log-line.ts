import type { ConnectorStatus } from "@/lib/api";

/** One connector's latest run, formatted as a copyable log line. */
export function logLine(c: ConnectorStatus): string {
  const head = `${c.source} (${c.connectorName}) — ${c.status}`;
  if (c.failure) {
    return `${head}\n  ${c.failure.at} ${c.failure.type}: ${c.failure.message ?? ""}`;
  }
  return head;
}
