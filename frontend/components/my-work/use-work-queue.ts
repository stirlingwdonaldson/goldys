"use client";

import { useCallback, useEffect, useState } from "react";
import type { Api } from "@/lib/api";
import { useApiData } from "@/lib/use-api-data";
import { useCurrentUser } from "@/components/app-shell/current-user-provider";
import { buildWorkQueue, defaultFocus, type Focus, type WorkItem } from "@/lib/work-queue";

export interface WorkQueueResult {
  items: WorkItem[];
  /** Signals that couldn't be read (not permitted, or the request failed), in plain words. */
  unavailable: string[];
}

/**
 * Reads every signal the queue is built from. Each one fails independently: a manager
 * who can't read invoice flags still gets their reconciliation decisions, and the page
 * says which part is missing instead of failing outright.
 */
export async function loadWorkQueue(api: Api): Promise<WorkQueueResult> {
  const [daily, product, connectors, flags, recompute] = await Promise.allSettled([
    api.listReconciliationExceptions(),
    api.listProductExceptions(),
    api.listConnectorStatuses(),
    api.listInvoiceFlags(),
    api.getRecomputeStatus(),
  ]);
  const value = <T,>(r: PromiseSettledResult<T>): T | null => (r.status === "fulfilled" ? r.value : null);
  const unavailable = [
    daily.status === "rejected" ? "daily-sales decisions" : null,
    product.status === "rejected" ? "product-sales decisions" : null,
    connectors.status === "rejected" ? "data feed status" : null,
    flags.status === "rejected" ? "invoice checks" : null,
    recompute.status === "rejected" ? "rule-change status" : null,
  ].filter((s): s is string => s !== null);
  return {
    items: buildWorkQueue({
      dailyExceptions: value(daily),
      productExceptions: value(product),
      connectors: value(connectors),
      invoiceFlags: value(flags),
      recompute: value(recompute),
    }),
    unavailable,
  };
}

export function useWorkQueue() {
  return useApiData(loadWorkQueue);
}

const FOCUS_KEY = "goldys.focus";
const FOCUS_VALUES: Focus[] = ["business", "venue", "kitchen", "foh"];

/**
 * The person's chosen perspective, remembered per browser. Starts from their profile
 * (see `defaultFocus`) until they pick one. Storage can be unavailable (private mode,
 * blocked site data), so every access is guarded and the default still works.
 */
export function useFocus(): [Focus, (f: Focus) => void] {
  const { user } = useCurrentUser();
  const [stored, setStored] = useState<Focus | null>(null);

  useEffect(() => {
    try {
      const v = window.localStorage.getItem(FOCUS_KEY);
      if (v && (FOCUS_VALUES as string[]).includes(v)) setStored(v as Focus);
    } catch {
      // Storage blocked: fall back to the profile default.
    }
  }, []);

  const setFocus = useCallback((f: Focus) => {
    setStored(f);
    try {
      window.localStorage.setItem(FOCUS_KEY, f);
    } catch {
      // Not persisted; the choice still applies for this visit.
    }
  }, []);

  return [stored ?? defaultFocus(user), setFocus];
}
