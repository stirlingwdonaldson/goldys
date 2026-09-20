"use client";

import { useDemoMode } from "@/lib/demo-mode";

export function DemoModeToggle() {
  const { demo, setDemo } = useDemoMode();

  return (
    <div className="flex items-center justify-between rounded-lg border p-4">
      <div>
        <p className="text-sm font-medium">Demo data</p>
        <p className="text-sm text-muted-foreground">
          Use sample data instead of the live backend.
        </p>
      </div>
      <button
        type="button"
        role="switch"
        aria-checked={demo}
        onClick={() => setDemo(!demo)}
        className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full transition-colors ${
          demo ? "bg-primary" : "bg-muted"
        }`}
      >
        <span
          className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${
            demo ? "translate-x-6" : "translate-x-1"
          }`}
        />
      </button>
    </div>
  );
}
