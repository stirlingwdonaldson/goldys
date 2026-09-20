"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { demoApi } from "@/lib/api/demo";
import { liveApi } from "@/lib/api/live";
import type { Api } from "@/lib/api";

interface DemoModeContextValue {
  demo: boolean;
  setDemo: (demo: boolean) => void;
}

const DemoModeContext = createContext<DemoModeContextValue | null>(null);

export function useDemoMode(): DemoModeContextValue {
  const ctx = useContext(DemoModeContext);
  if (!ctx) throw new Error("useDemoMode must be used within a DemoModeProvider");
  return ctx;
}

export function DemoModeProvider({ children }: { children: ReactNode }) {
  const [demo, setDemoState] = useState<boolean>(() => {
    if (typeof window === "undefined") return true;
    const stored = window.localStorage.getItem("goldys-demo-mode");
    if (stored != null) return stored === "true";
    return process.env.NEXT_PUBLIC_DEMO_MODE !== "false";
  });

  const setDemo = useCallback((value: boolean) => {
    setDemoState(value);
    window.localStorage.setItem("goldys-demo-mode", String(value));
  }, []);

  const value = useMemo(() => ({ demo, setDemo }), [demo, setDemo]);

  return <DemoModeContext.Provider value={value}>{children}</DemoModeContext.Provider>;
}

/** The data source for the current demo/live mode. Screens call this, never demo/live directly. */
export function useApi(): Api {
  const { demo } = useDemoMode();
  return demo ? demoApi : liveApi;
}
