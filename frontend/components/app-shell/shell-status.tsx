"use client";

import { createContext, useContext, useEffect, type ReactNode } from "react";
import type { ConnectorStatus } from "@/lib/api";
import { useApiData } from "@/lib/use-api-data";

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
  const summary = useApiData((api) => api.getDashboardSummary());
  const connectors = useApiData((api) => api.listConnectorStatuses());
  const reloadSummary = summary.reload;
  const reloadConnectors = connectors.reload;

  useEffect(() => {
    const onFocus = () => {
      reloadSummary();
      reloadConnectors();
    };
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [reloadSummary, reloadConnectors]);

  const value: ShellStatus = {
    // Permission errors or outages leave these null; the chrome then shows nothing
    // rather than a misleading zero.
    openConflicts: summary.data?.openConflicts ?? null,
    connectors: connectors.data,
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
