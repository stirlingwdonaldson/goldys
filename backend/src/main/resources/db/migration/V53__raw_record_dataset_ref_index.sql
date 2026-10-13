-- Lookup of the latest stored payload for one dataset window, used by the OpenTable connector to
-- skip storing a response byte-identical to the last one for the same (source, dataset, window).
-- Index only: the raw ledger stays append-only and nothing here reads or rewrites payloads.
CREATE INDEX idx_raw_record_source_fetcher_ref
    ON raw_record (source_system, fetcher_identity, source_record_ref, fetched_at DESC);
