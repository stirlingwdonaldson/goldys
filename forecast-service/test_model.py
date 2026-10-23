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


def test_validation_predictions_do_not_depend_on_future_actuals_or_snapshots():
    frame = _frame()
    frame["service_temp_max_c"] = np.arange(len(frame)) % 15 + 15
    history, future = frame.iloc[:120].copy(), frame.iloc[120:134].copy()
    original = model_module.evaluate_window(history, future, Config())
    poisoned = future.copy()
    poisoned["net_revenue"] *= 100
    poisoned["food_share"] = 0.99
    poisoned["food_revenue"] *= 100
    poisoned["bev_revenue"] *= 100
    poisoned["booked_covers"] *= 100
    poisoned["service_temp_max_c"] *= 100
    changed = model_module.evaluate_window(history, poisoned, Config())
    for column in ("predicted_net_revenue", "predicted_food_revenue", "predicted_bev_revenue", "baseline_net_revenue"):
        np.testing.assert_allclose(original[column], changed[column])
    assert not original["actual_net_revenue"].equals(changed["actual_net_revenue"])


def test_backtest_contains_only_held_out_dates():
    frame = _frame()
    models = model_module.train(frame, Config())
    assert models.validation_rows["date"].min() > frame.iloc[119]["date"]
    assert len(models.validation_rows) == 40


def test_sparse_classification_does_not_produce_a_food_split():
    frame = _frame()
    frame.loc[frame.index[10:], "food_share"] = np.nan
    models = model_module.train(frame, Config())
    assert models.share_model is None
    assert any("insufficient classified sales" in w for w in models.warnings)
