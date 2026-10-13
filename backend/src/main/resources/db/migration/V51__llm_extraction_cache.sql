-- V51 - cache LLM invoice extraction keyed by the SHA-256 of the input text. A re-run of the PDF
-- replay reuses the same extracted lines instead of re-calling the non-deterministic model, so the
-- re-enrich is idempotent and its flag set stops churning between runs.
CREATE TABLE llm_extraction_cache (
    text_sha256 varchar(64) PRIMARY KEY,
    extracted_json text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL
);
