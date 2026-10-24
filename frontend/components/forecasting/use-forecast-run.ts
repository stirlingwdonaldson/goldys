"use client";

import { useEffect, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { useApi } from "@/lib/demo-mode";
import type { CreateForecastInput, ForecastRun } from "@/lib/api/types";

/** Owns the selected run and rejects replies from superseded status checks. */
export function useForecastRun() {
  const api = useApi();
  const queryClient = useQueryClient();
  const [activeRun, setActiveRun] = useState<ForecastRun | null>(null);
  const [starting, setStarting] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const work = useRef<{ generation: number; timer: ReturnType<typeof setTimeout> | null }>({ generation: 0, timer: null }).current;
  const [runError, setRunError] = useState<{ id: string; message: string } | null>(null);
  const [lastCheckedAt, setLastCheckedAt] = useState<string | null>(null);
  const [loadingRun, setLoadingRun] = useState(false);

  useEffect(() => {
    return () => {
      ++work.generation;
      if (work.timer) clearTimeout(work.timer);
    };
  }, [work]);

  async function pollRun(id: string, generation: number) {
    try {
      const run = await api.getForecastRun(id);
      if (generation !== work.generation) return;
      setRunError(null);
      setLastCheckedAt(new Date().toISOString());
      setActiveRun(run);
      if (run.status === "QUEUED" || run.status === "RUNNING") {
        work.timer = setTimeout(() => {
          void pollRun(id, generation);
        }, 2000);
      } else {
        void queryClient.invalidateQueries({ queryKey: ["listForecastRuns"] });
      }
    } catch (error) {
      if (generation === work.generation) setRunError({ id, message: error instanceof Error ? error.message : "The run could not be checked." });
    }
  }

  async function createRun(input: CreateForecastInput) {
    const generation = ++work.generation;
    if (work.timer) clearTimeout(work.timer);
    setRunError(null);
    setLastCheckedAt(null);
    setLoadingRun(false);
    setStarting(true);
    setCreateError(null);
    setActiveRun(null);
    try {
      const run = await api.createForecastRun(input);
      if (generation !== work.generation) return;
      setActiveRun(run);
      setLastCheckedAt(new Date().toISOString());
      if (run.status === "QUEUED" || run.status === "RUNNING") {
        void pollRun(run.id, generation);
      } else {
        void queryClient.invalidateQueries({ queryKey: ["listForecastRuns"] });
      }
    } catch (e) {
      if (generation === work.generation) setCreateError(e instanceof Error ? e.message : "Couldn't start the forecast.");
    } finally {
      if (generation === work.generation) setStarting(false);
    }
  }

  async function viewRun(id: string) {
    const generation = ++work.generation;
    if (work.timer) clearTimeout(work.timer);
    if (activeRun?.id !== id) { setActiveRun(null); setLastCheckedAt(null); }
    setRunError(null);
    setStarting(false);
    setLoadingRun(true);
    await pollRun(id, generation);
    if (generation === work.generation) setLoadingRun(false);
  }

  return { activeRun, starting, createError, runError, lastCheckedAt, loadingRun, createRun, viewRun };
}
