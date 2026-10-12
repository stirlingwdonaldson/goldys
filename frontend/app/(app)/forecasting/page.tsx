"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  Area,
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { PageHeader } from "@/components/layout/page-header";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { formatDateTime, formatDay, formatNumber, formatPercent } from "@/lib/format";
import { AXIS_TICK, CHART_TOOLTIP_PROPS, compactAxis, formatXTick } from "@/components/widgets/chart-style";
import type {
  ForecastComparison,
  ForecastMetric,
  ForecastRun,
  ForecastSeriesPoint,
} from "@/lib/api/types";

const HORIZONS = [7, 14, 30, 90];

const STATUS_VARIANT: Record<ForecastRun["status"], "success" | "failed" | "info" | "neutral"> = {
  SUCCEEDED: "success",
  FAILED: "failed",
  QUEUED: "neutral",
  RUNNING: "info",
};

/** YYYY-MM-DD one year before today, the default history window start. */
function oneYearAgoISO(): string {
  const d = new Date();
  d.setUTCFullYear(d.getUTCFullYear() - 1);
  return d.toISOString().slice(0, 10);
}

/** The forecast series: a median line inside a p10–p90 shaded band. */
function ForecastChart({ series }: { series: ForecastSeriesPoint[] }) {
  return (
    <ResponsiveContainer width="100%" height="100%">
      <LineChart data={series} margin={{ top: 4, right: 8, left: -8, bottom: 0 }}>
        <CartesianGrid vertical={false} stroke="hsl(var(--border))" />
        <XAxis
          dataKey="date"
          tick={AXIS_TICK}
          tickLine={false}
          axisLine={false}
          minTickGap={16}
          tickFormatter={formatXTick}
        />
        <YAxis
          tick={AXIS_TICK}
          tickLine={false}
          axisLine={false}
          width={52}
          tickFormatter={(v: number) => compactAxis(v)}
        />
        <Tooltip {...CHART_TOOLTIP_PROPS} labelFormatter={formatXTick} formatter={(v: number) => formatNumber(v)} />
        {/* Two stacked areas draw a band between p10 and p90: the first is invisible,
            the second stacks on top so its fill covers exactly p10→p90. */}
        <Area type="monotone" dataKey="p10" stackId="band" stroke="none" fill="transparent" />
        <Area
          type="monotone"
          dataKey="p90"
          stackId="band"
          stroke="none"
          fill="hsl(var(--chart-1))"
          fillOpacity={0.15}
        />
        <Line
          type="monotone"
          dataKey="median"
          name="Median"
          stroke="hsl(var(--chart-1))"
          strokeWidth={2}
          dot={false}
          activeDot={{ r: 4, strokeWidth: 0 }}
        />
      </LineChart>
    </ResponsiveContainer>
  );
}

interface ComparisonState {
  runId: string;
  loading: boolean;
  data: ForecastComparison | null;
  error: string | null;
}

