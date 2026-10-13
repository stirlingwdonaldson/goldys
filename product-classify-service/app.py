"""FastAPI app exposing the self-hosted product classification service.

Data-agnostic: ``POST /embed`` takes ``{texts:[…]}`` and returns ``{vectors:[[…]]}`` of
384-dim sentence embeddings; ``POST /cluster`` takes ``{vectors:[[…]]}`` and returns a
dendrogram (``nodes``), a default flat clustering (``labels``) and a 2-D projection
(``projection``). It knows nothing about Goldy's schema, metrics, or permissions; Java
does all product preparation upstream.

``sentence-transformers``/``scikit-learn`` are imported lazily inside
:class:`~predictor.EmbeddingPredictor`, so this module imports cleanly without them.
The module-level ``predictor`` is swapped for a fake in tests.
"""

from __future__ import annotations

import os
import threading
from contextlib import asynccontextmanager

from fastapi import FastAPI
from pydantic import BaseModel

from predictor import EmbeddingPredictor, Predictor

MODEL_VERSION = "all-MiniLM-L6-v2"

# Swapped for a FakePredictor in tests.
predictor: Predictor = EmbeddingPredictor()


def _prewarm_enabled() -> bool:
    return os.environ.get("CLASSIFY_PREWARM", "false").strip().lower() in ("1", "true", "yes", "on")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Pre-download/load the model in a background thread so /health responds immediately
    # and the first /embed isn't stalled by the (~90 MB) weight download. The cache
    # volume persists the download across restarts.
    if _prewarm_enabled():
        threading.Thread(target=predictor.warmup, name="classify-prewarm", daemon=True).start()
    yield


app = FastAPI(title="Product classification service", version=MODEL_VERSION, lifespan=lifespan)


class EmbedRequest(BaseModel):
    texts: list[str]


class EmbedResponse(BaseModel):
    vectors: list[list[float]]


class ClusterRequest(BaseModel):
    vectors: list[list[float]]


class ClusterNode(BaseModel):
    id: int
    parentId: int | None
    isLeaf: bool
    productIndex: int | None
    height: float
    size: int


class ClusterPoint(BaseModel):
    x: float
    y: float


class ClusterResponse(BaseModel):
    nodes: list[ClusterNode]
    labels: list[int]
    projection: list[ClusterPoint]


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "model": MODEL_VERSION}


@app.post("/embed")
def embed(request: EmbedRequest) -> EmbedResponse:
    return EmbedResponse(vectors=predictor.embed(request.texts))


@app.post("/cluster")
def cluster(request: ClusterRequest) -> ClusterResponse:
    result = predictor.cluster(request.vectors)
    return ClusterResponse(
        nodes=result["nodes"],
        labels=result["labels"],
        projection=result["projection"],
    )
