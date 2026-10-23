import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, within, act } from "@testing-library/react";
import ForecastingPage from "./page";
import type {
  ForecastComparison,
  ForecastDiagnostics,
  ForecastMetric,
  ForecastRun,
} from "@/lib/api/types";

const metrics: ForecastMetric[] = [
  { metric: "sales.net", name: "Net sales", unit: "AUD", sourceDomain: "resolved_daily_sales", requiredPermission: "reconciliation.sales" },
  { metric: "reservations.bookings", name: "Bookings", unit: "count", sourceDomain: "resolved_reservation_day", requiredPermission: "reservations.metrics" },
  { metric: "reservations.covers", name: "Covers", unit: "count", sourceDomain: "resolved_reservation_day", requiredPermission: "reservations.metrics" },
  { metric: "labour.scheduled_hours", name: "Scheduled hours", unit: "hours", sourceDomain: "resolved_labour_day", requiredPermission: "labour.hours" },
];

/** A diagnostics payload whose model beats the baseline, with importance and a backtest. */
const diagnostics: ForecastDiagnostics = {
  modelVersion: "timesfm-3.0 + xgboost-twin",
  diagnosticModel: "xgboost",
  validationMethod: "recursive-holdout-v1",
  trainedRows: 287,
  reliable: true,
  warnings: [],
  features: ["day_of_week", "booked_covers", "net_revenue_lag_7"],
  shareFeatures: ["day_of_week", "booked_covers"],
  holdoutNetXgboost: { mae: 421.5, rmse: 512.8, mape: 4.12, n: 72 },
  holdoutNetBaseline: { mae: 640.0, rmse: 780.0, mape: 6.25, n: 72 },
  holdoutFood: null,
  holdoutBev: null,
  cvNetXgboost: { mae: 445.0, rmse: 540.0, mape: 4.3, n: 215 },
  cvNetBaseline: { mae: 655.0, rmse: 800.0, mape: 6.4, n: 215 },
  beatsBaseline: true,
  importance: [
    { feature: "booked_covers", importance: 0.38 },
    { feature: "net_revenue_lag_7", importance: 0.21 },
  ],
  backtest: {
    rows: [
      { date: "2026-09-26", day: "Saturday", actualNet: 11820, predictedNet: 11240, error: 580, absPctError: 4.9, actualFood: 5320, predictedFood: 5060, actualBev: 6500, predictedBev: 6180, baselineNet: 10980 },
      { date: "2026-09-27", day: "Sunday", actualNet: 10450, predictedNet: 10780, error: -330, absPctError: 3.2, actualFood: 4700, predictedFood: 4850, actualBev: 5750, predictedBev: 5930, baselineNet: 10200 },
    ],
    byWeekday: [
      { day: "Monday", n: 41, mae: 342.0, mape: 4.3, meanActual: 7950, meanPredicted: 7820 },
      { day: "Saturday", n: 41, mae: 470.0, mape: 4.1, meanActual: 11850, meanPredicted: 11400 },
    ],
    residualMean: 12.5,
    residualStd: 480.2,
    worstDays: [
      { date: "2026-10-03", day: "Saturday", actualNet: 12540, predictedNet: 11480, error: 1060, absPctError: 8.5, actualFood: 5640, predictedFood: 5170, actualBev: 6900, predictedBev: 6310, baselineNet: 11100 },
    ],
  },
};

const succeededRun: ForecastRun = {
  id: "fc-2",
  status: "SUCCEEDED",
  targetMetric: "sales.net",
  covariates: [],
  historyFrom: "2025-10-05",
  historyTo: "2026-10-05",
  horizonDays: 7,
  createdAt: "2026-10-06T02:00:00Z",
  series: [
    { date: "2026-10-06", median: 10000, p10: 9400, p90: 10600, food: 4500, bev: 5500, baseline: 9700, bookedCovers: 120, observedBookedCovers: 60, publicHoliday: false, publicHolidayEve: false, dayAfterPublicHoliday: false, schoolHoliday: true, uniInSession: false, publicHolidayName: null, weatherSource: "open-meteo", serviceTempMaxC: 21, serviceRainMm: 0 },
    { date: "2026-10-07", median: 10500, p10: 9900, p90: 11100, food: 4725, bev: 5775, baseline: 10185, bookedCovers: 135, observedBookedCovers: null, publicHoliday: true, publicHolidayEve: false, dayAfterPublicHoliday: false, schoolHoliday: false, uniInSession: true, publicHolidayName: "Melbourne Cup", weatherSource: null, serviceTempMaxC: null, serviceRainMm: null },
  ],
  errorMessage: null,
  modelVersion: "timesfm-3.0 + xgboost-twin",
  diagnostics,
};

