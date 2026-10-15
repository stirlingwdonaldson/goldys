"""Tests for the FastAPI forecasting runtime using a fake predictor.

The real model (``TimesFmPredictor``) imports ``torch``/``timesfm`` lazily, so
this suite runs with only ``fastapi``, ``httpx``, ``pydantic``, ``pytest``,
``pandas`` and ``xgboost`` installed. Each test swaps the app's module-level
``predictor`` for a fake so TimesFM is never exercised, and monkeypatches
``build_external_features`` to a schema-only stub so no network is hit.
"""

import datetime as dt

import numpy as np
import pandas as pd
import pytest
from fastapi.testclient import TestClient

import app as app_module
import forecast as forecast_module
from external import (
    PUBLIC_HOLIDAY_COLUMNS,
    SCHOOL_COLUMNS,
    UNIVERSITY_COLUMNS,
    WEATHER_COLUMNS,
)

QUANTILES = ["0.1", "0.5", "0.9"]


def _stub_external(config, start, end, force_refresh=False):
    """Schema-only stand-in for ``build_external_features`` (no network)."""
    columns = (
        PUBLIC_HOLIDAY_COLUMNS + SCHOOL_COLUMNS + UNIVERSITY_COLUMNS + WEATHER_COLUMNS
    )
    frame = pd.DataFrame({"date": pd.date_range(start, end, freq="D")})
    for column in columns:
        frame[column] = pd.NA
    return frame


def _body(n: int = 60, horizon: int = 7) -> dict:
    """A continuous daily series with a weekly rhythm in revenue and covers."""
    dates = pd.date_range("2026-03-02", periods=n, freq="D")
    dow = dates.dayofweek
    net = 1000.0 + 200.0 * np.sin(2 * np.pi * dow / 7)
    share = np.clip(0.5 + 0.04 * np.sin(2 * np.pi * dow / 7), 0.05, 0.95)
    covers = 50.0 + 10.0 * dow
    return {
        "dates": [d.strftime("%Y-%m-%d") for d in dates],
        "net": [round(float(v), 2) for v in net],
        "booked_covers": [round(float(v), 2) for v in covers],
        "bookings": [float(i % 31) for i in range(n)],
        "food": [round(float(net[i] * share[i]), 2) for i in range(n)],
        "bev": [round(float(net[i] * (1 - share[i])), 2) for i in range(n)],
        "horizon": horizon,
        "quantiles": QUANTILES,
    }


