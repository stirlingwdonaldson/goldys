"use client";

import { MutationCache, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, useSyncExternalStore, type ReactNode } from "react";
import { captureException } from "@sentry/nextjs";
import type { ApiError } from "@/lib/api/errors";

declare module "@tanstack/react-query" {
  interface Register { defaultError: ApiError; }
}

/** Mounted within AuthGate and keyed by mode: no cache survives identity verification or mode changes. */
export function QueryProvider({ children, enabled = true }: { children: ReactNode; enabled?: boolean }) {
  const [client] = useState(() => new QueryClient({ mutationCache: new MutationCache({ onError: error => { captureException(error); } }), defaultOptions: {
    queries: { staleTime: 30_000, retry: false, enabled }, mutations: { retry: false },
  } }));
  return <QueryClientProvider client={client}><BackgroundRefreshNotice client={client} />{children}</QueryClientProvider>;
}


function BackgroundRefreshNotice({ client }: { client: QueryClient }) {
  const cache = client.getQueryCache();
  const failed = () => cache.findAll().filter(query => query.state.error !== null && query.state.data !== undefined);
  const count = useSyncExternalStore(listener => cache.subscribe(listener), () => failed().length, () => 0);
  if (!count) return null;
  return <div role="status" className="border-b bg-muted px-4 py-2 text-sm">
    Some displayed data could not be refreshed. The last available values are shown.
    <button className="ml-2 underline" onClick={() => { void client.invalidateQueries({ predicate: query => query.state.error !== null && query.state.data !== undefined }); }}>Retry refresh</button>
  </div>;
}
