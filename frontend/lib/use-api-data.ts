"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { ApiError, isApiError, type Api } from "@/lib/api";
import { useApi } from "@/lib/demo-mode";

/**
 * Fetch data through the current Api (demo or live), exposing loading/error/data.
 * `fetcher` is read via a ref (not a dependency) so inline arrow functions don't
 * cause an infinite loop; `deps` controls when to re-fetch.
 */
export function useApiData<T>(fetcher: (api: Api) => Promise<T>, deps: unknown[] = []) {
  const api = useApi();
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  const [data, setData] = useState<T | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  const reload = useCallback(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setData(null);
    fetcherRef.current(api)
      .then((d) => {
        if (!cancelled) setData(d);
      })
      .catch((e: unknown) => {
        if (!cancelled) {
          setError(isApiError(e) ? e : new ApiError("UNEXPECTED_STATUS", "Something went wrong."));
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [api, ...deps]);

  useEffect(() => reload(), [reload]);

  return { data, loading, error, reload };
}