def _future_book_body(n: int = 60, horizon: int = 7, trailing: int = 3) -> dict:
    """History plus ``trailing`` future-book days: ``net``/``food``/``bev`` are
    null after the last labelled date, but ``booked_covers`` is already known —
    the normal shape Goldy's sends (the future book has no revenue yet)."""
    dates = pd.date_range("2026-03-02", periods=n + trailing, freq="D")
    dow = dates.dayofweek
    net = 1000.0 + 200.0 * np.sin(2 * np.pi * dow / 7)
    share = np.clip(0.5 + 0.04 * np.sin(2 * np.pi * dow / 7), 0.05, 0.95)
    covers = 50.0 + 10.0 * dow
    return {
        "dates": [d.strftime("%Y-%m-%d") for d in dates],
        "net": [round(float(v), 2) for v in net[:n]] + [None] * trailing,
        "booked_covers": [round(float(v), 2) for v in covers],
        "bookings": [float(i % 31) for i in range(n + trailing)],
        "food": [round(float(net[i] * share[i]), 2) for i in range(n)] + [None] * trailing,
        "bev": [round(float(net[i] * (1 - share[i])), 2) for i in range(n)]
        + [None] * trailing,
        "horizon": horizon,
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

    def predict(
        self,
        dates,
        target,
        covariates,
        horizon,
        freq,
        quantiles,
        *,
        past_future_covariates=None,
    ):
        self.last_call = {
            "dates": dates,
            "target": target,
            "covariates": covariates,
            "horizon": horizon,
            "freq": freq,
            "quantiles": quantiles,
            "past_future_covariates": past_future_covariates,
        }
        last = dt.date.fromisoformat(dates[-1])
        future = [(last + dt.timedelta(days=i + 1)).isoformat() for i in range(horizon)]
        return future, {q: [float(i + 1) for i in range(horizon)] for q in quantiles}


@pytest.fixture
def client():
    app_module.predictor = FakePredictor()
    return TestClient(app_module.app)


def test_health(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "model": "timesfm-3.0", "warm": True}


def test_forecastReturnsDiagnosticsAndSplit(client, monkeypatch):
    monkeypatch.setattr(forecast_module, "build_external_features", _stub_external)

    response = client.post("/forecast", json=_body())

    assert response.status_code == 200
    body = response.json()

    rows = body["forecast"]
    assert len(rows) == 7
    first = rows[0]
    for key in ("date", "median", "p10", "p90", "food", "bev", "baseline"):
        assert key in first, f"missing forecast field {key!r}"
    assert first["median"] == 1.0
    assert first["food"] is not None
    assert first["bev"] is not None
    assert first["baseline"] is not None

    diagnostics = body["diagnostics"]
    assert diagnostics is not None
    assert diagnostics["modelVersion"] == "timesfm-3.0"
    assert diagnostics["importance"], "importance should be non-empty"
    assert diagnostics["holdoutNetXgboost"], "holdoutNetXgboost should be present"
    assert diagnostics["backtest"]["rows"], "backtest rows should be non-empty"


def test_xgboostFailureStillReturnsForecast(client, monkeypatch):
    monkeypatch.setattr(forecast_module, "build_external_features", _stub_external)

    def boom(*args, **kwargs):
        raise RuntimeError("xgboost exploded")

    monkeypatch.setattr(app_module, "train", boom)

    response = client.post("/forecast", json=_body())

    assert response.status_code == 200
    body = response.json()
    assert body["forecast"], "forecast must be non-null when the twin fails"
    assert body["diagnostics"] is None
    assert body["warnings"], "a warning must be surfaced when the twin fails"


def test_pastFutureCovariatesArePassed(client, monkeypatch):
    monkeypatch.setattr(forecast_module, "build_external_features", _stub_external)
    body = _body()

    response = client.post("/forecast", json=body)

    assert response.status_code == 200
    covariates = app_module.predictor.last_call["past_future_covariates"]
    assert covariates is not None
    assert covariates.ndim == 2
    assert covariates.shape[1] == len(body["dates"]) + body["horizon"]


def test_forecastAnchorsAtLastLabelledNetDate(client, monkeypatch):
    monkeypatch.setattr(forecast_module, "build_external_features", _stub_external)
    body = _future_book_body(n=60, horizon=7, trailing=3)

    response = client.post("/forecast", json=body)

    assert response.status_code == 200
    result = response.json()

    rows = result["forecast"]
    assert len(rows) == 7, "forecast should have exactly horizon rows, not trailing book days"

    # The forecast starts the day after the last labelled net date (2026-04-30),
    # not after the last request date (2026-05-03).
    cutoff = dt.date(2026, 4, 30)
    expected = [(cutoff + dt.timedelta(days=i + 1)).isoformat() for i in range(7)]
    assert [r["date"] for r in rows] == expected

    # TimesFM's context is trimmed to the labelled history, and the future-known
    # covariates span exactly that history plus the horizon.
    last_call = app_module.predictor.last_call
    assert len(last_call["dates"]) == 60
    covariates = last_call["past_future_covariates"]
    assert covariates.shape[1] == len(last_call["dates"]) + body["horizon"]


def test_prewarm_enabled_parses_env(monkeypatch):
    for value in ("true", "1", "yes", "on", "TRUE", " True "):
        monkeypatch.setenv("TIMESFM_PREWARM", value)
        assert app_module._prewarm_enabled() is True

    for value in ("false", "0", "no", "off", ""):
        monkeypatch.setenv("TIMESFM_PREWARM", value)
        assert app_module._prewarm_enabled() is False

    monkeypatch.delenv("TIMESFM_PREWARM", raising=False)
    assert app_module._prewarm_enabled() is False
