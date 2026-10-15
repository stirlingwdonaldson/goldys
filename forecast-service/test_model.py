"""Tests for ``model.py`` — the diagnostic XGBoost twin.

Two behaviours the refactor depends on:

1. ``feature_importance`` returns one row per feature sorted by importance,
   descending (gain from the net-revenue model).
2. ``train`` always reports a chronological holdout for both the XGBoost model
   and the seasonal-naive baseline, so the caller can judge whether the model
   is earning its keep.

No test touches the network: ``train`` only needs a synthetic daily frame with
calendar and lag features already applied.
"""

import numpy as np
import pandas as pd

import features as features_module
import model as model_module
from external import Config


def _frame(n: int = 160) -> pd.DataFrame:
    """A synthetic daily frame with a strong weekly rhythm.

    ``net_revenue`` follows a weekday pattern plus light noise, ``food_share`` is
    already clipped, and ``food_revenue``/``bev_revenue`` are derived so the
    holdout metrics have real targets. ``n`` is above ``min_rows_for_xgboost``
    (120) so the result is ``reliable``.
    """
    dates = pd.date_range("2026-01-05", periods=n, freq="D")
    rng = np.random.RandomState(0)
    dow = dates.dayofweek
    net = 1200.0 + 400.0 * np.sin(2 * np.pi * dow / 7) + rng.normal(0, 60, n)
    share = np.clip(
        0.5 + 0.06 * np.sin(2 * np.pi * dow / 7) + rng.normal(0, 0.02, n),
        0.05,
        0.95,
    )
    frame = pd.DataFrame(
        {
            "date": dates,
            "net_revenue": net,
            "food_share": share,
            "food_revenue": net * share,
            "bev_revenue": net * (1 - share),
            "booked_covers": 80.0
            + 25.0 * np.sin(2 * np.pi * dow / 7)
            + rng.normal(0, 6, n),
        }
    )
    frame = features_module.add_calendar_features(frame)
    frame = features_module.add_lag_features(frame, Config())
    return frame


def test_importanceIsSortedDescending():
    models = model_module.train(_frame(), Config())

    importance = model_module.feature_importance(models)

    assert not importance.empty
    assert list(importance.columns) == ["feature", "importance"]
    values = importance["importance"].to_numpy(dtype=float)
    assert (values[:-1] >= values[1:]).all()


def test_holdoutMetricsCompareToBaseline():
    models = model_module.train(_frame(), Config())

    assert "holdout_net_xgboost" in models.metrics
    assert "holdout_net_baseline" in models.metrics

    for key in ("holdout_net_xgboost", "holdout_net_baseline"):
        metrics = models.metrics[key]
        assert {"mae", "rmse", "mape", "n"} <= set(metrics)
