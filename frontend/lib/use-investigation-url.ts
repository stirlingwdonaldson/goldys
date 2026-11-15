"use client";

import { useCallback, useRef } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { listUrl } from "./list-url";

export function validDate(value: string | null): value is string {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function investigationRange(params: URLSearchParams, fallback: { from: string; to: string }) {
  const from = params.get("from"), to = params.get("to");
  return validDate(from) && validDate(to) && from <= to ? { from, to } : fallback;
}

/** Scope updates preserve all unrelated panel/filter keys. Panel entries unwind with Back. */
export function useInvestigationUrl() {
  const search = useSearchParams();
  const pathname = usePathname();
  const router = useRouter();
  const pushed = useRef(false);
  const trigger = useRef<HTMLElement | null>(null);
  const update = useCallback((patch: Record<string, string | null>, mode: "push" | "replace" = "replace") => {
    const query = listUrl(new URLSearchParams(window.location.search), patch);
    // Native history integrates with Next search params without unmounting filter controls.
    window.history[mode === "push" ? "pushState" : "replaceState"](null, "", `${pathname}${query ? `?${query}` : ""}`);
  }, [pathname]);
  function openPanel(key: string, value: string, scope: Record<string, string> = {}) {
    trigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    pushed.current = true;
    update({ ...scope, [key]: value }, "push");
  }
  function closePanel(key: string) {
    if (pushed.current) { pushed.current = false; router.back(); }
    else update({ [key]: null });
  }
  function restoreFocus(event: Event, fallback?: HTMLElement | null) {
    event.preventDefault();
    (trigger.current?.isConnected ? trigger.current : fallback ?? document.getElementById("main-content"))?.focus({ preventScroll: true });
  }
  return { params: new URLSearchParams(search.toString()), update, openPanel, closePanel, restoreFocus };
}
