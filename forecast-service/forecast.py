"""Forward forecasting and backtest.

Forecasts are produced recursively: each predicted day is written back into
the series before the next day's lag features are computed, so a 14-day
horizon does not require lag features it cannot possibly know.

Book maturity: a future date's ``booked_covers`` is the book *right now*, which
is a fraction of what it will finally seat. ``estimate_pickup_curve`` measures
how full the book typically is at a given lead time and ``apply_maturity`` tops
the partial book up toward the same-weekday average, so the model predicts from
its best estimate of the settled book rather than a half-empty one.

This is a port of the reference pipeline's ``pipeline/forecast.py`` plus the
maturity logic from ``pipeline/opentable.py``, adapted to Goldy's
forecast-service:

  * ``booked_covers`` replaces the reference's ``bookings``/``ot_*`` columns.
  * Labour is deferred — there is no labour-budget output (``add_labour_budgets``
    is deliberately absent).
  * The OpenTable ``attach`` step is gone: ``booked_covers`` arrives in
    ``internal``, and maturity is applied directly in ``build_future_frame``.
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd

from external import Config, build_external_features
from features import (
    TARGET_NET,
    TARGET_SHARE,
    add_calendar_features,
    add_lag_features,
    prepare_matrix,
)
from model import TrainedModels, score

LOGGER = logging.getLogger(__name__)

# Covers columns that grow as the book fills, and so need scaling up on future
# dates. In Goldy's there is a single such column (no OpenTable sub-features).
MATURING_COLUMNS = ["booked_covers"]


# ---------------------------------------------------------------------------
# Booking maturity (the pickup curve)
# ---------------------------------------------------------------------------


def _exponential_maturity(lead: np.ndarray, floor: float, tau: float) -> np.ndarray:
    """m(L) = floor + (1 - floor) * exp(-L / tau); m(0) = 1, decreasing in L."""
    return floor + (1.0 - floor) * np.exp(-lead / tau)


def estimate_pickup_curve(
    daily: pd.DataFrame,
    snapshot_date: pd.Timestamp,
    *,
    max_lead: int = 60,
    column: str = "booked_covers",
) -> pd.Series:
    """Fraction of a date's eventual covers typically already booked ``lead``
    days out, indexed 0..max_lead.

    Measured by comparing each future date's observed covers against what that
    weekday finally averages in the settled history. A smooth two-parameter
    exponential ``floor + (1-floor)·exp(-L/tau)`` is fitted rather than binned
    medians, because a single snapshot gives one observation per lead time and
    the scatter is large; pooling every point into a monotone curve rests on all
    of them instead of one per bin.
    """
    flat = pd.Series(1.0, index=range(max_lead + 1))
    history = daily[daily["date"] < snapshot_date]
    future = daily[daily["date"] >= snapshot_date].copy()

    if history.empty or future.empty:
        LOGGER.warning(
            "Cannot estimate a pickup curve without both settled history and "
            "future bookings — assuming the book is always complete, which "
            "will bias longer-horizon forecasts low."
        )
        return flat

    settled_by_dow = history.groupby(history["date"].dt.dayofweek)[column].mean()
    future["lead"] = (future["date"] - snapshot_date).dt.days
    future["expected_final"] = future["date"].dt.dayofweek.map(settled_by_dow)
    future = future[
        (future["expected_final"] > 0) & future["lead"].between(0, max_lead)
    ]
    if len(future) < 5:
        LOGGER.warning(
            "Only %d future dates with bookings — too few to estimate a pickup "
            "curve. Falling back to the same-weekday average for future days.",
            len(future),
        )
        return flat

    ratio = (future[column] / future["expected_final"]).clip(0, 1.2).to_numpy()
    lead = future["lead"].to_numpy(dtype="float64")

    try:
        from scipy.optimize import curve_fit

        params, _ = curve_fit(
            _exponential_maturity,
            lead,
            ratio,
            p0=[0.05, 7.0],
            bounds=([0.0, 0.5], [0.6, 60.0]),
            maxfev=20000,
        )
        floor, tau = float(params[0]), float(params[1])
    except Exception as exc:  # scipy missing, NaN ratios, or the fit failed
        LOGGER.warning(
            "Pickup-curve fit failed (%s) — falling back to a fixed 7-day "
            "half-life, which is the shape usually seen in casual dining.",
            exc,
        )
        floor, tau = 0.15, 7.0

    index = np.arange(max_lead + 1)
    curve = pd.Series(
        _exponential_maturity(index.astype("float64"), floor, tau), index=index
    )
    return curve.clip(lower=0.02, upper=1.0)


def apply_maturity(
    daily: pd.DataFrame,
    snapshot_date: pd.Timestamp | None,
    curve: pd.Series | None = None,
    *,
    columns: list[str] | None = None,
) -> pd.DataFrame:
    """Top up partially-booked future dates with the covers still expected to
    arrive, using the additive pickup model from revenue management:

        estimate = observed + (1 - maturity) * weekday_average

    The bookings already on the book are taken as fact; the share still to come
    is estimated at the same-weekday average. At one day out maturity is ~1 and
    the observed number is used almost as-is; at fourteen days out maturity is
    ~0.2 and most of the estimate is the weekday average. Historical dates are
    untouched — their books are settled.
    """
    if snapshot_date is None or daily.empty:
        return daily

    columns = columns or [c for c in MATURING_COLUMNS if c in daily.columns]
    if curve is None:
        curve = estimate_pickup_curve(daily, snapshot_date)

    out = daily.copy()
    future_mask = out["date"] >= snapshot_date
    if not future_mask.any():
        return out

    history = out[~future_mask]
    lead = (out.loc[future_mask, "date"] - snapshot_date).dt.days
    maturity = lead.clip(0, curve.index.max()).map(curve).astype("float64")
    dow = out.loc[future_mask, "date"].dt.dayofweek

    for column in columns:
        # A topped-up book is fractional by nature; widen before assigning so
        # pandas does not silently truncate an int column.
        out[column] = out[column].astype("float64")
        settled_by_dow = history.groupby(history["date"].dt.dayofweek)[column].mean()
        fallback = dow.map(settled_by_dow).astype("float64")
        observed = out.loc[future_mask, column].astype("float64")
        expected_remaining = (1.0 - maturity) * fallback
        out.loc[future_mask, column] = (observed + expected_remaining).fillna(fallback)

    out["book_maturity"] = np.nan
    out.loc[future_mask, "book_maturity"] = maturity.to_numpy()
    out["book_maturity"] = out["book_maturity"].fillna(1.0)
    return out


def simulate_book_maturity(
    daily: pd.DataFrame, cutoff: pd.Timestamp, curve: pd.Series
) -> pd.DataFrame:
    """Degrade settled future books back to what they would have looked like
    from ``cutoff``, then re-apply the pickup top-up.

    This exists for the backtest. Every historical date has a *settled* book, so
    a naive backtest hands the model a complete book for days it is supposedly
    forecasting blind. Multiplying settled covers by m(lead) reconstructs a
    plausible partial book; ``apply_maturity`` then tops it back up exactly as
    it would live.
    """
    if daily.empty:
        return daily

    out = daily.copy()
    future_mask = out["date"] > cutoff
    if not future_mask.any():
        return out

    lead = (out.loc[future_mask, "date"] - cutoff).dt.days
    maturity = lead.clip(0, curve.index.max()).map(curve).astype("float64")

    for column in MATURING_COLUMNS:
        if column in out.columns:
            out[column] = out[column].astype("float64")
            out.loc[future_mask, column] = out.loc[future_mask, column] * maturity

    return apply_maturity(out, cutoff + pd.Timedelta(days=1), curve=curve)


# ---------------------------------------------------------------------------
# Future-frame assembly and the recursive forward pass
# ---------------------------------------------------------------------------


def _estimate_future_bookings(frame: pd.DataFrame, cutoff: pd.Timestamp) -> pd.Series:
    """Fill unknown future booked covers with the recent same-weekday average.

    Real bookings-on-the-book should be pasted into the data for future dates
    where known — they are a strong same-day signal.
    """
    history = frame[(frame["date"] <= cutoff) & frame["booked_covers"].notna()]
    if history.empty:
        return frame["booked_covers"]

    recent = history[history["date"] > cutoff - pd.Timedelta(days=56)]
    recent = recent if not recent.empty else history
    by_dow = recent.groupby(recent["date"].dt.dayofweek)["booked_covers"].mean()

    filled = frame["booked_covers"].copy()
    missing = filled.isna() & (frame["date"] > cutoff)
    filled.loc[missing] = frame.loc[missing, "date"].dt.dayofweek.map(by_dow)
    return filled


def build_future_frame(
    internal: pd.DataFrame,
    config: Config,
    *,
    horizon: int | None = None,
    force_refresh: bool = False,
    simulate_maturity: bool = False,
) -> tuple[pd.DataFrame, pd.Timestamp]:
    """History plus ``horizon`` future days, with external features attached and
    future booked covers topped up to their settled estimate.

    ``simulate_maturity`` is for the backtest only: it degrades the settled
    future book of the forecast window back to how full it would have been on
    ``cutoff``, so measured accuracy reflects what the pipeline can actually do
    live rather than what it could do with hindsight.
    """
    horizon = int(horizon or config.get("forecast.horizon_days", 14))
    # `cutoff` is the last day with actual revenue — not necessarily the last
    # row. `internal` may already carry future ``booked_covers`` (projected by
    # the reservations domain for dates still to come), which have no
    # ``net_revenue`` yet and must not be mistaken for history.
    labelled = internal.loc[internal[TARGET_NET].notna(), "date"]
    if labelled.empty:
        raise ValueError("Internal data has no actual revenue — nothing to model")
    cutoff = labelled.max()

    history_start = pd.Timestamp(config.get("data.history_start", "2025-01-01"))
    start = min(history_start, internal["date"].min())
    # Forecast exactly `horizon` days forward from the end of the internal
    # series. (The reference anchored this to `max(today, cutoff)` so its
    # lagging data still produced a forecast out to today; here `horizon`
    # means `horizon`, which is what the contract and the backtest expect.)
    end = cutoff + pd.Timedelta(days=horizon)

    spine = pd.DataFrame({"date": pd.date_range(start, end, freq="D")})
    frame = spine.merge(internal, on="date", how="left")

    external = build_external_features(config, start, end, force_refresh=force_refresh)
    frame = frame.merge(external, on="date", how="left")

    frame = add_calendar_features(frame)

    # Preserve the actual snapshot separately: maturity-adjusted covers are an
    # estimate and must never be displayed as reservations already on the book.
    frame["observed_booked_covers"] = frame["booked_covers"]

    # The snapshot is taken now, not at the last revenue date (which may be stale).
    # Missing books still receive the weekday fallback below; the observed snapshot
    # remains missing rather than being relabelled as an actual booking count.
    today = pd.Timestamp.now(
        tz=config.get("venue.timezone", "Australia/Melbourne")
    ).normalize().tz_localize(None)
    snapshot = max(today, cutoff + pd.Timedelta(days=1))
    curve = estimate_pickup_curve(frame, snapshot)
    if simulate_maturity:
        frame = simulate_book_maturity(frame, cutoff, curve)
    else:
        frame = apply_maturity(frame, snapshot, curve=curve)

    # Any future date with nothing on the book still falls back to the
    # same-weekday average, exactly as before.
    frame["booked_covers"] = _estimate_future_bookings(frame, cutoff)
    frame = frame[frame["date"] >= internal["date"].min()].reset_index(drop=True)
    return frame, cutoff


def forecast(
    internal: pd.DataFrame,
    models: TrainedModels,
    config: Config,
    *,
    horizon: int | None = None,
    force_refresh: bool = False,
    feature_sink: list[dict] | None = None,
    simulate_maturity: bool = False,
) -> pd.DataFrame:
    """Forecast forward from the end of ``internal``.

    ``feature_sink``, when given, is filled with the exact feature vector the
    model saw for each forecast day. Nothing in the maths depends on it; it
    exists so the web UI can show why a given day was predicted as it was.
    """
    frame, cutoff = build_future_frame(
        internal,
        config,
        horizon=horizon,
        force_refresh=force_refresh,
        simulate_maturity=simulate_maturity,
    )

    future_dates = frame.loc[frame["date"] > cutoff, "date"].sort_values()
    if future_dates.empty:
        LOGGER.warning("No future dates to forecast")
        return pd.DataFrame()

    working = frame.copy()
    predictions: list[dict] = []

    for target_date in future_dates:
        featured = add_lag_features(working, config)
        row = featured[featured["date"] == target_date]

        net = float(
            np.expm1(models.net_model.predict(prepare_matrix(row, models.features))[0])
        )
        net = max(net, 0.0)

        share = float(
            models.share_model.predict(prepare_matrix(row, models.share_features))[0]
        )
        share = float(np.clip(share, 0.05, 0.95))

        # Write the prediction back so the next day's lags can use it.
        mask = working["date"] == target_date
        working.loc[mask, TARGET_NET] = net
        working.loc[mask, TARGET_SHARE] = share
        working.loc[mask, "food_revenue"] = net * share
        working.loc[mask, "bev_revenue"] = net * (1 - share)

        if feature_sink is not None:
            vector = prepare_matrix(row, models.features).iloc[0]
            feature_sink.append(
                {
                    "date": target_date.strftime("%Y-%m-%d"),
                    "features": {
                        name: (None if pd.isna(value) else float(value))
                        for name, value in vector.items()
                    },
                }
            )

        baseline = row["net_revenue_dow_mean_4"].to_numpy(dtype=float)
        predictions.append(
            {
                "date": target_date,
                "day": target_date.day_name(),
                "forecast_net_revenue": net,
                "forecast_food_revenue": net * share,
                "forecast_bev_revenue": net * (1 - share),
                "forecast_food_share": share,
                "baseline_net_revenue": (
                    float(baseline[0]) if baseline.size and not np.isnan(baseline[0])
                    else np.nan
                ),
                "bookings_used": row["booked_covers"].to_numpy(dtype=float)[0]
                if row["booked_covers"].notna().any()
                else np.nan,
                "public_holiday": row["public_holiday"].to_numpy()[0],
                "public_holiday_name": row["public_holiday_name"].to_numpy()[0],
                "school_holiday": row["school_holiday"].to_numpy()[0],
                "uni_in_session": row["uni_in_session"].to_numpy()[0],
                "service_temp_max_c": row["service_temp_max_c"].to_numpy()[0],
                "service_rain_mm": row["service_rain_mm"].to_numpy()[0],
                "weather_source": row["weather_source"].to_numpy()[0],
            }
        )

    return pd.DataFrame(predictions)


# ---------------------------------------------------------------------------
# Backtest (held-out recursive XGBoost predictions)
# ---------------------------------------------------------------------------


def backtest(frame: pd.DataFrame, models: TrainedModels) -> pd.DataFrame:
    """Only held-out recursive predictions, never fitted training predictions."""
    return models.validation_rows.copy()


def backtest_summary(backtest: pd.DataFrame) -> dict:
    """Headline error stats plus a by-weekday breakdown and the worst misses."""
    if backtest.empty:
        return {}

    actual = backtest["actual_net_revenue"].astype(float)
    predicted = backtest["predicted_net_revenue"].astype(float)
    baseline = backtest["baseline_net_revenue"].astype(float)

    def _score(values: pd.Series) -> dict[str, float]:
        return score(actual, values).as_dict()

    by_day = (
        backtest.assign(abs_error=(predicted - actual).abs())
        .groupby("day", dropna=False)
        .agg(
            n=("actual_net_revenue", "size"),
            mae=("abs_error", "mean"),
            mape=("abs_pct_error", "mean"),
            mean_actual=("actual_net_revenue", "mean"),
            mean_predicted=("predicted_net_revenue", "mean"),
        )
        .reset_index()
    )

    order = [
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday",
        "Sunday",
    ]
    by_day["sort"] = by_day["day"].map({name: i for i, name in enumerate(order)})
    by_day = by_day.sort_values("sort").drop(columns="sort")

    residuals = (predicted - actual).dropna()
    return {
        "fitted": _score(predicted),
        "baseline": _score(baseline),
        "by_weekday": by_day,
        "residual_mean": float(residuals.mean()) if len(residuals) else None,
        "residual_std": float(residuals.std()) if len(residuals) else None,
        "worst_days": backtest.reindex(
            backtest["abs_pct_error"].sort_values(ascending=False).index
        ).head(10),
    }
