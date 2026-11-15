import { useSyncExternalStore, type ReactNode } from "react";
import { AppRouterContext } from "next/dist/shared/lib/app-router-context.shared-runtime";
import { PathnameContext, SearchParamsContext } from "next/dist/shared/lib/hooks-client-context.shared-runtime";

// Supply Next's native-history integration to jsdom; browser tests exercise Next itself.
for (const method of ["pushState", "replaceState"] as const) {
  const original = window.history[method].bind(window.history);
  window.history[method] = (...args: Parameters<History[typeof method]>) => {
    original(args[0], args[1], args[2]);
    window.dispatchEvent(new Event("test-navigation"));
  };
}
const subscribe = (listener: () => void) => {
  window.addEventListener("test-navigation", listener);
  window.addEventListener("popstate", listener);
  return () => { window.removeEventListener("test-navigation", listener); window.removeEventListener("popstate", listener); };
};
export function TestNavigation({ children }: { children: ReactNode }) {
  const url = useSyncExternalStore(subscribe, () => window.location.href);
  const parsed = new URL(url);
  return <AppRouterContext.Provider value={{ back: () => window.history.back(), forward: () => window.history.forward(), refresh: () => {}, push: url => window.history.pushState(null, "", url), replace: url => window.history.replaceState(null, "", url), prefetch: async () => {} }}>
    <PathnameContext.Provider value={parsed.pathname}><SearchParamsContext.Provider value={parsed.searchParams}>{children}</SearchParamsContext.Provider></PathnameContext.Provider>
  </AppRouterContext.Provider>;
}
