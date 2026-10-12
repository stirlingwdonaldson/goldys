"""FastAPI app exposing the self-hosted TimesFM inference service.

Data-agnostic: ``POST /forecast`` takes aligned numeric arrays and returns quantile
forecasts; it knows nothing about Goldy's schema, metrics, or permissions. Java does
all data preparation upstream.

``torch``/``timesfm`` are imported lazily inside :class:`~predictor.TimesFmPredictor`,
so this module imports cleanly without them. The module-level ``predictor`` is swapped
for a fake in tests.
"""

from __future__ import annotations

import math
import os
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel, Field, model_validator

from predictor import Predictor, TimesFmPredictor

MODEL_VERSION = "timesfm-3.0"

# Swapped for a FakePredictor in tests.
predictor: Predictor = TimesFmPredictor()

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
    target: list[float | None]
    covariates: dict[str, list[float | None]] | None = None
    horizon: int = Field(gt=0)
    freq: str = "D"
    quantiles: list[str]

    @model_validator(mode="after")
    def _aligned_lengths(self) -> "ForecastRequest":
        n = len(self.dates)
        if len(self.target) != n:
            raise ValueError("target length must equal dates length")
        if self.covariates is not None:
            for name, values in self.covariates.items():
                if len(values) != n:
                    raise ValueError(f"covariate {name!r} length must equal dates length")
        return self


class ForecastResponse(BaseModel):
    dates: list[str]
    quantiles: dict[str, list[float | None]]


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_VERSION, "warm": predictor.warm}


@app.post("/forecast")
def forecast(request: ForecastRequest) -> ForecastResponse:
    target = [_nan_if_none(value) for value in request.target]
    covariates = None
    if request.covariates is not None:
        covariates = {
            name: [_nan_if_none(value) for value in values]
            for name, values in request.covariates.items()
        }

    dates, quantiles = predictor.predict(
        request.dates, target, covariates, request.horizon, request.freq, request.quantiles
    )

    return ForecastResponse(
        dates=dates,
        quantiles={
            q: [_none_if_nan(value) for value in values]
            for q, values in quantiles.items()
        },
    )


def _nan_if_none(value: float | None) -> float:
    return float("nan") if value is None else value


def _none_if_nan(value: float) -> float | None:
    return None if math.isnan(value) else value
