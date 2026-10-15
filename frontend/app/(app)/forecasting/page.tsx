"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { TablePagination, useClientPagination } from "@/components/data-table/table-pagination";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { PageHeader } from "@/components/layout/page-header";
import { EmptyState } from "@/components/states/empty-state";
import { ErrorState } from "@/components/states/error-state";
import { LoadingState } from "@/components/states/loading-state";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useApi } from "@/lib/demo-mode";
import { useApiData } from "@/lib/use-api-data";
import { formatDateTime, formatDay, formatNumber, formatPercent } from "@/lib/format";
import { ForecastVsBaselineChart } from "@/components/forecasting/forecast-vs-baseline-chart";
import { FoodBevSplitChart } from "@/components/forecasting/food-bev-split-chart";
import { FeatureImportanceChart } from "@/components/forecasting/feature-importance-chart";
import { BacktestChart } from "@/components/forecasting/backtest-chart";
import { ResidualChart } from "@/components/forecasting/residual-chart";
import { PredictedVsActualChart } from "@/components/forecasting/predicted-vs-actual-chart";
import { WeekdayErrorChart } from "@/components/forecasting/weekday-error-chart";
import type {
  BacktestRow,
  ForecastComparison,
  ForecastDayRow,
  ForecastDiagnostics,
  ForecastRun,
  WeekdayErrorRow,
} from "@/lib/api/types";

const HORIZONS = [7, 14, 30, 90];

const TABS = [
  { value: "forecast", label: "Forecast" },
  { value: "model", label: "Model" },
  { value: "backtest", label: "Backtest" },
  { value: "runs", label: "Runs" },
] as const;

type Tab = (typeof TABS)[number]["value"];

/**
 * Fixed forecasting configuration: predict net revenue from the demand signals a pub knows ahead
 * of time. The target and the model's covariate set are intentionally not user-editable — the
 * runtime trains on a fixed internal set (`net`, `booked_covers`, `bookings`, `food`, `bev`), so
 * every run uses the same, sensible model.
 */
const TARGET_METRIC = "sales.gross";

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

/**
 * The external-driver chips for one forecast day: the booked covers on the book, public-holiday
 * flags (with the holiday's name), school/university terms, and the forecast weather. Only drivers
 * actually present are shown; a day with no drivers renders no chips.
 */
function driverChips(day: ForecastDayRow): string[] {
  const chips: string[] = [];
  if (day.bookedCovers != null) chips.push(`${day.bookedCovers} booked covers`);
  if (day.publicHoliday) {
    chips.push(day.publicHolidayName ? `Public holiday · ${day.publicHolidayName}` : "Public holiday");
  }
  if (day.publicHolidayEve) chips.push("Public holiday eve");
  if (day.dayAfterPublicHoliday) chips.push("Day after public holiday");
  if (day.schoolHoliday) chips.push("School holidays");
  if (day.uniInSession) chips.push("Uni in session");
  if (day.weatherSource != null) {
    const parts = [
      day.serviceTempMaxC != null ? `${day.serviceTempMaxC}°` : null,
      day.serviceRainMm != null ? `${day.serviceRainMm} mm rain` : null,
    ].filter((p): p is string => p != null);
    if (parts.length > 0) chips.push(parts.join(" · "));
  }
  return chips;
}

/** Day-by-day forecast: each day's median and its driver chips. */
function DayByDay({ rows }: { rows: ForecastDayRow[] }) {
  return (
    <ul data-testid="day-by-day" className="divide-y">
      {rows.map((d) => (
        <li key={d.date} className="flex flex-wrap items-center justify-between gap-2 py-2">
          <div className="flex items-baseline gap-3">
            <span className="text-sm font-medium">{formatDay(d.date)}</span>
            <span className="text-sm text-muted-foreground">{formatNumber(d.median)}</span>
          </div>
          <div className="flex flex-wrap gap-1.5">
            {driverChips(d).map((chip) => (
              <Badge key={chip} variant="neutral">
                {chip}
              </Badge>
            ))}
          </div>
        </li>
      ))}
    </ul>
  );
}

