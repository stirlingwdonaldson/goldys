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
import { ApiError, getCurrentUser, isApiError, type CurrentUser } from "@/lib/api";

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
      .catch((e: unknown) => {
        const err = isApiError(e)
          ? e
          : new ApiError("UNEXPECTED_STATUS", "Something went wrong loading your profile.");
        setError(err);
        // NOT_PERMITTED (authenticated but no active profile) and an unparseable
        // response (the empty 401/403 body when OIDC isn't configured, or a
        // login-page redirect) are calm "not signed in" states, not server faults.
        // Everything else — a network failure, a real 5xx — is an error with a
        // retry path in the UI.
        const signedOut =
          err.code === "NOT_PERMITTED" || err.code === "UNPARSEABLE_RESPONSE";
        setStatus(signedOut ? "unauthenticated" : "error");
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
