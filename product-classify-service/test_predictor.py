"""Tests for the real ``EmbeddingPredictor.cluster`` dendrogram reconstruction.

These pin the one non-trivial algorithm in the service — turning scikit-learn's
``children_``/``distances_`` arrays into the ``nodes`` list the Java adapter parses —
without loading the sentence-transformer model. ``cluster()`` only needs ``numpy`` +
``scikit-learn`` (both in ``requirements.txt``), so no model download happens.
"""

from predictor import EmbeddingPredictor


def _points():
    # Clearly-separated, non-zero vectors so cosine affinity is well-defined and the
    # merge order has no ties (the structural invariants below hold regardless, but a
    # stable dendrogram makes failures easier to read).
    return [
        [1.0, 0.0, 0.0, 0.0],
        [0.9, 0.1, 0.0, 0.0],
        [0.0, 1.0, 0.0, 0.0],
        [0.0, 0.0, 1.0, 0.0],
        [0.0, 0.0, 0.0, 1.0],
    ]


def test_cluster_builds_full_binary_tree():
    vectors = _points()
    n = len(vectors)

    result = EmbeddingPredictor().cluster(vectors)
    nodes = result["nodes"]

    # 1. n leaves + n-1 internal nodes.
    assert len(nodes) == 2 * n - 1

    by_id = {node["id"]: node for node in nodes}
    leaves = [node for node in nodes if node["isLeaf"]]
    internal = [node for node in nodes if not node["isLeaf"]]

    # 2. Leaves come first: ids 0..n-1, with leaf-only fields set correctly.
    assert [node["id"] for node in nodes[:n]] == list(range(n))
    assert len(leaves) == n
    assert len(internal) == n - 1

    for i, leaf in enumerate(leaves):
        assert leaf["id"] == i
        assert leaf["isLeaf"] is True
        assert leaf["productIndex"] == i
        assert leaf["height"] == 0.0
        assert leaf["size"] == 1
        assert leaf["parentId"] is not None
        assert n <= leaf["parentId"] <= 2 * n - 2

    # 3. Internal nodes come after, ids n..2n-2, with internal-only fields.
    assert [node["id"] for node in internal] == list(range(n, 2 * n - 1))
    for node in internal:
        assert node["isLeaf"] is False
        assert node["productIndex"] is None
        assert node["height"] >= 0.0
        assert node["size"] >= 2

    # Every internal node is binary and its size is the sum of its children's sizes.
    children_of = {node["id"]: [] for node in nodes}
    for node in nodes:
        if node["parentId"] is not None:
            children_of[node["parentId"]].append(node["id"])
    for node in internal:
        kids = children_of[node["id"]]
        assert len(kids) == 2
        assert node["size"] == by_id[kids[0]]["size"] + by_id[kids[1]]["size"]

    # 4. The root (last internal node) has no parent and spans every input vector.
    root = by_id[2 * n - 2]
    assert root["isLeaf"] is False
    assert root["parentId"] is None
    assert root["size"] == n

    # 5. labels and projection are aligned to the input, one x/y pair per vector.
    assert len(result["labels"]) == n
    assert len(result["projection"]) == n
    for point in result["projection"]:
        assert set(point.keys()) == {"x", "y"}
        assert isinstance(point["x"], float)
        assert isinstance(point["y"], float)


def test_cluster_empty():
    result = EmbeddingPredictor().cluster([])

    assert result == {"nodes": [], "labels": [], "projection": []}


def test_cluster_single_vector():
    result = EmbeddingPredictor().cluster([[1.0, 2.0]])

    assert result["nodes"] == [
        {
            "id": 0,
            "parentId": None,
            "isLeaf": True,
            "productIndex": 0,
            "height": 0.0,
            "size": 1,
        }
    ]
    assert result["labels"] == [0]
    assert result["projection"] == [{"x": 0.0, "y": 0.0}]