export default function ForecastingPage() {
  const api = useApi();
  const metricsQuery = useApiData((a) => a.listForecastMetrics());
  const runsQuery = useApiData((a) => a.listForecastRuns());

  const [target, setTarget] = useState<string | undefined>(undefined);
  const [covariates, setCovariates] = useState<string[]>([]);
  const [horizon, setHorizon] = useState<number>(7);
  const [activeRun, setActiveRun] = useState<ForecastRun | null>(null);
  const [starting, setStarting] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const [comparison, setComparison] = useState<ComparisonState | null>(null);
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const metrics = useMemo(() => metricsQuery.data ?? [], [metricsQuery.data]);
  const runs = runsQuery.data ?? [];

  const metricByKey = useMemo(() => new Map(metrics.map((m) => [m.metric, m])), [metrics]);
  const metricName = (key: string): string => metricByKey.get(key)?.name ?? key;

  const sourcesInUse = useMemo(
    () =>
      [target, ...covariates]
        .filter((key): key is string => Boolean(key))
        .map((key) => metricByKey.get(key))
        .filter((m): m is ForecastMetric => Boolean(m)),
    [target, covariates, metricByKey],
  );

  useEffect(() => {
    return () => {
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, []);

  // Default to the first metric once loaded, so the page is ready to run immediately
  // (and keeps the target selector deterministic for the screen's tests).
  useEffect(() => {
    if (target === undefined && metrics.length > 0) {
      setTarget(metrics[0].metric);
    }
  }, [target, metrics]);

  async function pollRun(id: string) {
    try {
      const run = await api.getForecastRun(id);
      setActiveRun(run);
      if (run.status === "QUEUED" || run.status === "RUNNING") {
        pollTimer.current = setTimeout(() => {
          void pollRun(id);
        }, 2000);
      } else {
        void runsQuery.reload();
      }
    } catch {
      // The run stays at its last known status; a later refresh of the runs list recovers it.
    }
  }

  async function handleRun() {
    if (!target) return;
    setStarting(true);
    setCreateError(null);
    setActiveRun(null);
    setComparison(null);
    try {
      const run = await api.createForecastRun({
        target,
        covariates,
        historyFrom: oneYearAgoISO(),
        horizonDays: horizon,
      });
      setActiveRun(run);
      if (run.status === "QUEUED" || run.status === "RUNNING") {
        void pollRun(run.id);
      } else {
        void runsQuery.reload();
      }
    } catch (e) {
      setCreateError(e instanceof Error ? e.message : "Couldn't start the forecast.");
    } finally {
      setStarting(false);
    }
  }

  async function handleCompare(id: string) {
    setComparison({ runId: id, loading: true, data: null, error: null });
    try {
      const data = await api.getForecastComparison(id);
      setComparison({ runId: id, loading: false, data, error: null });
    } catch (e) {
      setComparison({
        runId: id,
        loading: false,
        data: null,
        error: e instanceof Error ? e.message : "Couldn't load the comparison.",
      });
    }
  }

  function toggleCovariate(metric: string, checked: boolean) {
    setCovariates((prev) => (checked ? [...prev, metric] : prev.filter((c) => c !== metric)));
  }

  if (metricsQuery.loading) return <LoadingState rows={3} />;
  if (metricsQuery.error) {
    return (
      <ErrorState
        title="Couldn't load forecast metrics"
        message={metricsQuery.error.message}
        correlationId={metricsQuery.error.correlationId}
        onRetry={metricsQuery.reload}
      />
    );
  }
  if (metrics.length === 0) {
    return (
      <EmptyState
        title="No forecastable metrics"
        description="Forecasting becomes available once a metric with history resolves."
      />
    );
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Forecasting"
        description="Project a metric forward with TimesFM, using other metrics as context."
      />

      <Card>
        <CardHeader>
          <CardTitle>Set up a forecast</CardTitle>
          <CardDescription>Choose a target, optional covariates, and a horizon.</CardDescription>
        </CardHeader>
        <CardContent className="flex flex-col gap-6">
          <div className="flex flex-col gap-2">
            <Label htmlFor="forecast-target">Target metric</Label>
            <Select value={target} onValueChange={(v) => setTarget(v)}>
              <SelectTrigger id="forecast-target" aria-label="Target metric">
                <SelectValue placeholder="Select a target" />
              </SelectTrigger>
              <SelectContent>
                {metrics.map((m) => (
                  <SelectItem key={m.metric} value={m.metric}>
                    {m.name}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">Covariates</span>
            <p className="text-sm text-muted-foreground">
              Optional inputs that may influence the target. Each shows its source domain.
            </p>
            <div className="flex flex-col gap-2">
              {metrics
                .filter((m) => m.metric !== target)
                .map((m) => (
                  <div key={m.metric} className="flex items-center gap-2">
                    <Checkbox
                      id={`covariate-${m.metric}`}
                      checked={covariates.includes(m.metric)}
                      onCheckedChange={(checked) => toggleCovariate(m.metric, checked === true)}
                    />
                    <Label htmlFor={`covariate-${m.metric}`}>
                      {m.name} — {m.sourceDomain}
                    </Label>
                  </div>
                ))}
            </div>
          </div>

          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium">Horizon</span>
            <div role="group" aria-label="Horizon" className="flex flex-wrap gap-2">
              {HORIZONS.map((d) => (
                <Button
                  key={d}
                  type="button"
                  variant={horizon === d ? "default" : "outline"}
                  aria-pressed={horizon === d}
                  onClick={() => setHorizon(d)}
                >
                  {d} days
                </Button>
              ))}
            </div>
          </div>

          <div className="flex items-center gap-3">
            <Button variant="brand" onClick={handleRun} disabled={!target || starting}>
              {starting ? "Starting…" : "Run forecast"}
            </Button>
            {target ? null : (
              <span className="text-sm text-muted-foreground">Pick a target to run a forecast.</span>
            )}
          </div>

          <section data-testid="sources-in-use" className="rounded-lg border p-4">
            <h2 className="text-sm font-semibold">Sources in use</h2>
            {sourcesInUse.length === 0 ? (
              <p className="mt-1 text-sm text-muted-foreground">
                Pick a target to see which sources feed the forecast.
              </p>
            ) : (
              <ul className="mt-2 flex flex-col gap-1.5">
                {sourcesInUse.map((m) => (
                  <li key={m.metric} className="flex items-baseline justify-between gap-4 text-sm">
                    <span className="font-medium">{m.name}</span>
                    <span className="text-muted-foreground">
                      {m.sourceDomain} · {m.unit}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </CardContent>
      </Card>

      {createError ? <ErrorState title="Couldn't start the forecast" message={createError} /> : null}

      {activeRun?.status === "FAILED" ? (
        <ErrorState
          title="Forecast failed"
          message={activeRun.errorMessage ?? "The forecast run failed."}
        />
      ) : null}

      {activeRun?.status === "QUEUED" || activeRun?.status === "RUNNING" ? (
        <Card>
          <CardContent className="flex items-center gap-3 py-4">
            <Badge variant={STATUS_VARIANT[activeRun.status]}>{activeRun.status}</Badge>
            <p className="text-sm text-muted-foreground">Forecast is running — this can take a moment.</p>
          </CardContent>
        </Card>
      ) : null}

      {activeRun?.status === "SUCCEEDED" && activeRun.series ? (
        <Card>
          <CardHeader>
            <CardTitle>{metricName(activeRun.targetMetric)} forecast</CardTitle>
            <CardDescription>
              {activeRun.horizonDays}-day forecast from {activeRun.historyTo}.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div data-testid="forecast-chart" role="img" aria-label="Forecast chart" className="h-64 w-full">
              <ForecastChart series={activeRun.series} />
            </div>
          </CardContent>
        </Card>
      ) : null}

      {comparison ? (
        <Card>
          <CardHeader>
            <CardTitle>Compare to actuals</CardTitle>
            <CardDescription>Predicted median against resolved actuals.</CardDescription>
          </CardHeader>
          <CardContent className="flex flex-col gap-4">
            {comparison.loading ? (
              <p className="text-sm text-muted-foreground">Loading comparison…</p>
            ) : comparison.error ? (
              <p className="text-sm text-destructive">{comparison.error}</p>
            ) : comparison.data ? (
              <>
                <div className="flex gap-2">
                  <Badge variant="info">MAPE {formatPercent(comparison.data.mape)}</Badge>
                  <Badge variant="info">MAE {formatNumber(comparison.data.mae, 1)}</Badge>
                </div>
                {comparison.data.points.length === 0 ? (
                  <p className="text-sm text-muted-foreground">No actuals yet to compare.</p>
                ) : (
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="text-left text-muted-foreground">
                        <th className="font-medium">Date</th>
                        <th className="font-medium">Predicted median</th>
                        <th className="font-medium">Actual</th>
                      </tr>
                    </thead>
                    <tbody>
                      {comparison.data.points.map((p) => (
                        <tr key={p.date} className="border-t">
                          <td className="py-1.5">{formatDay(p.date)}</td>
                          <td className="py-1.5">{formatNumber(p.predictedMedian)}</td>
                          <td className="py-1.5">{p.actual == null ? "—" : formatNumber(p.actual)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </>
            ) : null}
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <CardTitle>Runs</CardTitle>
          <CardDescription>Recent forecast runs and their status.</CardDescription>
        </CardHeader>
        <CardContent>
          {runsQuery.loading ? (
            <p className="text-sm text-muted-foreground">Loading runs…</p>
          ) : runs.length === 0 ? (
            <p className="text-sm text-muted-foreground">No runs yet.</p>
          ) : (
            <ul data-testid="runs-list" className="divide-y">
              {runs.map((run) => (
                <li key={run.id} className="flex items-center justify-between gap-4 py-2">
                  <div className="flex items-center gap-3">
                    <Badge variant={STATUS_VARIANT[run.status]}>{run.status}</Badge>
                    <span className="text-sm font-medium">{metricName(run.targetMetric)}</span>
                    <span className="text-sm text-muted-foreground">{formatDateTime(run.createdAt)}</span>
                  </div>
                  {run.status === "SUCCEEDED" ? (
                    <Button size="sm" variant="outline" onClick={() => handleCompare(run.id)}>
                      Compare
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