/** The full forecast table: one row per forecast day, paged like the other plain tables. */
function ForecastTable({ rows }: { rows: ForecastDayRow[] }) {
  const { pageRows, pagination } = useClientPagination(rows);
  return (
    <div className="flex flex-col gap-3">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-muted-foreground">
            <th className="font-medium">Date</th>
            <th className="font-medium">Median</th>
            <th className="font-medium">P10–P90</th>
            <th className="font-medium">Food</th>
            <th className="font-medium">Beverage</th>
            <th className="font-medium">Baseline</th>
            <th className="font-medium">Booked covers</th>
          </tr>
        </thead>
        <tbody>
          {pageRows.map((d) => (
            <tr key={d.date} className="border-t">
              <td className="py-1.5">{formatDay(d.date)}</td>
              <td className="py-1.5">{formatNumber(d.median)}</td>
              <td className="py-1.5">
                {formatNumber(d.p10)} – {formatNumber(d.p90)}
              </td>
              <td className="py-1.5">{formatNumber(d.food)}</td>
              <td className="py-1.5">{formatNumber(d.bev)}</td>
              <td className="py-1.5">{formatNumber(d.baseline)}</td>
              <td className="py-1.5">{formatNumber(d.bookedCovers)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <TablePagination {...pagination} />
    </div>
  );
}

/** Holdout and cross-validation accuracy of the model against the seasonal-naive baseline. */
function AccuracyComparisonTable({ diagnostics }: { diagnostics: ForecastDiagnostics }) {
  const rows = [
    { label: "Holdout", model: diagnostics.holdoutNetXgboost, baseline: diagnostics.holdoutNetBaseline },
    { label: "Cross-validation", model: diagnostics.cvNetXgboost, baseline: diagnostics.cvNetBaseline },
  ];
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-muted-foreground">
          <th className="font-medium">Set</th>
          <th className="font-medium">Model MAE</th>
          <th className="font-medium">Baseline MAE</th>
          <th className="font-medium">Model MAPE</th>
          <th className="font-medium">Baseline MAPE</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.label} className="border-t">
            <td className="py-1.5 font-medium">{r.label}</td>
            <td className="py-1.5">{formatNumber(r.model?.mae, 1)}</td>
            <td className="py-1.5">{formatNumber(r.baseline?.mae, 1)}</td>
            <td className="py-1.5">{formatPercent(r.model?.mape)}</td>
            <td className="py-1.5">{formatPercent(r.baseline?.mape)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** A banner that calls out when the model lost to the seasonal-naive baseline on holdout. */
function BeatsBaselineBanner({ beatsBaseline }: { beatsBaseline: boolean | null }) {
  if (beatsBaseline == null) return null;
  return beatsBaseline ? (
    <div className="rounded-xl bg-status-success-soft p-3 text-sm font-medium text-status-success">
      Beats the seasonal-naive baseline on holdout accuracy.
    </div>
  ) : (
    <div
      role="status"
      className="rounded-xl bg-status-conflict-soft p-3 text-sm font-medium text-status-conflict"
    >
      Does not beat the seasonal-naive baseline — the baseline was more accurate on holdout.
    </div>
  );
}

/** Error by weekday: where the model is systematically weak. */
function WeekdayErrorTable({ rows }: { rows: WeekdayErrorRow[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-muted-foreground">
          <th className="font-medium">Day</th>
          <th className="font-medium">N</th>
          <th className="font-medium">MAE</th>
          <th className="font-medium">MAPE</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.day ?? "unknown"} className="border-t">
            <td className="py-1.5">{r.day ?? "—"}</td>
            <td className="py-1.5">{formatNumber(r.n)}</td>
            <td className="py-1.5">{formatNumber(r.mae, 1)}</td>
            <td className="py-1.5">{r.mape == null ? "—" : `${formatNumber(r.mape, 1)}%`}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** The backtest days the model missed worst, largest absolute percentage error first. */
function WorstMissesTable({ rows }: { rows: BacktestRow[] }) {
  return (
    <table className="w-full text-sm">
      <thead>
        <tr className="text-left text-muted-foreground">
          <th className="font-medium">Date</th>
          <th className="font-medium">Actual</th>
          <th className="font-medium">Predicted</th>
          <th className="font-medium">Error</th>
          <th className="font-medium">|%|</th>
        </tr>
      </thead>
      <tbody>
        {rows.map((r) => (
          <tr key={r.date ?? "unknown"} className="border-t">
            <td className="py-1.5">{r.date ? formatDay(r.date) : "—"}</td>
            <td className="py-1.5">{formatNumber(r.actualNet)}</td>
            <td className="py-1.5">{formatNumber(r.predictedNet)}</td>
            <td className="py-1.5">{formatNumber(r.error)}</td>
            <td className="py-1.5">{r.absPctError == null ? "—" : `${formatNumber(r.absPctError, 1)}%`}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

interface ComparisonState {
  runId: string;
  loading: boolean;
  data: ForecastComparison | null;
  error: string | null;
}

type ComparisonPoint = ForecastComparison["points"][number];

/** Predicted median against the actual, one row per forecast day, paged. */
function ComparisonTable({ points }: { points: ComparisonPoint[] }) {
  const { pageRows, pagination } = useClientPagination(points);
  return (
    <div className="flex flex-col gap-3">
      <table className="w-full text-sm">
        <thead>
          <tr className="text-left text-muted-foreground">
            <th className="font-medium">Date</th>
            <th className="font-medium">Predicted median</th>
            <th className="font-medium">Actual</th>
          </tr>
        </thead>
        <tbody>
          {pageRows.map((p) => (
            <tr key={p.date} className="border-t">
              <td className="py-1.5">{formatDay(p.date)}</td>
              <td className="py-1.5">{formatNumber(p.predictedMedian)}</td>
              <td className="py-1.5">{p.actual == null ? "—" : formatNumber(p.actual)}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <TablePagination {...pagination} />
    </div>
  );
}

export default function ForecastingPage() {
  const api = useApi();
  const metricsQuery = useApiData((a) => a.listForecastMetrics());
  const runsQuery = useApiData((a) => a.listForecastRuns());

  const [tab, setTab] = useState<Tab>("forecast");
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
  const metricLabel = (key: string): string => {
    const m = metricByKey.get(key);
    return m ? `${m.name} — ${m.sourceDomain} · ${m.unit}` : key;
  };

  useEffect(() => {
    return () => {
      if (pollTimer.current) clearTimeout(pollTimer.current);
    };
  }, []);

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
    setStarting(true);
    setCreateError(null);
    setActiveRun(null);
    setComparison(null);
    try {
      const run = await api.createForecastRun({
        target: TARGET_METRIC,
        covariates: [],
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

  async function handleViewRun(id: string) {
    try {
      const run = await api.getForecastRun(id);
      setActiveRun(run);
      setTab("forecast");
    } catch {
      // The runs list still shows the run's status; a later refresh recovers it.
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

  const diagnostics = activeRun?.diagnostics ?? null;
  const series = activeRun?.series ?? null;
  const backtest = diagnostics?.backtest ?? null;

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Forecasting"
        description="Forecast revenue forward from the bookings, covers and demand signals the pub knows ahead of time."
      />

      <Tabs value={tab} onValueChange={(v) => setTab(v as Tab)}>
        <TabsList aria-label="Forecasting views">
          {TABS.map((t) => (
            <TabsTrigger key={t.value} value={t.value}>
              {t.label}
            </TabsTrigger>
          ))}
        </TabsList>

        <TabsContent value="forecast">
          <div className="flex flex-col gap-6">
            <Card>
              <CardHeader>
                <CardTitle>Set up a forecast</CardTitle>
                <CardDescription>
                  Predicts net revenue using a fixed internal model over bookings, covers and other
                  demand signals known ahead of time.
                </CardDescription>
              </CardHeader>
              <CardContent className="flex flex-col gap-6">
                <div className="flex flex-col gap-2">
                  <span className="text-sm font-medium">Target metric</span>
                  <p className="text-sm text-muted-foreground">{metricLabel(TARGET_METRIC)}</p>
                </div>

                <div className="flex flex-col gap-2">
                  <span className="text-sm font-medium">Model</span>
                  <p className="text-sm text-muted-foreground">
                    A fixed set of internal covariates — net revenue, booked covers, bookings, food
                    and beverage — so every run trains the same model.
                  </p>
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
                  <Button variant="brand" onClick={handleRun} disabled={starting}>
                    {starting ? "Starting…" : "Run forecast"}
                  </Button>
                </div>
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

            {activeRun?.status === "SUCCEEDED" && series ? (
              <>
                <Card>
                  <CardHeader>
                    <CardTitle>{metricName(activeRun.targetMetric)} forecast</CardTitle>
                    <CardDescription>
                      {activeRun.horizonDays}-day forecast from {activeRun.historyTo}.
                    </CardDescription>
                  </CardHeader>
                  <CardContent className="flex flex-col gap-6">
                    <ForecastVsBaselineChart rows={series} />
                    <FoodBevSplitChart rows={series} />
                    <p className="text-xs text-muted-foreground">
                      Food/bev split is approximate — pending category refinement.
                    </p>
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle>Day by day</CardTitle>
                    <CardDescription>
                      Each forecast day with the demand drivers known at forecast time.
                    </CardDescription>
                  </CardHeader>
                  <CardContent>
                    <DayByDay rows={series} />
                  </CardContent>
                </Card>

                <Card>
                  <CardHeader>
                    <CardTitle>Forecast table</CardTitle>
                    <CardDescription>The full series, split and baseline for every day.</CardDescription>
                  </CardHeader>
                  <CardContent>
                    <ForecastTable rows={series} />
                  </CardContent>
                </Card>
              </>
            ) : null}
          </div>
        </TabsContent>

        <TabsContent value="model">
          {diagnostics ? (
            <div className="flex flex-col gap-6">
              <BeatsBaselineBanner beatsBaseline={diagnostics.beatsBaseline} />

              <Card>
                <CardHeader>
                  <CardTitle>Accuracy vs baseline</CardTitle>
                  <CardDescription>Holdout and cross-validation error against the seasonal-naive baseline.</CardDescription>
                </CardHeader>
                <CardContent>
                  <AccuracyComparisonTable diagnostics={diagnostics} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Feature importance</CardTitle>
                  <CardDescription>How much each feature contributes to the net-revenue model.</CardDescription>
                </CardHeader>
                <CardContent>
                  <FeatureImportanceChart rows={diagnostics.importance ?? []} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Features</CardTitle>
                  <CardDescription>The covariates the model was trained on.</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                  <div className="flex flex-wrap gap-1.5">
                    {(diagnostics.features ?? []).map((f) => (
                      <Badge key={f} variant="neutral">
                        {f}
                      </Badge>
                    ))}
                  </div>
                  {(diagnostics.warnings ?? []).length > 0 ? (
                    <div className="flex flex-col gap-1.5">
                      <span className="text-sm font-medium">Warnings</span>
                      <ul className="list-disc pl-5 text-sm text-muted-foreground">
                        {(diagnostics.warnings ?? []).map((w) => (
                          <li key={w}>{w}</li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                </CardContent>
              </Card>
            </div>
          ) : (
            <EmptyState
              title="No model diagnostics"
              description="Model accuracy, importance and features appear once a forecast run succeeds."
            />
          )}
        </TabsContent>

        <TabsContent value="backtest">
          {backtest ? (
            <div className="flex flex-col gap-6">
              <Card>
                <CardHeader>
                  <CardTitle>Actual vs model vs baseline</CardTitle>
                  <CardDescription>Fitted model against actuals over the training history.</CardDescription>
                </CardHeader>
                <CardContent>
                  <BacktestChart rows={backtest.rows ?? []} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Residuals</CardTitle>
                  <CardDescription>Where the model over- and under-predicts over time.</CardDescription>
                </CardHeader>
                <CardContent>
                  <ResidualChart rows={backtest.rows ?? []} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Predicted vs actual</CardTitle>
                  <CardDescription>Points above the line are over-predictions.</CardDescription>
                </CardHeader>
                <CardContent>
                  <PredictedVsActualChart rows={backtest.rows ?? []} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Error by weekday</CardTitle>
                  <CardDescription>Where the model is systematically weak.</CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-4">
                  <WeekdayErrorChart rows={backtest.byWeekday ?? []} />
                  <WeekdayErrorTable rows={backtest.byWeekday ?? []} />
                </CardContent>
              </Card>

              <Card>
                <CardHeader>
                  <CardTitle>Worst misses</CardTitle>
                  <CardDescription>The backtest days with the largest absolute percentage error.</CardDescription>
                </CardHeader>
                <CardContent>
                  <WorstMissesTable rows={backtest.worstDays ?? []} />
                </CardContent>
              </Card>
            </div>
          ) : (
            <EmptyState
              title="No backtest data"
              description="Backtest accuracy appears once a forecast run succeeds."
            />
          )}
        </TabsContent>

        <TabsContent value="runs">
          <div className="flex flex-col gap-6">
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
                          <div className="flex items-center gap-2">
                            <Button size="sm" variant="outline" onClick={() => handleViewRun(run.id)}>
                              View
                            </Button>
                            <Button size="sm" variant="outline" onClick={() => handleCompare(run.id)}>
                              Compare
                            </Button>
                          </div>
                        ) : null}
                      </li>
                    ))}
                  </ul>
                )}
              </CardContent>
            </Card>

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
                        <ComparisonTable points={comparison.data.points} />
                      )}
                    </>
                  ) : null}
                </CardContent>
              </Card>
            ) : null}
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}
