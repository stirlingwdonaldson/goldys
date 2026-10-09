"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";
import { ApiError, getCurrentUser, isApiError, isAbortError, type CurrentUser } from "@/lib/api";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated" | "error";

interface CurrentUserContextValue {
  user: CurrentUser | null;
  status: AuthStatus;
  error: ApiError | null;
  refresh: () => void;
  clear: () => void;
}

const CurrentUserContext = createContext<CurrentUserContextValue | null>(null);

export function useCurrentUser(): CurrentUserContextValue {
  const ctx = useContext(CurrentUserContext);
  if (!ctx) throw new Error("useCurrentUser must be used within a CurrentUserProvider");
  return ctx;
}

export function CurrentUserProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [error, setError] = useState<ApiError | null>(null);
  const generation = useRef(0);
  const active = useRef<AbortController | null>(null);

  const cancelActive = useCallback(() => {
    const current = ++generation.current;
    active.current?.abort();
    active.current = null;
    return current;
  }, []);

  const refresh = useCallback(() => {
    const current = cancelActive();
    const controller = new AbortController();
    active.current = controller;
    // A prior identity must not grant presentation privileges during verification.
    setUser(null);
    setStatus("loading");
    setError(null);
    void getCurrentUser({ signal: controller.signal })
      .then((u) => {
        if (current !== generation.current) return;
        setUser(u);
        setStatus("authenticated");
      })
      .catch((e: unknown) => {
        if (current !== generation.current || isAbortError(e)) return;
        const err = isApiError(e)
          ? e
          : new ApiError("UNEXPECTED_STATUS", "Something went wrong loading your profile.", undefined, undefined,
            { kind: "unexpected", cause: e });
        setUser(null);
        setError(err);
        const signedOut = err.status === 401 || err.code === "AUTH_REQUIRED";
        setStatus(signedOut ? "unauthenticated" : "error");
      });
  }, [cancelActive]);

  const clear = useCallback(() => {
    cancelActive();
    setUser(null);
    setError(null);
    setStatus("unauthenticated");
  }, [cancelActive]);

  useEffect(() => {
    refresh();
    return () => {
      cancelActive();
    };
  }, [refresh, cancelActive]);

  const value = useMemo(
    () => ({ user, status, error, refresh, clear }),
    [user, status, error, refresh, clear],
  );

  return <CurrentUserContext.Provider value={value}>{children}</CurrentUserContext.Provider>;
}
