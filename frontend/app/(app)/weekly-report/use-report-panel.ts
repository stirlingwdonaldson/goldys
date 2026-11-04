"use client";

import { useRef } from "react";
import { useRouter, useSearchParams } from "next/navigation";

/** Keep the selected week and report scope when a detail panel is shared or replayed. */
export function useReportPanel(name: "targets" | "details", week: string) {
  const params = useSearchParams();
  const router = useRouter();
  const pushed = useRef(false);
  const trigger = useRef<HTMLElement | null>(null);
  function open(tab?: string) {
    trigger.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const next = new URLSearchParams(params.toString());
    next.set("week", week);
    next.set("reportPanel", name);
    if (tab) next.set("reportTab", tab); else next.delete("reportTab");
    pushed.current = true;
    router.push(`/weekly-report?${next}`, { scroll: false });
  }
  function close() {
    if (pushed.current) { pushed.current = false; router.back(); return; }
    const next = new URLSearchParams(params.toString());
    next.delete("reportPanel");
    next.delete("reportTab");
    router.replace(`/weekly-report?${next}`, { scroll: false });
  }
  function select(tab: string) {
    const next = new URLSearchParams(params.toString());
    next.set("reportTab", tab);
    router.replace(`/weekly-report?${next}`, { scroll: false });
  }
  function restoreFocus(event: Event) {
    const target = trigger.current?.isConnected ? trigger.current : document.querySelector<HTMLButtonElement>(`button[data-report-panel-trigger="${name}"]`);
    if (!target) return;
    event.preventDefault();
    target.focus({ preventScroll: true });
  }
  return { isOpen: params.get("reportPanel") === name, tab: params.get("reportTab"), open, close, select, restoreFocus };
}
