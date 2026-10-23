"""FastAPI app exposing the self-hosted forecasting runtime.

``POST /forecast`` takes Goldy's daily internal series (net revenue, booked
covers, reservations, food/bev split) plus a horizon and quantiles, builds one
feature matrix across history + horizon, and returns:

  * ``forecast`` — the headline TimesFM net-revenue quantiles (median/p10/p90),
    split into food/bev by the XGBoost share model, with the per-day baseline and
    drivers (booked covers, holidays, school/uni, weather);
  * ``diagnostics`` — the XGBoost twin's holdout/CV accuracy, feature importance
    and backtest (null when the twin fails; the forecast is never lost).

``torch``/``timesfm`` are imported lazily inside :class:`~predictor.TimesFmPredictor`,
so this module imports cleanly without them. The module-level ``predictor`` is
swapped for a fake in tests.
"""

from __future__ import annotations

import datetime as dt
import logging
import math
import os
import threading
from contextlib import asynccontextmanager

import numpy as np
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel, Field, model_validator

from external import SCHOOL_COLUMNS, UNIVERSITY_COLUMNS, Config
from features import (
    PUBLIC_HOLIDAY_FEATURE_COLUMNS,
    TARGET_NET,
    TARGET_SHARE,
    WEATHER_FEATURE_COLUMNS,
    add_lag_features,
    prepare_matrix,
)
from forecast import backtest, backtest_summary, build_future_frame
from model import feature_importance, train
from predictor import Predictor, TimesFmPredictor

LOGGER = logging.getLogger(__name__)

MODEL_VERSION = "timesfm-3.0"

# Swapped for a FakePredictor in tests.
predictor: Predictor = TimesFmPredictor()

# The columns passed to TimesFM as ``past_future_covariates``: everything whose
# value is known for both the context and the horizon — the booked covers (with
# future maturity top-up) and the external drivers (holidays, school/uni, weather).
# Calendar features are inferred by TimesFM from the timestamps, and lag features
# are past-only for the target, so neither belongs here.
FUTURE_KNOWN_COLUMNS = (
    ["booked_covers"]
    + PUBLIC_HOLIDAY_FEATURE_COLUMNS
    + SCHOOL_COLUMNS
    + UNIVERSITY_COLUMNS
    + WEATHER_FEATURE_COLUMNS
)


def _prewarm_enabled() -> bool:
    return os.environ.get("TIMESFM_PREWARM", "false").strip().lower() in ("1", "true", "yes", "on")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Pre-download/load the model in a background thread so /health responds immediately
    # and the first forecast isn't stalled by the (multi-hundred-MB) weight download.
    # The cache volume persists the download across restarts.
    if _prewarm_enabled():
        threading.Thread(target=predictor.warmup, name="timesfm-prewarm", daemon=True).start()
    yield


app = FastAPI(title="TimesFM inference service", version=MODEL_VERSION, lifespan=lifespan)


class ForecastRequest(BaseModel):
    dates: list[str]
    net: list[float | None]
    booked_covers: list[float | None] | None = None
    bookings: list[float | None] | None = None
    food: list[float | None] | None = None
    bev: list[float | None] | None = None
    horizon: int = Field(gt=0)
    quantiles: list[str]

    @model_validator(mode="after")
    def _aligned_lengths(self) -> "ForecastRequest":
        n = len(self.dates)
        for name in ("net", "booked_covers", "bookings", "food", "bev"):
            values = getattr(self, name)
            if values is not None and len(values) != n:
                raise ValueError(f"{name} length must equal dates length")
        return self


class ForecastDay(BaseModel):
    date: str
    median: float | None = None
    p10: float | None = None
    p90: float | None = None
    food: float | None = None
    bev: float | None = None
    baseline: float | None = None
    bookedCovers: float | None = None
    observedBookedCovers: float | None = None
    publicHoliday: int | None = None
    publicHolidayName: str | None = None
    publicHolidayEve: int | None = None
    dayAfterPublicHoliday: int | None = None
    schoolHoliday: int | None = None
    uniInSession: int | None = None
    serviceTempMaxC: float | None = None
    serviceRainMm: float | None = None
    weatherSource: str | None = None


class ForecastResponse(BaseModel):
    forecast: list[ForecastDay]
    diagnostics: dict | None = None
    warnings: list[str] = Field(default_factory=list)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_VERSION, "warm": predictor.warm}


