"""Tests for ``forecast.py`` — the recursive forward forecast, its baseline,
the backtest summary, and the book-maturity top-up.

Three behaviours the refactor depends on:

1. An empty future book (``booked_covers = 0``) is topped up to the same-weekday
   average by the additive maturity model — ``bookings_used`` is never 0.
2. ``baseline_net_revenue`` on a forecast day is the mean of the last four
   same-weekdays in the history.
3. ``backtest_summary`` emits a ``worst_days`` frame ordered by ``abs_pct_error``
   descending.

None of these touch the network: ``build_external_features`` is monkeypatched to
a schema-only stub, and the models are stubs (the maths under test is the
recursive pass, the baseline, and the maturity top-up, not XGBoost itself).
"""

import numpy as np
import pandas as pd
import pytest

import forecast as forecast_module
from external import (
    PUBLIC_HOLIDAY_COLUMNS,
    SCHOOL_COLUMNS,
    UNIVERSITY_COLUMNS,
    WEATHER_COLUMNS,
    Config,
)
from model import TrainedModels
from model import evaluate_window


class _StubModel:
    """A model whose ``predict`` returns a constant (log-space for net)."""

    def __init__(self, value: float) -> None:
        self.value = value

    def predict(self, matrix: pd.DataFrame) -> np.ndarray:
        return np.full(len(matrix), self.value)


def _stub_models() -> TrainedModels:
    return TrainedModels(
        net_model=_StubModel(np.log1p(1000.0)),
        share_model=_StubModel(0.5),
        features=["day_of_week"],
        share_features=["day_of_week"],
    )


def _stub_external(config, start, end, force_refresh=False) -> pd.DataFrame:
    """Schema-only stand-in for ``build_external_features`` (no network)."""
    columns = (
        PUBLIC_HOLIDAY_COLUMNS + SCHOOL_COLUMNS + UNIVERSITY_COLUMNS + WEATHER_COLUMNS
    )
    frame = pd.DataFrame({"date": pd.date_range(start, end, freq="D")})
    for column in columns:
        frame[column] = pd.NA
    return frame


def _history_frame(n: int = 70) -> pd.DataFrame:
    """Deterministic history with a weekday-only rhythm in revenue and covers."""
    dates = pd.date_range("2026-01-05", periods=n, freq="D")
    dow = dates.dayofweek
    net = 1000.0 + 200.0 * np.sin(2 * np.pi * dow / 7)
    share = np.clip(0.5 + 0.04 * np.sin(2 * np.pi * dow / 7), 0.05, 0.95)
    return pd.DataFrame(
        {
            "date": dates,
            "net_revenue": net,
            "food_share": share,
            "food_revenue": net * share,
            "bev_revenue": net * (1 - share),
            "booked_covers": 50.0 + 10.0 * dow,
        }
    )


def test_emptyFutureBookTopsUpToWeekdayAverage():
    # Four weeks of settled covers where every weekday seats a constant number
    # (Mondays = 50, Tuesdays = 60, …), then a future Monday with an empty book.
    history = pd.date_range("2026-01-05", periods=28, freq="D")
    daily = pd.DataFrame(
        {"date": history, "booked_covers": (50.0 + 10.0 * history.dayofweek)}
    )
    snapshot = history[-1]  # Sunday 2026-02-01, settled
    future = pd.Timestamp("2026-02-02")  # Monday, nobody booked yet
    daily = pd.concat(
        [daily, pd.DataFrame({"date": [future], "booked_covers": [0.0]})],
        ignore_index=True,
    )

    # m(0) = 1 (settled), m(1..) = 0 (book entirely immature one day out), so
    # the additive top-up is observed(0) + (1 - 0) * weekday_average.
    curve = pd.Series(1.0, index=range(61))
    curve.iloc[1:] = 0.0

    out = forecast_module.apply_maturity(daily, snapshot, curve)
    topped_up = out.loc[out["date"] == future, "booked_covers"].iloc[0]

    # Mondays settle at 50 covers; the topped-up book is 50, never 0.
    assert topped_up == pytest.approx(50.0)


def test_baselineIsMeanOfLastFourSameWeekdays(monkeypatch):
    internal = _history_frame(70)
    models = _stub_models()
    monkeypatch.setattr(forecast_module, "build_external_features", _stub_external)

    out = forecast_module.forecast(internal, models, Config(), horizon=1)

    assert len(out) == 1
    row = out.iloc[0]

    future_date = internal["date"].max() + pd.Timedelta(days=1)
    dow = future_date.dayofweek
    expected = (
        internal[internal["date"].dt.dayofweek == dow]["net_revenue"].tail(4).mean()
    )
    assert row["baseline_net_revenue"] == pytest.approx(expected)


def test_backtestEmitsWorstDays():
    frame = _history_frame(150)
    frame = forecast_module.add_calendar_features(frame)
    frame = forecast_module.add_lag_features(frame, Config())
    models = _stub_models()
    models.validation_rows = evaluate_window(frame.iloc[:110], frame.iloc[110:], Config())
    backtest = forecast_module.backtest(frame, models)
    summary = forecast_module.backtest_summary(backtest)

    assert "by_weekday" in summary
    assert "residual_mean" in summary
    assert "residual_std" in summary

    worst = summary["worst_days"]
    assert not worst.empty
    errors = worst["abs_pct_error"].to_numpy(dtype=float)
    assert (errors[:-1] >= errors[1:]).all()


def test_future_frame_preserves_observed_book_separately(monkeypatch):
    internal = _history_frame()
    future = internal["date"].max() + pd.Timedelta(days=1)
    internal = pd.concat([internal, pd.DataFrame({"date": [future], "booked_covers": [np.nan]})], ignore_index=True)
    monkeypatch.setattr(forecast_module, "build_external_features", _stub_external)
    frame, _ = forecast_module.build_future_frame(internal, Config(), horizon=1)
    row = frame[frame.date == future].iloc[0]
    assert pd.isna(row.observed_booked_covers)
    assert row.booked_covers > 0
