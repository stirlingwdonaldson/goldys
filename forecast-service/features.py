"""Feature engineering: join internal and external data, add calendar and
lag features, and declare which columns the model is allowed to see.

Leakage rules enforced here:

  * Same-day labour cost is never a feature. Labour is *entirely deferred* —
    there are no labour columns at all in this service (decision I).
  * Lags and rolling windows are always shifted, never centred.
  * ``booked_covers`` IS allowed same-day: it is on the book before service.

This is a port of the reference pipeline's ``pipeline/features.py``, adapted
to Goldy's forecast-service:

  * ``Config`` comes from :mod:`external` (env-backed, not ``config.yaml``).
  * The OpenTable block is gone (there is no ``opentable`` module here, and
    ``booked_covers`` replaces the reference's ``bookings``/``ot_*`` columns).
  * Labour lags are gone (labour is deferred).
  * Macro features are gone (near-useless at daily grain; out of scope).
"""

from __future__ import annotations

import logging

import numpy as np
import pandas as pd

from external import (
    SCHOOL_COLUMNS,
    UNIVERSITY_COLUMNS,
    WEATHER_COLUMNS,
    Config,
    build_external_features,
)

LOGGER = logging.getLogger(__name__)

TARGET_NET = "net_revenue"
TARGET_SHARE = "food_share"

CALENDAR_FEATURES = [
    "day_of_week",
    "is_weekend",
    "is_friday",
    "is_saturday",
    "day_of_month",
    "month",
    "week_of_year_sin",
    "week_of_year_cos",
    "days_to_month_end",
]

CATEGORICAL_FEATURES = ["day_of_week", "month"]

# The holiday columns that are actually features. ``public_holiday_name`` is a
# display string, not a signal, so it is deliberately excluded.
PUBLIC_HOLIDAY_FEATURE_COLUMNS = [
    "public_holiday",
    "public_holiday_eve",
    "day_after_public_holiday",
]

# ``weather_source`` is a string label ("observed"/"forecast"), not a feature.
WEATHER_FEATURE_COLUMNS = [c for c in WEATHER_COLUMNS if c != "weather_source"]


# ---------------------------------------------------------------------------
# Calendar
# ---------------------------------------------------------------------------


def add_calendar_features(frame: pd.DataFrame) -> pd.DataFrame:
    result = frame.copy()
    dates = pd.to_datetime(result["date"])

    result["day_of_week"] = dates.dt.dayofweek.astype("int16")
    result["is_weekend"] = dates.dt.dayofweek.isin([4, 5, 6]).astype("int8")
    result["is_friday"] = (dates.dt.dayofweek == 4).astype("int8")
    result["is_saturday"] = (dates.dt.dayofweek == 5).astype("int8")
    result["day_of_month"] = dates.dt.day.astype("int16")
    result["month"] = dates.dt.month.astype("int16")

    # Smooth annual seasonality without giving the tree 365 splits to find.
    day_of_year = dates.dt.dayofyear
    angle = 2 * np.pi * day_of_year / 365.25
    result["week_of_year_sin"] = np.sin(angle)
    result["week_of_year_cos"] = np.cos(angle)

    result["days_to_month_end"] = (dates.dt.days_in_month - dates.dt.day).astype("int16")
    return result


# ---------------------------------------------------------------------------
# Lags
# ---------------------------------------------------------------------------