@app.post("/forecast")
def forecast(request: ForecastRequest) -> ForecastResponse:
    config = Config()
    internal = _build_internal(request)

    # One feature matrix across history + horizon: external drivers, calendar,
    # and the future booked-covers maturity top-up. Lags are added separately
    # (below) because the twin needs them and TimesFM's covariates do not.
    frame, cutoff = build_future_frame(internal, config, horizon=request.horizon)
    lagged = add_lag_features(frame, config)

    # The headline forecast: TimesFM net-revenue quantiles conditioned on the
    # future-known covariates. TimesFM is anchored at the last labelled net date
    # (``cutoff``): trailing null-net dates are the future book, not context.
    # Dropping them keeps the context length consistent with the covariate time
    # dimension and anchors ``forecast_dates`` at ``cutoff``, so the forecast
    # rows and the frame's future rows stay date-aligned.
    # (``build_future_frame`` above guarantees at least one labelled date.)
    cutoff_index = max(i for i, value in enumerate(request.net) if value is not None)
    history_dates = request.dates[: cutoff_index + 1]
    target = [_nan_if_none(value) for value in request.net[: cutoff_index + 1]]
    past_future = _future_known_covariates(frame)
    future_dates, quantiles = predictor.predict(
        history_dates,
        target,
        None,
        request.horizon,
        "D",
        request.quantiles,
        past_future_covariates=past_future,
    )

    median_values = quantiles.get("0.5", [])
    p10_values = quantiles.get("0.1", [])
    p90_values = quantiles.get("0.9", [])

    # The XGBoost twin is best-effort: its failure must never lose the forecast.
    diagnostics: dict | None = None
    shares: list[float | None] = [None] * len(future_dates)
    warnings: list[str] = []
    try:
        models = train(lagged, config)
        importance = feature_importance(models)
        bt = backtest(lagged, models)
        summary = backtest_summary(bt)
        shares = _split_future(frame, cutoff, models, median_values, config)
        diagnostics = _build_diagnostics(models, importance, bt, summary)
        warnings = list(models.warnings)
    except Exception as exc:  # noqa: BLE001 - the twin is a diagnostic, not the forecast
        LOGGER.warning("XGBoost twin failed; returning the TimesFM forecast only: %s", exc)
        warnings = [f"XGBoost twin failed: {exc}"]

    future_frame = lagged[lagged["date"] > cutoff].sort_values("date").reset_index(drop=True)
    rows = [
        _build_day(future_dates, i, future_frame, shares, median_values, p10_values, p90_values)
        for i in range(len(future_dates))
    ]

    return ForecastResponse(forecast=rows, diagnostics=diagnostics, warnings=warnings)


# ---------------------------------------------------------------------------
# Request -> internal daily frame
# ---------------------------------------------------------------------------


def _build_internal(request: ForecastRequest) -> pd.DataFrame:
    """The request's aligned series as the internal frame ``assemble_dataset`` /
    ``build_future_frame`` expect: ``date`` + ``net_revenue`` + ``food_share`` +
    ``food_revenue``/``bev_revenue`` + ``booked_covers`` (``bookings`` rides along
    unused — the count is not a feature in Goldy's)."""
    frame = pd.DataFrame({"date": pd.to_datetime(request.dates)})
    frame[TARGET_NET] = [_nan_if_none(value) for value in request.net]
    frame["booked_covers"] = (
        [_nan_if_none(value) for value in request.booked_covers]
        if request.booked_covers is not None
        else np.nan
    )
    frame["food_revenue"] = (
        [_nan_if_none(value) for value in request.food] if request.food is not None else np.nan
    )
    frame["bev_revenue"] = (
        [_nan_if_none(value) for value in request.bev] if request.bev is not None else np.nan
    )
    if request.bookings is not None:
        frame["bookings"] = [_nan_if_none(value) for value in request.bookings]

    # The share target is the food fraction of food+bev, both gross (inc-GST),
    # so it is internally consistent. food / net would be ~1.1x off once the
    # target is net (ex-GST), and net_forecast x share would not sum to net.
    food = frame["food_revenue"]
    bev = frame["bev_revenue"]
    denom = food + bev
    with np.errstate(divide="ignore", invalid="ignore"):
        frame[TARGET_SHARE] = (food / denom).where(denom.notna() & (denom != 0))
    return frame