/** A succeeded run whose model lost to the baseline, to exercise the banner. */
const losingRun: ForecastRun = {
  ...succeededRun,
  id: "fc-lose",
  diagnostics: { ...diagnostics, beatsBaseline: false },
};

const queuedRun: ForecastRun = {
  id: "fc-1",
  status: "QUEUED",
  targetMetric: "sales.net",
  covariates: [],
  historyFrom: "2025-10-06",
  historyTo: "2026-10-06",
  horizonDays: 14,
  createdAt: "2026-10-06T01:30:00Z",
  series: null,
  errorMessage: null,
  modelVersion: null,
  diagnostics: null,
};

const comparison: ForecastComparison = {
  runId: "fc-2",
  mape: 0.0412,
  mae: 512.4,
  points: [
    { date: "2026-10-06", predictedMedian: 10000, actual: 9254.1 },
    { date: "2026-10-07", predictedMedian: 10500, actual: null },
  ],
};

const mocks = vi.hoisted(() => {
  const api = {
    listForecastMetrics: vi.fn(),
    createForecastRun: vi.fn(),
    getForecastRun: vi.fn(),
    listForecastRuns: vi.fn(),
    getForecastComparison: vi.fn(),
  };
  return { api };
});

vi.mock("@/lib/demo-mode", () => ({
  useApi: () => mocks.api,
}));

// The screen renders the fixed revenue target once the metric list has loaded; await it before
// driving the run flow. The tab bar renders immediately.
async function renderLoaded() {
  render(<ForecastingPage />);
  await screen.findByText("Net sales — resolved_daily_sales · AUD");
}

/** Drives the run flow to a succeeded run (create returns QUEUED, the poll resolves to SUCCEEDED). */
async function runForecast() {
  await act(async () => {
    fireEvent.click(screen.getByRole("button", { name: "Run forecast" }));
  });
  // The poll resolves the queued run to SUCCEEDED and the forecast-vs-baseline chart renders.
  await screen.findByTestId("forecast-vs-baseline-chart");
}

