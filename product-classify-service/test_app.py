"""Tests for the FastAPI product classification service using a fake predictor.

The real model (``EmbeddingPredictor``) imports ``sentence_transformers``/``sklearn``
lazily, so this suite runs with only ``fastapi``, ``httpx``, ``pydantic``, and ``pytest``
installed. Each test swaps the app's module-level ``predictor`` for a fake so the model
is never exercised and no weights are downloaded.
"""

import pytest
from fastapi.testclient import TestClient

import app as app_module


class FakePredictor:
    """Deterministic stand-in: records its calls and returns a fixed result."""

    warm = True

    def __init__(self):
        self.last_embed_texts = None
        self.last_cluster_vectors = None
        self.warmup_calls = 0

    def warmup(self):
        self.warmup_calls += 1

    def embed(self, texts):
        self.last_embed_texts = texts
        return [[0.1, 0.2, 0.3] for _ in texts]

    def cluster(self, vectors):
        self.last_cluster_vectors = vectors
        n = len(vectors)
        leaves = [
            {"id": i, "parentId": n, "isLeaf": True, "productIndex": i, "height": 0.0, "size": 1}
            for i in range(n)
        ]
        root = {
            "id": n,
            "parentId": None,
            "isLeaf": False,
            "productIndex": None,
            "height": 1.0,
            "size": n,
        }
        return {
            "nodes": leaves + [root],
            "labels": [0] * n,
            "projection": [{"x": float(i), "y": float(i) * 2.0} for i in range(n)],
        }


@pytest.fixture
def client():
    app_module.predictor = FakePredictor()
    return TestClient(app_module.app)


def test_health_reports_model(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok", "model": "all-MiniLM-L6-v2"}


def test_embed_returns_vectors(client):
    response = client.post("/embed", json={"texts": ["apple", "banana"]})

    assert response.status_code == 200
    body = response.json()
    assert body["vectors"] == [[0.1, 0.2, 0.3], [0.1, 0.2, 0.3]]
    # One vector per input text, in order, passed through to the predictor.
    assert app_module.predictor.last_embed_texts == ["apple", "banana"]


def test_embed_empty_texts(client):
    response = client.post("/embed", json={"texts": []})

    assert response.status_code == 200
    assert response.json() == {"vectors": []}


def test_cluster_returns_nodes_labels_projection(client):
    response = client.post("/cluster", json={"vectors": [[0.1, 0.2], [0.3, 0.4]]})

    assert response.status_code == 200
    body = response.json()

    nodes = body["nodes"]
    assert len(nodes) == 3

    leaf0 = nodes[0]
    assert leaf0["id"] == 0
    assert leaf0["parentId"] == 2
    assert leaf0["isLeaf"] is True
    assert leaf0["productIndex"] == 0
    assert leaf0["height"] == 0.0
    assert leaf0["size"] == 1

    root = nodes[2]
    assert root["id"] == 2
    assert root["parentId"] is None
    assert root["isLeaf"] is False
    assert root["productIndex"] is None
    assert root["height"] == 1.0
    assert root["size"] == 2

    assert body["labels"] == [0, 0]
    assert body["projection"] == [{"x": 0.0, "y": 0.0}, {"x": 1.0, "y": 2.0}]


def test_prewarm_enabled_parses_env(monkeypatch):
    for value in ("true", "1", "yes", "on", "TRUE", " True "):
        monkeypatch.setenv("CLASSIFY_PREWARM", value)
        assert app_module._prewarm_enabled() is True

    for value in ("false", "0", "no", "off", ""):
        monkeypatch.setenv("CLASSIFY_PREWARM", value)
        assert app_module._prewarm_enabled() is False

    monkeypatch.delenv("CLASSIFY_PREWARM", raising=False)
    assert app_module._prewarm_enabled() is False