# ---------------------------------------------------------------------------
# TimesFM future-known covariates
# ---------------------------------------------------------------------------


def _future_known_covariates(frame: pd.DataFrame) -> np.ndarray:
    """The future-known columns stacked as ``(n_features, n_timesteps)`` float32.

    Missing values are NaN, which TimesFM treats as an absent covariate.
    """
    columns = [c for c in FUTURE_KNOWN_COLUMNS if c in frame.columns]
    matrix = frame[columns].apply(pd.to_numeric, errors="coerce").to_numpy(dtype=np.float32)
    return matrix.T


# ---------------------------------------------------------------------------
# Food/bev share split (recursive, mirroring forecast.forecast)
# ---------------------------------------------------------------------------


def _split_future(
    frame: pd.DataFrame,
    cutoff: pd.Timestamp,
    models,
    net_medians: list[float],
    config: Config,
) -> list[float | None]:
    """Apply the share model to each future date's net median, writing ``food_share``
    (and net) back before the next day's share lags are computed, exactly as the
    reference's recursive forward pass does for the twin's own net forecast."""
    future_dates = frame.loc[frame["date"] > cutoff, "date"].sort_values()
    working = frame.copy()
    shares: list[float | None] = []
    for i, target_date in enumerate(future_dates):
        featured = add_lag_features(working, config)
        row = featured[featured["date"] == target_date]

        share: float | None = None
        if models.share_model is not None:
            share = float(
                models.share_model.predict(prepare_matrix(row, models.share_features))[0]
            )
            share = float(np.clip(share, 0.05, 0.95))

        net = net_medians[i] if i < len(net_medians) else float("nan")
        net = _nan_if_none(net)

        mask = working["date"] == target_date
        working.loc[mask, TARGET_NET] = net
        working.loc[mask, TARGET_SHARE] = share if share is not None else np.nan
        working.loc[mask, "food_revenue"] = net * share if share is not None else np.nan
        working.loc[mask, "bev_revenue"] = net * (1 - share) if share is not None else np.nan
        shares.append(share)
    return shares


# ---------------------------------------------------------------------------
# Response assembly
# ---------------------------------------------------------------------------


def _build_day(
    future_dates: list[str],
    index: int,
    future_frame: pd.DataFrame,
    shares: list[float | None],
    median_values: list[float],
    p10_values: list[float],
    p90_values: list[float],
) -> ForecastDay:
    row = future_frame.iloc[index] if index < len(future_frame) else None
    share = shares[index] if index < len(shares) else None

    median = _num(_at(median_values, index))
    p10 = _num(_at(p10_values, index))
    p90 = _num(_at(p90_values, index))

    food = _num(median * share) if median is not None and share is not None else None
    bev = _num(median * (1 - share)) if median is not None and share is not None else None

    return ForecastDay(
        date=future_dates[index],
        median=median,
        p10=p10,
        p90=p90,
        food=food,
        bev=bev,
        baseline=_num(_field(row, "net_revenue_dow_mean_4")),
        bookedCovers=_num(_field(row, "booked_covers")),
        observedBookedCovers=_num(_field(row, "observed_booked_covers")),
        publicHoliday=_flag(_field(row, "public_holiday")),
        publicHolidayName=_string(_field(row, "public_holiday_name")),
        publicHolidayEve=_flag(_field(row, "public_holiday_eve")),
        dayAfterPublicHoliday=_flag(_field(row, "day_after_public_holiday")),
        schoolHoliday=_flag(_field(row, "school_holiday")),
        uniInSession=_flag(_field(row, "uni_in_session")),
        serviceTempMaxC=_num(_field(row, "service_temp_max_c")),
        serviceRainMm=_num(_field(row, "service_rain_mm")),
        weatherSource=_string(_field(row, "weather_source")),
    )


_BACKTEST_RENAME = {
    "date": "date",
    "day": "day",
    "actual_net_revenue": "actualNet",
    "predicted_net_revenue": "predictedNet",
    "error": "error",
    "abs_pct_error": "absPctError",
    "actual_food_revenue": "actualFood",
    "predicted_food_revenue": "predictedFood",
    "actual_bev_revenue": "actualBev",
    "predicted_bev_revenue": "predictedBev",
    "baseline_net_revenue": "baselineNet",
}

