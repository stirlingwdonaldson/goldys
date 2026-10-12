-- V46 - store an optional source-record reference on raw records.
--
-- The CTB detail backfill writes one raw record per parent id (a stocktake, wastage record or
-- recipe), but the detail response does not echo its parent id, so the link must be captured
-- out-of-band. This nullable column lets a connector record that reference at insert time without
-- disturbing the byte-faithful payload or the append-only trigger.
ALTER TABLE raw_record ADD COLUMN source_record_ref varchar(512);
