"""Tests for ``features.py`` — the feature matrix and its leakage rules.

Four behaviours the refactor depends on:

1. Lag features are shifted, never centred: ``net_revenue_lag_7[i]`` equals
   ``net_revenue[i - 7]`` and is NaN for the first seven rows.
2. Rolling windows are shifted first, so the window never includes the day
   being predicted.
3. ``net_revenue_dow_mean_4`` is the mean of the prior four same-weekdays.
4. No labour column is ever emitted (labour is fully deferred).

None of these tests touch the network: they build a small daily frame and
exercise the pure feature builders directly.
"""

import pandas as pd
import pytest

import features as features_module
from external import Config


def _base_frame() -> pd.DataFrame:
    """One row per day over five weeks (2026-01-05 is a Monday).

    ``net_revenue`` is ``100 + row_index`` so every day is unique, which makes
    the shifted-lag and same-weekday expectations unambiguous.
    """
    dates = pd.date_range("2026-01-05", periods=35, freq="D")
    return pd.DataFrame(
        {
            "date": dates,
            "net_revenue": [100 + i for i in range(35)],
            "food_share": [0.5] * 35,
            "booked_covers": [40 + i for i in range(35)],
        }
    )


def _lagged_frame() -> pd.DataFrame:
    return features_module.add_lag_features(
        features_module.add_calendar_features(_base_frame()), Config()
    )


def test_lagsAreShiftedNotCentred():
    frame = _lagged_frame()

    revenue = frame["net_revenue"].to_numpy()
    lag = frame["net_revenue_lag_7"].to_numpy()

    # No history for the first seven rows.
    assert pd.isna(lag[:7]).all()
    # Every later row is exactly the value seven days earlier, never a
    # centred (future-looking) value.
    for i in range(7, len(frame)):
        assert lag[i] == revenue[i - 7]


def test_rollingWindowExcludesThePredictedDay():
    frame = _lagged_frame()

    # Day 7 is the first row with a full 7-day window of history (days 0..6).
    # The rolling mean at day 7 must be the mean of days 0..6 and must not
    # include day 7's own value.
    prior = frame["net_revenue"].iloc[0:7]
    assert frame["net_revenue_roll_mean_7"].iloc[7] == pytest.approx(prior.mean())

    # Make the exclusion unmistakable: spike day 7's revenue and confirm the
    # rolling mean is unchanged — the spike is the predicted day, so it must
    # be invisible to the window.
    spiked = _base_frame()
    spiked.loc[7, "net_revenue"] = 99999.0
    spiked = features_module.add_lag_features(
        features_module.add_calendar_features(spiked), Config()
    )
    assert spiked["net_revenue_roll_mean_7"].iloc[7] == pytest.approx(prior.mean())


def test_dowMean4IsMeanOfPriorFourSameWeekdays():
    frame = _lagged_frame()

    # Mondays fall at indices 0, 7, 14, 21, 28 (2026-01-05 is a Monday), with
    # net_revenue 100, 107, 114, 121, 128.
    mondays = frame[frame["day_of_week"] == 0]["net_revenue"].to_numpy()
    assert list(mondays) == [100, 107, 114, 121, 128]

    dow_mean = frame["net_revenue_dow_mean_4"].to_numpy()

    # 1st Monday: no prior same-weekday, so it is NaN.
    assert pd.isna(dow_mean[0])
    # 2nd Monday: mean of the one prior Monday.
    assert dow_mean[7] == pytest.approx(100.0)
    # 5th Monday: mean of the prior four Mondays.
    assert dow_mean[28] == pytest.approx((100 + 107 + 114 + 121) / 4)

    # Same rule for the food-share baseline (all four prior Mondays == 0.5).
    share_mean = frame["food_share_dow_mean_4"].to_numpy()
    assert pd.isna(share_mean[0])
    assert share_mean[28] == pytest.approx(0.5)


def test_noLabourColumnsAreEmitted():
    frame = _lagged_frame()
    # Labour is fully deferred, but plant the columns anyway so the test
    # proves feature_columns actively excludes them rather than merely having
    # nothing to emit.
    frame["foh_labour_cost"] = 1000.0
    frame["boh_labour_cost"] = 800.0

    columns = features_module.feature_columns(frame, Config())

    assert [c for c in columns if "labour" in c] == []
