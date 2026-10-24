"use client";

import { useCallback, useRef } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { listPage, listUrl } from "@/lib/list-url";

/** URL values select evidence only for this report's fixed, permitted metric. */
export function reportTarget(params: URLSearchParams, metricId: string, metricLabel: string) {
  const date = params.get("trace");
  if (params.get("metric") !== metricId || !date || !/^\d{4}-\d{2}-\d{2}$/.test(date)) return null;
  const instant = new Date(`${date}T00:00:00Z`);
  if (!Number.isFinite(instant.getTime()) || instant.toISOString().slice(0, 10) !== date) return null;
  return { metricId, metricLabel, date };
}

/** Preserve parent scope while evidence is opened, shared, reloaded or replayed. */
export function useReportUrl(metricId: string, metricLabel: string) {
  const search = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const params = new URLSearchParams(search.toString());
  const pushed = useRef(false);
  const trigger = useRef<string | null>(null);
  const container = useRef<HTMLDivElement>(null);
  const update = useCallback((patch: Record<string, string | null>, mode: "push" | "replace" = "replace") => {
    const qs = listUrl(new URLSearchParams(search.toString()), patch);
    router[mode](qs ? `${pathname}?${qs}` : pathname, { scroll: false });
  }, [search, pathname, router]);
  const openTrace = useCallback((date: string) => {
    trigger.current = document.activeElement?.getAttribute("aria-label") ?? null;
    pushed.current = true;
    update({ metric: metricId, trace: date }, "push");
  }, [metricId, update]);
  function closeTrace() {
    if (pushed.current) { pushed.current = false; router.back(); }
    else update({ metric: null, trace: null });
  }
  function restoreFocus() {
    const button = Array.from(container.current?.querySelectorAll<HTMLButtonElement>("button[aria-label]") ?? [])
      .find(element => element.getAttribute("aria-label") === trigger.current);
    (button ?? container.current)?.focus({ preventScroll: true });
  }
  return { params, update, ...listPage(params), container, openTrace, closeTrace, restoreFocus,
    target: reportTarget(params, metricId, metricLabel) };
}
