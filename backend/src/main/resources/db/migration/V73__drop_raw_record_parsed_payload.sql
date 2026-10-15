-- V73 - drop the vestigial parsed_payload column from raw_record.
--
-- V2 demoted the original `payload` jsonb column to `parsed_payload` as an
-- "optional derived representation" once exact bytes became the authoritative
-- evidence, but nothing ever mapped or wrote it: the RawRecord entity has no
-- field for it, no application code references it, and every production row is
-- null (audited 2026-10). Dropping it removes a dead column that read like an
-- unfinished feature. Exact bytes (payload_bytes + payload_sha256 +
-- payload_byte_length) remain the authoritative evidence and are untouched.
ALTER TABLE raw_record DROP COLUMN parsed_payload;
