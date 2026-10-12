"""Tests for the FastAPI TimesFM inference service using a fake predictor.

The real model (``TimesFmPredictor``) imports ``torch``/``timesfm`` lazily, so this
suite runs with only ``fastapi``, ``httpx``, ``pydantic``, and ``pytest`` installed.
Each test swaps the app's module-level ``predictor`` for a fake so the model is never
exercised.
"""

import datetime as dt
import math

import pytest
from fastapi.testclient import TestClient

import app as app_module

QUANTILES = ["0.1", "0.2", "0.3", "0.4", "0.5", "0.6", "0.7", "0.8", "0.9"]

FORECAST_BODY = {
    "dates": ["2026-09-01", "2026-09-02", "2026-09-03"],
    "target": [1234.5, None, 1250.0],
    "covariates": {"covers": [85, None, 90]},
    "horizon": 2,
    "freq": "D",
    "quantiles": QUANTILES,
}


class FakePredictor:
    """Deterministic stand-in: records its call and returns a fixed forecast."""

    warm = True

    def __init__(self):
        self.last_call = None
        self.warmup_calls = 0

    def warmup(self):
        self.warmup_calls += 1

    def predict(self, dates, target, covariates, horizon, freq, quantiles):
        self.last_call = (dates, target, covariates, horizon, freq, quantiles)
        last = dt.date.fromisoformat(dates[-1])
        future = [(last + dt.timedelta(days=i + 1)).isoformat() for i in range(horizon)]
        return future, {q: [float(i + 1) for i in range(horizon)] for q in quantiles}


class NanPredictor:
    """Returns NaN so the response null-conversion can be pinned."""

    warm = True

    def predict(self, dates, target, covariates, horizon, freq, quantiles):
        last = dt.date.fromisoformat(dates[-1])
        future = [(last + dt.timedelta(days=i + 1)).isoformat() for i in range(horizon)]
        return future, {q: [float("nan") for _ in range(horizon)] for q in quantiles}


@pytest.fixture
def client():
    app_module.predictor = FakePredictor()
    return TestClient(app_module.app)


def test_health(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "model": "timesfm-3.0", "warm": True}


def test_forecast_shape(client):
    response = client.post("/forecast", json=FORECAST_BODY)

    assert response.status_code == 200
    body = response.json()
    assert len(body["dates"]) == 2
    assert list(body["quantiles"].keys()) == QUANTILES
    for values in body["quantiles"].values():
        assert len(values) == 2
    # JSON nulls in the request reach the model as NaN, not None or a dropped element.
    _, target, covariates, _, _, _ = app_module.predictor.last_call
    assert math.isnan(target[1])
    assert math.isnan(covariates["covers"][1])


def test_target_length_mismatch_is_422(client):
    body = {**FORECAST_BODY, "target": [1.0, 2.0]}

    response = client.post("/forecast", json=body)

    assert response.status_code == 422


def test_nan_serializes_as_null():
    app_module.predictor = NanPredictor()
    client = TestClient(app_module.app)

    response = client.post("/forecast", json=FORECAST_BODY)

    assert response.status_code == 200
    body = response.json()
    assert body["quantiles"]["0.1"] == [None, None]


def test_prewarm_enabled_parses_env(monkeypatch):
    for value in ("true", "1", "yes", "on", "TRUE", " True "):
        monkeypatch.setenv("TIMESFM_PREWARM", value)
        assert app_module._prewarm_enabled() is True

    for value in ("false", "0", "no", "off", ""):
        monkeypatch.setenv("TIMESFM_PREWARM", value)
        assert app_module._prewarm_enabled() is False

    monkeypatch.delenv("TIMESFM_PREWARM", raising=False)
    assert app_module._prewarm_enabled() is False