_WEEKDAY_RENAME = {
    "day": "day",
    "n": "n",
    "mae": "mae",
    "mape": "mape",
    "mean_actual": "meanActual",
    "mean_predicted": "meanPredicted",
}


def _build_diagnostics(models, importance: pd.DataFrame, bt: pd.DataFrame, summary: dict) -> dict:
    metrics = models.metrics
    return {
        "modelVersion": MODEL_VERSION,
        "diagnosticModel": "xgboost",
        "validationMethod": "recursive-holdout-v1",
        "trainedRows": models.trained_rows,
        "reliable": models.reliable,
        "warnings": list(models.warnings),
        "features": list(models.features),
        "shareFeatures": list(models.share_features),
        "holdoutNetXgboost": _metrics_jsonable(metrics.get("holdout_net_xgboost")),
        "holdoutNetBaseline": _metrics_jsonable(metrics.get("holdout_net_baseline")),
        "holdoutFood": _metrics_jsonable(metrics.get("holdout_food")),
        "holdoutBev": _metrics_jsonable(metrics.get("holdout_bev")),
        "cvNetXgboost": _metrics_jsonable(metrics.get("cv_net_xgboost")),
        "cvNetBaseline": _metrics_jsonable(metrics.get("cv_net_baseline")),
        "beatsBaseline": _beats_baseline(metrics),
        "importance": _records(importance),
        "backtest": {
            "rows": _records(bt, _BACKTEST_RENAME),
            "byWeekday": _records(summary.get("by_weekday"), _WEEKDAY_RENAME),
            "residualMean": _jsonable(summary.get("residual_mean")),
            "residualStd": _jsonable(summary.get("residual_std")),
            "worstDays": _records(summary.get("worst_days"), _BACKTEST_RENAME),
        },
    }


def _beats_baseline(metrics: dict) -> bool | None:
    """Whether the twin beats the seasonal-naive baseline, preferring CV accuracy
    when it was computable and falling back to the holdout otherwise."""
    if "cv_net_xgboost" in metrics and "cv_net_baseline" in metrics:
        model_key, baseline_key = "cv_net_xgboost", "cv_net_baseline"
    elif "holdout_net_xgboost" in metrics and "holdout_net_baseline" in metrics:
        model_key, baseline_key = "holdout_net_xgboost", "holdout_net_baseline"
    else:
        return None

    model_mae = metrics[model_key].get("mae")
    baseline_mae = metrics[baseline_key].get("mae")
    if (
        model_mae is None
        or baseline_mae is None
        or (isinstance(model_mae, float) and math.isnan(model_mae))
        or (isinstance(baseline_mae, float) and math.isnan(baseline_mae))
    ):
        return None
    return model_mae <= baseline_mae


def _metrics_jsonable(metrics: dict | None) -> dict | None:
    if metrics is None:
        return None
    return {key: _jsonable(value) for key, value in metrics.items()}


def _records(frame: pd.DataFrame | None, rename: dict[str, str] | None = None) -> list[dict]:
    if frame is None or frame.empty:
        return []
    rename = rename or {}
    records = []
    for _, row in frame.iterrows():
        records.append({rename.get(col, col): _jsonable(row[col]) for col in frame.columns})
    return records


def _at(values: list, index: int):
    return values[index] if values is not None and index < len(values) else None


def _field(row, name: str):
    if row is None or name not in row.index:
        return None
    return row[name]


def _num(value) -> float | None:
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    return float(value)


def _flag(value) -> int | None:
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    return int(value)


def _string(value) -> str | None:
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    return str(value)


def _jsonable(value):
    if isinstance(value, (pd.Timestamp, dt.datetime, dt.date)):
        return pd.Timestamp(value).date().isoformat()
    if value is None:
        return None
    try:
        if pd.isna(value):
            return None
    except (TypeError, ValueError):
        pass
    if isinstance(value, (np.floating, np.integer, np.bool_)):
        return value.item()
    if isinstance(value, float):
        return None if math.isnan(value) else value
    if isinstance(value, (str, int, bool)):
        return value
    return value


def _nan_if_none(value: float | None) -> float:
    return float("nan") if value is None else value
