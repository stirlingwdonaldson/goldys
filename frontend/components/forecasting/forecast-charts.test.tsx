import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { ForecastVsBaselineChart } from "./forecast-vs-baseline-chart";
import { FoodBevSplitChart } from "./food-bev-split-chart";
import { FeatureImportanceChart } from "./feature-importance-chart";
import { BacktestChart } from "./backtest-chart";
import { ResidualChart } from "./residual-chart";
import { PredictedVsActualChart } from "./predicted-vs-actual-chart";
import { WeekdayErrorChart } from "./weekday-error-chart";
import type {
  BacktestRow,
  ForecastDayRow,
  ForecastImportanceRow,
  WeekdayErrorRow,
} from "@/lib/api/types";

/** A fully-populated forecast day; `overrides` drive the null-field cases. */
function day(date: string, overrides: Partial<ForecastDayRow> = {}): ForecastDayRow {
  return {
    date,
    median: 12000,
    p10: 10000,
    p90: 14000,
    food: 7000,
    bev: 5000,
    baseline: 11800,
    bookedCovers: 120,
    observedBookedCovers: 60,
    publicHoliday: false,
    publicHolidayEve: false,
    dayAfterPublicHoliday: false,
    schoolHoliday: false,
    uniInSession: true,
    publicHolidayName: null,
    weatherSource: null,
    serviceTempMaxC: 22,
    serviceRainMm: 0,
    ...overrides,
  };
}

/** A fully-populated backtest row; `overrides` drive the null-field cases. */
function backtestRow(date: string, overrides: Partial<BacktestRow> = {}): BacktestRow {
  return {
    date,
    day: null,
    actualNet: 12000,
    predictedNet: 11800,
    error: -200,
    absPctError: 1.7,
    actualFood: 7000,
    predictedFood: 6900,
    actualBev: 5000,
    predictedBev: 4900,
    baselineNet: 11500,
    ...overrides,
  };
}

function importanceRow(feature: string, importance: number): ForecastImportanceRow {
  return { feature, importance };
}

function weekdayRow(day: string, mape: number): WeekdayErrorRow {
  return { day, mape, n: 12, mae: 300, meanActual: 12000, meanPredicted: 11800 };
}

describe("ForecastVsBaselineChart", () => {
  it("renders with forecast rows", () => {
    render(
      <ForecastVsBaselineChart
        rows={[day("2026-10-06"), day("2026-10-07"), day("2026-10-08")]}
      />,
    );
    const chart = screen.getByTestId("forecast-vs-baseline-chart");
    expect(chart).toBeInTheDocument();
    expect(chart).toHaveAttribute("role", "img");
  });

  it("does not crash when the median and baseline are null", () => {
    render(
      <ForecastVsBaselineChart
        rows={[day("2026-10-06", { median: null, baseline: null })]}
      />,
    );
    expect(screen.getByTestId("forecast-vs-baseline-chart")).toBeInTheDocument();
  });
});

describe("FoodBevSplitChart", () => {
  it("renders with forecast rows", () => {
    render(
      <FoodBevSplitChart rows={[day("2026-10-06"), day("2026-10-07")]} />,
    );
    expect(screen.getByTestId("food-bev-split-chart")).toBeInTheDocument();
  });

  it("does not crash when food and bev are null", () => {
    render(<FoodBevSplitChart rows={[day("2026-10-06", { food: null, bev: null })]} />);
    expect(screen.getByTestId("food-bev-split-chart")).toBeInTheDocument();
  });
});

describe("FeatureImportanceChart", () => {
  it("renders with importance rows", () => {
    render(
      <FeatureImportanceChart
        rows={[
          importanceRow("reservations.covers", 0.42),
          importanceRow("labour.scheduled_hours", 0.31),
          importanceRow("serviceTempMaxC", 0.27),
        ]}
      />,
    );
    expect(screen.getByTestId("feature-importance-chart")).toBeInTheDocument();
  });

  it("skips null driver rows and renders an empty state when none remain", () => {
    render(<FeatureImportanceChart rows={[{ feature: null, importance: null }]} />);
    expect(screen.getByText("No feature importance")).toBeInTheDocument();
  });
});

describe("BacktestChart", () => {
  it("renders with backtest rows", () => {
    render(
      <BacktestChart
        rows={[backtestRow("2026-10-06"), backtestRow("2026-10-07"), backtestRow("2026-10-08")]}
      />,
    );
    expect(screen.getByTestId("backtest-chart")).toBeInTheDocument();
  });

  it("does not crash when actual, predicted and baseline are null", () => {
    render(
      <BacktestChart
        rows={[
          backtestRow("2026-10-06", {
            actualNet: null,
            predictedNet: null,
            baselineNet: null,
          }),
        ]}
      />,
    );
    expect(screen.getByTestId("backtest-chart")).toBeInTheDocument();
  });
});

describe("ResidualChart", () => {
  it("renders with backtest rows", () => {
    render(
      <ResidualChart
        rows={[
          backtestRow("2026-10-06", { error: -200 }),
          backtestRow("2026-10-07", { error: 150 }),
        ]}
      />,
    );
    expect(screen.getByTestId("residual-chart")).toBeInTheDocument();
  });

  it("does not crash when the error is null", () => {
    render(<ResidualChart rows={[backtestRow("2026-10-06", { error: null })]} />);
    expect(screen.getByTestId("residual-chart")).toBeInTheDocument();
  });
});

describe("PredictedVsActualChart", () => {
  it("renders with backtest rows", () => {
    render(
      <PredictedVsActualChart
        rows={[backtestRow("2026-10-06"), backtestRow("2026-10-07")]}
      />,
    );
    expect(screen.getByTestId("predicted-vs-actual-chart")).toBeInTheDocument();
  });

  it("skips rows with a null actual or predicted and renders an empty state when none remain", () => {
    render(
      <PredictedVsActualChart
        rows={[backtestRow("2026-10-06", { actualNet: null, predictedNet: null })]}
      />,
    );
    expect(screen.getByText("No comparison points")).toBeInTheDocument();
  });
});

describe("WeekdayErrorChart", () => {
  it("renders with weekday rows", () => {
    render(
      <WeekdayErrorChart
        rows={[
          weekdayRow("Monday", 3.1),
          weekdayRow("Tuesday", 2.4),
          weekdayRow("Wednesday", 4.8),
        ]}
      />,
    );
    expect(screen.getByTestId("weekday-error-chart")).toBeInTheDocument();
  });

  it("skips null day or mape rows and renders an empty state when none remain", () => {
    render(<WeekdayErrorChart rows={[{ day: null, mape: null, n: 0, mae: null, meanActual: null, meanPredicted: null }]} />);
    expect(screen.getByText("No weekday error data")).toBeInTheDocument();
  });
});
