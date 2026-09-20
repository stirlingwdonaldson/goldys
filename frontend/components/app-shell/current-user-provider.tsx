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
import { ApiError, getCurrentUser, type CurrentUser } from "@/lib/api";

export type AuthStatus = "loading" | "authenticated" | "unauthenticated" | "error";

interface CurrentUserContextValue {
  user: CurrentUser | null;
  status: AuthStatus;
  error: ApiError | null;
  refresh: () => void;
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

  const refresh = useCallback(() => {
    setStatus("loading");
    setError(null);
    getCurrentUser()
      .then((u) => {
        setUser(u);
        setStatus("authenticated");
      })
      .catch((e: ApiError) => {
        setError(e);
        // No active staff profile (or the normal no-OIDC dev state) is not a crash.
        setStatus(e.code === "NOT_PERMITTED" ? "unauthenticated" : "error");
      });
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  const value = useMemo(
    () => ({ user, status, error, refresh }),
    [user, status, error, refresh],
  );

  return <CurrentUserContext.Provider value={value}>{children}</CurrentUserContext.Provider>;
}