describe("ForecastingPage", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mocks.api.listForecastMetrics.mockResolvedValue(metrics);
    mocks.api.listForecastRuns.mockResolvedValue([succeededRun, queuedRun]);
    mocks.api.createForecastRun.mockResolvedValue({ ...queuedRun, id: "fc-new" });
    mocks.api.getForecastRun.mockResolvedValue(succeededRun);
    mocks.api.getForecastComparison.mockResolvedValue(comparison);
  });

  it("renders the four tabs", async () => {
    await renderLoaded();

    expect(screen.getByRole("tab", { name: "Forecast" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Model" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Backtest" })).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Runs" })).toBeInTheDocument();
  });

  it("shows the fixed config with no predictor picker and a changeable horizon", async () => {
    await renderLoaded();

    // The target is locked to revenue and shown read-only.
    expect(screen.getByText("Net sales — resolved_daily_sales · AUD")).toBeInTheDocument();
    // The model's covariate set is fixed, described but not selectable.
    expect(
      screen.getByText(/A fixed set of internal covariates — net revenue, booked covers, bookings, food/),
    ).toBeInTheDocument();
    // There is no target dropdown or covariate checkbox to change.
    expect(screen.queryByRole("combobox")).toBeNull();
    expect(screen.queryByRole("checkbox")).toBeNull();

    // The horizon stays changeable.
    expect(screen.getByRole("button", { name: "7 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "14 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "30 days" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "90 days" })).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "30 days" }));
    expect(screen.getByRole("button", { name: "30 days" })).toHaveAttribute("aria-pressed", "true");
  });

  it("runs a forecast and renders the forecast-vs-baseline, split, day-by-day chips and split note", async () => {
    await renderLoaded();
    await runForecast();

    expect(mocks.api.createForecastRun).toHaveBeenCalledWith(
      expect.objectContaining({ target: "sales.net", covariates: [], horizonDays: 7 }),
    );

    // The forecast-vs-baseline and food/bev split charts render.
    expect(screen.getByTestId("forecast-vs-baseline-chart")).toBeInTheDocument();
    expect(screen.getByTestId("food-bev-split-chart")).toBeInTheDocument();

    // The split carries the approximation note.
    expect(screen.getByText(/Food\/bev split is approximate — pending category refinement/)).toBeInTheDocument();

    // The day-by-day list renders with driver chips: booked covers, school hols, public holiday
    // with its name, uni in session, and weather.
    const dayByDay = screen.getByTestId("day-by-day");
    expect(within(dayByDay).getByText("60 booked covers")).toBeInTheDocument();
    expect(within(dayByDay).getByText("120 estimated final covers")).toBeInTheDocument();
    expect(within(dayByDay).queryByText("135 booked covers")).not.toBeInTheDocument();
    expect(within(dayByDay).getByText("School holidays")).toBeInTheDocument();
    expect(within(dayByDay).getByText("Public holiday · Melbourne Cup")).toBeInTheDocument();
    expect(within(dayByDay).getByText("Uni in session")).toBeInTheDocument();
    expect(within(dayByDay).getByText("21° · 0 mm rain")).toBeInTheDocument();
  });

  it("shows feature importance and the beats-baseline banner on the Model tab when the model loses", async () => {
    mocks.api.getForecastRun.mockResolvedValue(losingRun);

    await renderLoaded();
    await runForecast();

    // Radix Tabs activate on mouse-down, not click.
    fireEvent.mouseDown(screen.getByRole("tab", { name: "Model" }), { button: 0 });

    expect(await screen.findByTestId("feature-importance-chart")).toBeInTheDocument();
    expect(screen.getByText(/XGBoost does not beat the seasonal-naive baseline/)).toBeInTheDocument();
    // The accuracy-vs-baseline table is present too.
    expect(screen.getByText("Accuracy vs baseline")).toBeInTheDocument();
    expect(screen.getByText(/these scores and feature importances do not measure TimesFM accuracy/)).toBeInTheDocument();
  });

  it("explains an unavailable split without displaying an empty split chart", async () => {
    mocks.api.getForecastRun.mockResolvedValue({
      ...succeededRun,
      series: succeededRun.series!.map((day) => ({ ...day, food: null, bev: null })),
    });
    await renderLoaded();
    await runForecast();
    expect(screen.getByText(/Food\/beverage split unavailable/)).toBeInTheDocument();
    expect(screen.queryByTestId("food-bev-split-chart")).not.toBeInTheDocument();
  });

  it("labels legacy fitted diagnostics and withholds the baseline endorsement", async () => {
    mocks.api.getForecastRun.mockResolvedValue({
      ...succeededRun,
      diagnostics: { ...diagnostics, validationMethod: null, diagnosticModel: null },
    });
    await renderLoaded();
    await runForecast();
    fireEvent.mouseDown(screen.getByRole("tab", { name: "Model" }), { button: 0 });
    expect(await screen.findByText(/Historical diagnostics use fitted predictions/)).toBeInTheDocument();
    expect(screen.queryByText(/XGBoost beats the seasonal-naive baseline/)).not.toBeInTheDocument();
  });

  it("shows weekday error and worst misses on the Backtest tab", async () => {
    await renderLoaded();
    await runForecast();

    fireEvent.mouseDown(screen.getByRole("tab", { name: "Backtest" }), { button: 0 });

    expect(await screen.findByTestId("weekday-error-chart")).toBeInTheDocument();
    expect(screen.getByText("Worst misses")).toBeInTheDocument();
    // The worst-miss row (2026-10-03) with its absolute percentage error.
    expect(screen.getByText("8.5%")).toBeInTheDocument();
  });

  it("renders the error message when a run fails", async () => {
    mocks.api.getForecastRun.mockResolvedValue({
      ...queuedRun,
      id: "fc-new",
      status: "FAILED",
      errorMessage: "No history for the target metric",
    });

    await renderLoaded();

    await act(async () => {
      fireEvent.click(screen.getByRole("button", { name: "Run forecast" }));
    });

    expect(await screen.findByText("No history for the target metric")).toBeInTheDocument();
  });

  it("loads a succeeded run into the tabs via the View button on the Runs tab", async () => {
    await renderLoaded();

    fireEvent.mouseDown(screen.getByRole("tab", { name: "Runs" }), { button: 0 });

    const runs = await screen.findByTestId("runs-list");
    fireEvent.click(within(runs).getByRole("button", { name: "View" }));

    // View fetches the full run and switches back to the Forecast tab with its charts.
    expect(await screen.findByTestId("forecast-vs-baseline-chart")).toBeInTheDocument();
    expect(mocks.api.getForecastRun).toHaveBeenCalledWith("fc-2");
  });

  it("opens a compare-to-actuals view from the Runs tab for a succeeded run", async () => {
    await renderLoaded();

    fireEvent.mouseDown(screen.getByRole("tab", { name: "Runs" }), { button: 0 });

    const runs = await screen.findByTestId("runs-list");
    fireEvent.click(within(runs).getByRole("button", { name: "Compare" }));

    expect(await screen.findByText("Compare to actuals")).toBeInTheDocument();
    expect(await screen.findByText("MAPE 4.1%")).toBeInTheDocument();
    expect(screen.getByText("MAE 512.4")).toBeInTheDocument();
    expect(screen.getByText("9,254")).toBeInTheDocument();
  });
});
