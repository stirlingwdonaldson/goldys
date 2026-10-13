-- Preserve the clustering dendrogram's node order: the Python service reports each node with an
-- integer id that encodes merge order (and, for leaves, left-to-right position). Storing it lets
-- Task 8 render the dendrogram deterministically instead of ordering by a random UUID.
ALTER TABLE classification_cluster ADD COLUMN node_order int NOT NULL;
