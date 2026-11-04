"use client";

import { useEffect } from "react";
import { ArrowLeft, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";

export function PrototypeSwitcher({ current, variants, onChange }: { current: string; variants: readonly { key: string; name: string }[]; onChange: (key: string) => void }) {
  const index = variants.findIndex((variant) => variant.key === current);
  const cycle = (direction: number) => onChange(variants[(index + direction + variants.length) % variants.length].key);
  useEffect(() => {
    const keydown = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement;
      if (event.altKey || event.ctrlKey || event.metaKey || event.shiftKey || document.querySelector('[role="dialog"]') || target.closest('input, textarea, select, [contenteditable], [role="tablist"], [role="combobox"]')) return;
      if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
        event.preventDefault();
        onChange(variants[(index + (event.key === "ArrowLeft" ? -1 : 1) + variants.length) % variants.length].key);
      }
    };
    window.addEventListener("keydown", keydown);
    return () => window.removeEventListener("keydown", keydown);
  }, [index, onChange, variants]);
  if (process.env.NODE_ENV === "production") return null;
  return <nav aria-label="Prototype directions" className="fixed bottom-5 left-1/2 z-40 flex -translate-x-1/2 items-center gap-2 rounded-full border border-primary/10 bg-primary p-2 text-primary-foreground shadow-sheet">
    <Button variant="ghost" size="icon" aria-label="Previous direction" onClick={() => cycle(-1)}><ArrowLeft /></Button>
    <div className="min-w-48 text-center"><span className="block text-2xs uppercase tracking-widest opacity-60">Prototype · {index + 1} of {variants.length}</span><span className="text-sm font-medium">{current} / {variants[index].name}</span></div>
    <Button variant="ghost" size="icon" aria-label="Next direction" onClick={() => cycle(1)}><ArrowRight /></Button>
  </nav>;
}
