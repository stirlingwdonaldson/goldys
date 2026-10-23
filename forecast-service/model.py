"""Model training.

Two models are fitted:

  1. ``net_revenue`` — total takings for the day (log1p target)
  2. ``food_share``  — the food fraction of net, which splits net into food/bev

Both are evaluated against a seasonal-naive baseline (same weekday, mean of
the last four occurrences). If XGBoost cannot beat that baseline it is not
earning its keep, and the pipeline says so rather than quietly shipping it.

This is a port of the reference pipeline's ``pipeline/model.py``, adapted to
Goldy's forecast-service:

  * ``Config`` comes from :mod:`external` (env-backed, not ``config.yaml``).
  * ``booked_covers`` replaces the reference's ``bookings``/OpenTable columns.
  * Labour is deferred, so there is no labour model or labour metric.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

from external import Config
from features import (
    TARGET_NET,
    TARGET_SHARE,
    WEATHER_FEATURE_COLUMNS,
    add_lag_features,
    feature_columns,
    prepare_matrix,
)

LOGGER = logging.getLogger(__name__)


@dataclass
class Metrics:
    mae: float
    rmse: float
    mape: float
    n: int

    def as_dict(self) -> dict[str, float]:
        return {"mae": self.mae, "rmse": self.rmse, "mape": self.mape, "n": self.n}

    def render(self, label: str) -> str:
        return (
            f"{label:<22} MAE ${self.mae:>9,.0f}   "
            f"RMSE ${self.rmse:>9,.0f}   MAPE {self.mape:>5.1f}%   n={self.n}"
        )


@dataclass
class TrainedModels:
    net_model: Any
    share_model: Any
    features: list[str]
    share_features: list[str]
    metrics: dict[str, Any] = field(default_factory=dict)
    warnings: list[str] = field(default_factory=list)
    trained_rows: int = 0
    reliable: bool = False
    config_snapshot: dict[str, Any] = field(default_factory=dict)
    validation_rows: pd.DataFrame = field(default_factory=pd.DataFrame)


def score(actual: pd.Series, predicted: pd.Series) -> Metrics:
    mask = actual.notna() & predicted.notna()
    actual_values = actual[mask].to_numpy(dtype=float)
    predicted_values = predicted[mask].to_numpy(dtype=float)
    if actual_values.size == 0:
        return Metrics(mae=float("nan"), rmse=float("nan"), mape=float("nan"), n=0)

    errors = predicted_values - actual_values
    nonzero = actual_values != 0
    mape = (
        float(np.mean(np.abs(errors[nonzero] / actual_values[nonzero])) * 100)
        if nonzero.any()
        else float("nan")
    )
    return Metrics(
        mae=float(np.mean(np.abs(errors))),
        rmse=float(np.sqrt(np.mean(errors**2))),
        mape=mape,
        n=int(actual_values.size),
    )


def _make_regressor(config: Config, *, n_rows: int):
    from xgboost import XGBRegressor

    params = dict(config.get("model.xgboost", {}) or {})
    params.pop("early_stopping_rounds", None)

    # Shallow trees and heavier regularisation when data is thin, otherwise
    # XGBoost memorises the handful of rows it has.
    if n_rows < 200:
        params["max_depth"] = min(int(params.get("max_depth", 4)), 3)
        params["n_estimators"] = min(int(params.get("n_estimators", 400)), 200)
        params["reg_lambda"] = max(float(params.get("reg_lambda", 2.0)), 5.0)

    return XGBRegressor(
        objective="reg:squarederror",
        random_state=int(config.get("model.random_state", 42)),
        n_jobs=4,
        **params,
    )


def _fit_target(
    frame: pd.DataFrame,
    columns: list[str],
    target: str,
    config: Config,
    *,
    log_target: bool,
):
    usable = frame[frame[target].notna()]
    if target == TARGET_SHARE:
        usable = usable[usable[TARGET_NET].notna()]
        minimum = int(config.get("model.min_share_rows", 28))
        coverage = float(config.get("model.min_share_coverage", 0.8))
        labelled = frame[TARGET_NET].notna().sum()
        if len(usable) < minimum or len(usable) < labelled * coverage:
            return None, usable
    if usable.empty:
        return None, usable

    matrix = prepare_matrix(usable, columns)
    values = usable[target].astype(float)
    y = np.log1p(values.clip(lower=0)) if log_target else values

    model = _make_regressor(config, n_rows=len(usable))
    model.fit(matrix, y)
    return model, usable


def _predict_target(model, frame: pd.DataFrame, columns: list[str], *, log_target: bool):
    if model is None:
        return pd.Series(np.nan, index=frame.index)
    raw = model.predict(prepare_matrix(frame, columns))
    values = np.expm1(raw) if log_target else raw
    return pd.Series(values, index=frame.index)


def evaluate_window(
    history: pd.DataFrame, future: pd.DataFrame, config: Config
) -> pd.DataFrame:
    """Evaluate a fixed forecast origin without reading future actuals as features.

    We have no historical booking snapshots or archived weather forecasts. Future
    bookings therefore use the training weekday average; observed future weather is
    withheld. This is a conservative diagnostic, not a reconstruction of TimesFM.
    """
    history = add_lag_features(history, config)
    columns = feature_columns(history, config)
    if not columns or future.empty:
        return pd.DataFrame()
    share_columns = [c for c in columns if "net_revenue" not in c]
    net_model, _ = _fit_target(history, columns, TARGET_NET, config, log_target=True)
    share_model, _ = _fit_target(
        history, share_columns, TARGET_SHARE, config, log_target=False
    )
    if net_model is None:
        return pd.DataFrame()

    unseen = future.copy()
    unknown_columns = [
        TARGET_NET, TARGET_SHARE, "food_revenue", "bev_revenue", *WEATHER_FEATURE_COLUMNS
    ]
    for column in unknown_columns:
        if column in unseen:
            unseen[column] = np.nan
    if "booked_covers" in unseen:
        by_dow = history.groupby("day_of_week")["booked_covers"].mean()
        unseen["booked_covers"] = unseen["day_of_week"].map(by_dow)
    working = pd.concat([history, unseen], ignore_index=True)
    baseline = history.groupby("day_of_week")[TARGET_NET].apply(
        lambda s: s.dropna().tail(4).mean()
    )
    rows = []
    for _, actual in future.sort_values("date").iterrows():
        featured = add_lag_features(working, config)
        row = featured[featured["date"] == actual["date"]]
        net = max(
            float(_predict_target(net_model, row, columns, log_target=True).iloc[0]), 0.0
        )
        share = float(
            _predict_target(share_model, row, share_columns, log_target=False)
            .clip(0.05, 0.95).iloc[0]
        )
        mask = working["date"] == actual["date"]
        working.loc[mask, TARGET_NET] = net
        working.loc[mask, TARGET_SHARE] = share
        working.loc[mask, "food_revenue"] = net * share
        working.loc[mask, "bev_revenue"] = net * (1 - share)
        truth = actual[TARGET_NET]
        error = net - truth
        # Product amounts are gross; split actual net using the same share as live
        # predictions, so food/beverage accuracy compares like with like.
        rows.append({
            "date": actual["date"],
            "day": actual["date"].day_name(),
            "actual_net_revenue": truth,
            "predicted_net_revenue": net,
            "error": error,
            "abs_pct_error": (
                abs(error / truth) * 100 if pd.notna(truth) and truth != 0 else np.nan
            ),
            "actual_food_revenue": truth * actual[TARGET_SHARE],
            "predicted_food_revenue": net * share,
            "actual_bev_revenue": truth * (1 - actual[TARGET_SHARE]),
            "predicted_bev_revenue": net * (1 - share),
            "baseline_net_revenue": baseline.get(actual["day_of_week"], np.nan),
        })
    return pd.DataFrame(rows)


def _expanding_cv(
    frame: pd.DataFrame,
    config: Config,
) -> tuple[Metrics | None, Metrics | None]:
    """Expanding-window CV: always train on the past, test on the future."""
    folds = int(config.get("model.cv_folds", 4))
    horizon = int(config.get("model.cv_horizon_days", 7))
    last_actual = frame.loc[frame[TARGET_NET].notna(), "date"].max()
    labelled = frame[frame["date"] <= last_actual].reset_index(drop=True)

    min_train = max(horizon * 2, 14)
    if len(labelled) < min_train + horizon:
        return None, None

    max_folds = (len(labelled) - min_train) // horizon
    folds = max(1, min(folds, max_folds))
    if folds < 1:
        return None, None

    model_pred, base_pred, truth = [], [], []
    for fold in range(folds):
        test_end = len(labelled) - (folds - fold - 1) * horizon
        test_start = test_end - horizon
        if test_start < min_train:
            continue

        train = labelled.iloc[:test_start]
        test = labelled.iloc[test_start:test_end]

        evaluated = evaluate_window(train, test, config)
        if evaluated.empty:
            continue
        model_pred.append(evaluated["predicted_net_revenue"])
        base_pred.append(evaluated["baseline_net_revenue"])
        truth.append(evaluated["actual_net_revenue"])

    if not truth:
        return None, None

    actual = pd.concat(truth, ignore_index=True)
    return (
        score(actual, pd.concat(model_pred, ignore_index=True)),
        score(actual, pd.concat(base_pred, ignore_index=True)),
    )


def train(frame: pd.DataFrame, config: Config) -> TrainedModels:
    labelled = frame[frame[TARGET_NET].notna()].copy()
    n_rows = len(labelled)
    warnings: list[str] = []

    minimum = int(config.get("model.min_rows_for_xgboost", 120))
    reliable = n_rows >= minimum
    if not reliable:
        warnings.append(
            f"Only {n_rows} days of revenue history. XGBoost needs roughly "
            f"{minimum}+ days (ideally 12+ months) before it can learn seasonal, "
            f"weather and holiday effects. Treat these forecasts as indicative "
            f"only and prefer the seasonal-naive baseline until more data lands."
        )

    columns = feature_columns(frame, config)
    if not columns:
        raise ValueError("No usable feature columns — check external data sources")

    # The share model must not see net-revenue lags; it predicts a ratio.
    share_columns = [c for c in columns if "net_revenue" not in c]

    # Chronological holdout. `test_fraction` (0.25) scales the split with the
    # history instead of staying pinned at one week.
    test_fraction = config.get("model.test_fraction", 0.25)
    if test_fraction:
        test_days = max(1, int(round(n_rows * float(test_fraction))))
    else:
        test_days = int(config.get("model.test_days", 7))
    if n_rows > test_days * 2:
        first_test_date = labelled.iloc[-test_days]["date"]
        train_frame = frame[frame["date"] < first_test_date]
        test_frame = frame[
            (frame["date"] >= first_test_date)
            & (frame["date"] <= labelled["date"].max())
        ]
        validation = evaluate_window(train_frame, test_frame, config)
    else:
        warnings.append("Too little data for a holdout; no backtest accuracy is available")
        validation = pd.DataFrame()

    metrics: dict[str, Any] = {}
    if not validation.empty:
        for key, actual, predicted in (
            ("holdout_net_xgboost", "actual_net_revenue", "predicted_net_revenue"),
            ("holdout_net_baseline", "actual_net_revenue", "baseline_net_revenue"),
            ("holdout_food", "actual_food_revenue", "predicted_food_revenue"),
            ("holdout_bev", "actual_bev_revenue", "predicted_bev_revenue"),
        ):
            metrics[key] = score(validation[actual], validation[predicted]).as_dict()

    cv_model, cv_baseline = _expanding_cv(frame, config)
    if cv_model and cv_baseline:
        metrics["cv_net_xgboost"] = cv_model.as_dict()
        metrics["cv_net_baseline"] = cv_baseline.as_dict()
        if cv_model.mae > cv_baseline.mae:
            warnings.append(
                f"XGBoost (CV MAE ${cv_model.mae:,.0f}) is not beating the "
                f"seasonal-naive baseline (${cv_baseline.mae:,.0f}). "
                f"Use the baseline until more history is available."
            )
    else:
        warnings.append("Not enough data for cross-validation; only a holdout was run")

    # Refit on everything so the shipped model uses all available history.
    net_model, _ = _fit_target(labelled, columns, TARGET_NET, config, log_target=True)
    share_model, _ = _fit_target(
        labelled, share_columns, TARGET_SHARE, config, log_target=False
    )
    if share_model is None:
        minimum = int(config.get("model.min_share_rows", 28))
        coverage = float(config.get("model.min_share_coverage", 0.8))
        warnings.append(
            "Food/beverage split unavailable: insufficient classified sales history "
            f"(need {minimum} days and {coverage:.0%} coverage)."
        )

    return TrainedModels(
        net_model=net_model,
        share_model=share_model,
        features=columns,
        share_features=share_columns,
        metrics=metrics,
        warnings=warnings,
        trained_rows=n_rows,
        reliable=reliable,
        validation_rows=validation,
        config_snapshot={
            "target_transform": config.get("model.target_transform", "log1p"),
            "xgboost": config.get("model.xgboost", {}),
        },
    )


def feature_importance(models: TrainedModels) -> pd.DataFrame:
    if models.net_model is None:
        return pd.DataFrame(columns=["feature", "importance"])
    importance = pd.DataFrame(
        {
            "feature": models.features,
            "importance": models.net_model.feature_importances_,
        }
    )
    return importance.sort_values("importance", ascending=False).reset_index(drop=True)


def save(models: TrainedModels, path: Path) -> Path:
    import joblib

    path.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(models, path)
    return path


def load(path: Path) -> TrainedModels:
    import joblib

    return joblib.load(path)
