"""Predictor abstraction for the product classification service.

The real classifier wraps ``sentence-transformers/all-MiniLM-L6-v2`` (384-dim sentence
embeddings) and scikit-learn's agglomerative clustering + PCA. ``sentence_transformers``,
``sklearn`` and ``numpy`` are imported lazily inside :class:`EmbeddingPredictor` — never
at module import time — so the FastAPI app (and its test suite) can be imported and run
with only the lightweight dependencies installed and without downloading the model.
"""

from __future__ import annotations

from typing import Protocol

# The default flat cut of the dendrogram: one cluster label per input vector, targeting
# a product-category count in the ~20-40 range (capped at the number of inputs).
DEFAULT_FLAT_CLUSTERS = 30

# The SentenceTransformer model backing /embed: 384-dim sentence embeddings.
MODEL_NAME = "sentence-transformers/all-MiniLM-L6-v2"


class Predictor(Protocol):
    """The interface ``app`` depends on; tests inject a ``FakePredictor``."""

    @property
    def warm(self) -> bool:
        """Whether the model is loaded and ready to serve embeddings."""

    def warmup(self) -> None:
        """Pre-load the model (downloading weights on first call) without embedding."""

    def embed(self, texts: list[str]) -> list[list[float]]:
        """Embed each text into a 384-dim vector, in order. Empty input → empty list."""

    def cluster(self, vectors: list[list[float]]) -> dict:
        """Cluster ``vectors`` into a dendrogram.

        Returns a dict with three keys, all aligned to the input order where relevant:

        - ``nodes``: leaves first (``id`` 0..n-1, one per input vector), then internal
          merge nodes (``id`` n..2n-2 in merge order). Each node is a dict with
          ``id``/``parentId``/``isLeaf``/``productIndex``/``height``/``size``.
        - ``labels``: the flat cluster id per input vector at the default cut.
        - ``projection``: a 2-D PCA ``{"x", "y"}`` point per input vector, in order.
        """


class EmbeddingPredictor:
    """Sentence-transformer + agglomerative clustering, loaded lazily on first use."""

    def __init__(self) -> None:
        self._model = None

    @property
    def warm(self) -> bool:
        return self._model is not None

    def warmup(self) -> None:
        """Pre-load the model so the first /embed isn't stalled by the weight download."""
        self._load()

    def _load(self):
        if self._model is None:
            # Lazy import: sentence-transformers/torch are only required at inference time.
            from sentence_transformers import SentenceTransformer

            self._model = SentenceTransformer(MODEL_NAME)
        return self._model

    def embed(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        model = self._load()
        # encode returns a (n, 384) float32 array; .tolist() yields plain Python floats.
        return model.encode(texts).tolist()

    def cluster(self, vectors: list[list[float]]) -> dict:
        import numpy as np
        from sklearn.cluster import AgglomerativeClustering
        from sklearn.decomposition import PCA

        n = len(vectors)
        if n == 0:
            return {"nodes": [], "labels": [], "projection": []}

        # PCA needs at least as many samples as components; a single vector degenerates.
        if n == 1:
            leaf = {
                "id": 0,
                "parentId": None,
                "isLeaf": True,
                "productIndex": 0,
                "height": 0.0,
                "size": 1,
            }
            return {"nodes": [leaf], "labels": [0], "projection": [{"x": 0.0, "y": 0.0}]}

        matrix = np.asarray(vectors, dtype=np.float64)

        # Build the full dendrogram: children_ is an (n-1, 2) array of merge pairs and
        # distances_ the (n-1,) merge heights, both in merge order (row i merges the two
        # child node ids into internal node id n+i).
        tree = AgglomerativeClustering(
            n_clusters=None,
            distance_threshold=0.0,
            linkage="average",
            metric="cosine",
            compute_full_tree=True,
        )
        tree.fit(matrix)
        children = tree.children_
        distances = tree.distances_

        # Leaves are 0..n-1; internal nodes are n..2n-2. Track each node's parent id and
        # subtree leaf count so we can emit parentId/size deterministically.
        total = 2 * n - 1
        parent: list[int | None] = [None] * total
        size = [1] * n + [0] * (n - 1)
        for i in range(n - 1):
            left = int(children[i, 0])
            right = int(children[i, 1])
            node_id = n + i
            parent[left] = node_id
            parent[right] = node_id
            size[node_id] = size[left] + size[right]

        nodes: list[dict] = []
        for i in range(n):
            nodes.append(
                {
                    "id": i,
                    "parentId": parent[i],
                    "isLeaf": True,
                    "productIndex": i,
                    "height": 0.0,
                    "size": size[i],
                }
            )
        for i in range(n - 1):
            node_id = n + i
            nodes.append(
                {
                    "id": node_id,
                    "parentId": parent[node_id],
                    "isLeaf": False,
                    "productIndex": None,
                    "height": float(distances[i]),
                    "size": size[node_id],
                }
            )

        # Default flat cut: ~DEFAULT_FLAT_CLUSTERS clusters (capped at n).
        n_clusters = min(n, DEFAULT_FLAT_CLUSTERS)
        labels = (
            AgglomerativeClustering(
                n_clusters=n_clusters, linkage="average", metric="cosine"
            )
            .fit(matrix)
            .labels_
            .tolist()
        )

        projection = [
            {"x": float(point[0]), "y": float(point[1])}
            for point in PCA(n_components=2).fit_transform(matrix)
        ]

        return {"nodes": nodes, "labels": labels, "projection": projection}
