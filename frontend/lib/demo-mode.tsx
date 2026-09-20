"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
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
  // Server and client must agree on the initial render to avoid a hydration
  // mismatch. Both use the build-time env (NEXT_PUBLIC_* is inlined); the
  // localStorage override is applied only after mount, in a useEffect below.
  const envDefault = process.env.NEXT_PUBLIC_DEMO_MODE !== "false";
  const [demo, setDemoState] = useState<boolean>(envDefault);

  useEffect(() => {
    const stored = window.localStorage.getItem("goldys-demo-mode");
    if (stored != null) setDemoState(stored === "true");
  }, []);

  const setDemo = useCallback((value: boolean) => {
    setDemoState(value);
    window.localStorage.setItem("goldys-demo-mode", String(value));
  }, []);

  const value = useMemo(() => ({ demo, setDemo }), [demo, setDemo]);

  return <DemoModeContext.Provider value={value}>{children}</DemoModeContext.Provider>;
}

/**
 * The data source for the current demo/live mode. Screens call this, never
 * demo/live directly.
 *
 * IMPORTANT: this must return a referentially stable value (the module-singleton
 * `demoApi`/`liveApi`), because `useApiData` depends on `api` identity to decide
 * when to re-fetch. Do not wrap the return in a fresh object or useMemo-less call.
 */
export function useApi(): Api {
  const { demo } = useDemoMode();
  return demo ? demoApi : liveApi;
}
