"""Predictor abstraction for the TimesFM inference service.

The real forecaster wraps Google's TimesFM 3.0. ``torch`` and ``timesfm`` are imported
lazily inside :class:`TimesFmPredictor` — never at module import time — so the FastAPI
app (and its test suite) can be imported and run with neither installed.
"""

from __future__ import annotations

import datetime as dt
import os
from typing import Protocol

import numpy as np


class Predictor(Protocol):
    """The interface ``app`` depends on; tests inject a ``FakePredictor``."""

    @property
    def warm(self) -> bool:
        """Whether the model is loaded and ready to serve predictions."""

    def warmup(self) -> None:
        """Pre-load the model (downloading weights on first call) without predicting."""

    def predict(
        self,
        dates: list[str],
        target: list[float],
        covariates: dict[str, list[float]] | None,
        horizon: int,
        freq: str,
        quantiles: list[str],
        *,
        past_future_covariates: np.ndarray | None = None,
    ) -> tuple[list[str], dict[str, list[float]]]:
        """Forecast ``horizon`` steps ahead of ``dates[-1]``.

        ``target`` and every covariate array are aligned to ``dates`` by index; missing
        values are ``float("nan")``. ``past_future_covariates`` (when given) holds the
        covariates known for both the context and the horizon, shaped
        ``(num_covariates, context_len + horizon)``; it is passed straight through to
        TimesFM's ``past_future_covariates``. Returns the future dates (ascending) and
        one value per quantile per date.
        """


# The nine deciles TimesFM 3.0's quantile head returns, in ascending order.
QUANTILE_HEAD = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9]


def forecast_dates(last_date: str, horizon: int, freq: str) -> list[str]:
    """The ``horizon`` dates that follow ``last_date`` at ``freq`` (daily only)."""
    if freq != "D":
        raise ValueError(f"unsupported frequency: {freq!r}")
    last = dt.date.fromisoformat(last_date)
    return [(last + dt.timedelta(days=i + 1)).isoformat() for i in range(horizon)]


class TimesFmPredictor:
    """TimesFM 3.0 forecaster, loaded lazily on first :meth:`predict`."""

    def __init__(self) -> None:
        self._forecaster = None

    @property
    def warm(self) -> bool:
        return self._forecaster is not None

    def warmup(self) -> None:
        """Pre-load the model so the first forecast isn't stalled by the weight download."""
        self._load()

    def _load(self):
        if self._forecaster is None:
            # Lazy import: torch/timesfm are only required at inference time.
            from timesfm3 import TimesFM3Forecaster

            model = os.environ.get("TIMESFM_MODEL", "google/timesfm-3.0-pytorch")
            kwargs = {}
            if os.environ.get("TIMESFM_DEVICE"):
                kwargs["device"] = os.environ["TIMESFM_DEVICE"]
            if os.environ.get("TIMESFM_CACHE_DIR"):
                kwargs["cache_dir"] = os.environ["TIMESFM_CACHE_DIR"]
            self._forecaster = TimesFM3Forecaster.from_pretrained(model, **kwargs)
        return self._forecaster

    def predict(
        self, dates, target, covariates, horizon, freq, quantiles, *, past_future_covariates=None
    ):
        forecaster = self._load()

        context = np.asarray(target, dtype=np.float32)

        covariate_array = None
        if covariates:
            covariate_array = np.stack(
                [np.asarray(values, dtype=np.float32) for values in covariates.values()]
            )

        output = forecaster.predict(
            context,
            horizon=horizon,
            past_only_covariates=covariate_array,
            past_future_covariates=past_future_covariates,
            return_quantiles=True,
        )

        # output.quantiles has shape (horizon, 9), one column per QUANTILE_HEAD decile.
        matrix = output.quantiles
        result = {
            q: [float(value) for value in matrix[:, QUANTILE_HEAD.index(float(q))]]
            for q in quantiles
        }

        return forecast_dates(dates[-1], horizon, freq), result
