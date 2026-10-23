"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { ConnectorStatus } from "@/lib/api";
import { useApi } from "@/lib/demo-mode";
import { useQuery } from "@tanstack/react-query";

/**
 * App-wide status shown in the chrome on every page (heuristic 1): how many
 * figures need a decision, and whether every source is delivering. Fetched once
 * by the shell and refreshed when the window regains focus.
 */
interface ShellStatus {
  openConflicts: number | null;
  connectors: ConnectorStatus[] | null;
  refresh: () => void;
}

const ShellStatusContext = createContext<ShellStatus>({
  openConflicts: null,
  connectors: null,
  refresh: () => {},
});

export function useShellStatus(): ShellStatus {
  return useContext(ShellStatusContext);
}

export function ShellStatusProvider({ children }: { children: ReactNode }) {
  const queryApi = useApi();
  const summary = useQuery({ queryKey: ["getDashboardSummary"], queryFn: ({ signal }) => queryApi.getDashboardSummary({ signal }) });
  const connectors = useQuery({ queryKey: ["listConnectorStatuses"], queryFn: ({ signal }) => queryApi.listConnectorStatuses({ signal }) });
  const reloadSummary = summary.refetch;
  const reloadConnectors = connectors.refetch;

  const value: ShellStatus = {
    // Permission errors or outages leave these null; the chrome then shows nothing
    // rather than a misleading zero.
    openConflicts: summary.data?.openConflicts ?? null,
    connectors: connectors.data ?? null,
    refresh: () => {
      reloadSummary();
      reloadConnectors();
    },
  };

  return <ShellStatusContext.Provider value={value}>{children}</ShellStatusContext.Provider>;
}

/** A source counts as failing when its latest run failed outright. */
export function isFailing(c: ConnectorStatus): boolean {
  return c.status === "failed";
}