def add_lag_features(frame: pd.DataFrame, config: Config) -> pd.DataFrame:
    """Add lag and rolling features. Assumes `frame` is sorted by date with
    one row per calendar day (gaps reindexed by `assemble_dataset`)."""
    result = frame.sort_values("date").copy()

    lags = list(config.get("features.revenue_lags", [7, 14]))
    windows = list(config.get("features.revenue_rolling_windows", [7, 28]))

    for lag in lags:
        result[f"net_revenue_lag_{lag}"] = result[TARGET_NET].shift(lag)

    for window in windows:
        # shift(1) first so the window never includes the day being predicted.
        shifted = result[TARGET_NET].shift(1)
        result[f"net_revenue_roll_mean_{window}"] = shifted.rolling(
            window, min_periods=max(2, window // 4)
        ).mean()
        result[f"food_share_roll_mean_{window}"] = (
            result[TARGET_SHARE].shift(1).rolling(window, min_periods=2).mean()
        )

    # Same weekday, averaged over the last 4 occurrences — the single most
    # predictive feature in a venue with a hard weekly rhythm.
    result["net_revenue_dow_mean_4"] = (
        result.groupby("day_of_week")[TARGET_NET]
        .transform(lambda s: s.shift(1).rolling(4, min_periods=1).mean())
    )
    result["food_share_dow_mean_4"] = (
        result.groupby("day_of_week")[TARGET_SHARE]
        .transform(lambda s: s.shift(1).rolling(4, min_periods=1).mean())
    )

    # Labour is fully deferred (decision I): no lagged-labour features, no
    # same-day labour. There is no labour column to leak.
    return result


# ---------------------------------------------------------------------------
# Assembly
# ---------------------------------------------------------------------------


def assemble_dataset(
    internal: pd.DataFrame,
    config: Config,
    *,
    end: pd.Timestamp | None = None,
    force_refresh: bool = False,
) -> pd.DataFrame:
    """Build the full daily frame: internal actuals + external features +
    calendar + lags, spanning history through `end` (default: last actual)."""
    if internal.empty:
        raise ValueError("Internal data is empty — nothing to model")

    history_start = pd.Timestamp(config.get("data.history_start", "2025-01-01"))
    start = min(history_start, internal["date"].min())
    end = pd.Timestamp(end) if end is not None else internal["date"].max()
    end = max(end, internal["date"].max())

    spine = pd.DataFrame({"date": pd.date_range(start, end, freq="D")})
    frame = spine.merge(internal, on="date", how="left")

    external = build_external_features(config, start, end, force_refresh=force_refresh)
    frame = frame.merge(external, on="date", how="left")

    frame = add_calendar_features(frame)
    frame = add_lag_features(frame, config)

    # Rows before trading data begins carry no signal and would otherwise
    # dominate the training set with NaNs.
    frame = frame[frame["date"] >= internal["date"].min()].reset_index(drop=True)
    return frame


# ---------------------------------------------------------------------------
# Feature selection
# ---------------------------------------------------------------------------


def booked_covers_coverage(frame: pd.DataFrame) -> float:
    """Fraction of days with actual revenue that also have a booked-covers figure."""
    if "booked_covers" not in frame.columns or TARGET_NET not in frame.columns:
        return 0.0
    labelled = frame[frame[TARGET_NET].notna()]
    if labelled.empty:
        return 0.0
    return float(labelled["booked_covers"].notna().mean())


def _booked_covers_enabled(frame: pd.DataFrame, config: Config) -> bool:
    """Booked covers are the strongest non-calendar signal available, but only
    once they actually exist. On a mostly-empty column XGBoost splits on the
    handful of populated days and generalises the result to every other day,
    which measurably hurts.

    So the switch is three-way:

      features.include_booked_covers: true   -> use whenever coverage clears the bar
      features.include_booked_covers: false  -> never use
      features.include_booked_covers: force  -> always use, however sparse

    The coverage bar means this turns itself back on as history is backfilled,
    with no config edit needed.
    """
    setting = config.get("features.include_booked_covers", True)
    if isinstance(setting, str) and setting.lower() == "force":
        return True
    if not setting:
        return False

    threshold = float(config.get("features.booked_covers_min_coverage", 0.45))
    coverage = booked_covers_coverage(frame)
    if coverage >= threshold:
        return True

    LOGGER.warning(
        "Booked covers present on only %.1f%% of trading days (need %.0f%% to be "
        "useful) — excluding them from the model. Backfill the booked_covers "
        "column and this switches itself on. Override with "
        "features.include_booked_covers: force.",
        coverage * 100,
        threshold * 100,
    )
    return False


def feature_columns(frame: pd.DataFrame, config: Config) -> list[str]:
    """Columns the model may use. Anything not listed here is invisible to it."""
    columns = list(CALENDAR_FEATURES)
    columns += PUBLIC_HOLIDAY_FEATURE_COLUMNS

    if config.get("features.include_school", True):
        columns += list(SCHOOL_COLUMNS)

    if config.get("features.include_university", True):
        columns += list(UNIVERSITY_COLUMNS)

    if config.get("features.include_weather", True):
        columns += WEATHER_FEATURE_COLUMNS

    if _booked_covers_enabled(frame, config):
        columns.append("booked_covers")

    columns += [c for c in frame.columns if "_lag_" in c or "_roll_" in c]
    columns += [c for c in frame.columns if c.endswith("_dow_mean_4")]

    present = [c for c in dict.fromkeys(columns) if c in frame.columns]

    # Explicit exclusions from config. Every extra feature is another axis a
    # tree can split on by chance; on ~380 rows that costs real accuracy.
    excluded = set(config.get("features.exclude", []) or [])
    unknown = excluded - set(present)
    if unknown:
        LOGGER.debug(
            "features.exclude lists unknown columns: %s", ", ".join(sorted(unknown))
        )
    present = [c for c in present if c not in excluded]

    # Drop columns that are entirely missing — they add nothing and make
    # feature-importance output confusing.
    usable = [c for c in present if frame[c].notna().any()]
    dropped = sorted(set(present) - set(usable))
    if dropped:
        LOGGER.info("Dropping all-null features: %s", ", ".join(dropped))

    # Drop constants: a column with one value carries no information but
    # still shows up in feature importance and confuses the reader.
    labelled = frame[frame[TARGET_NET].notna()] if TARGET_NET in frame else frame
    constant = [c for c in usable if labelled[c].nunique(dropna=True) <= 1]
    if constant:
        LOGGER.info("Dropping constant features: %s", ", ".join(sorted(constant)))
        usable = [c for c in usable if c not in constant]

    # No labour columns are ever added, so none can leak through here.
    return usable


def prepare_matrix(frame: pd.DataFrame, columns: list[str]) -> pd.DataFrame:
    """Coerce the feature matrix to numeric float. XGBoost handles the NaNs."""
    matrix = frame[columns].copy()
    for column in matrix.columns:
        matrix[column] = pd.to_numeric(matrix[column], errors="coerce")
    return matrix.astype("float64")
