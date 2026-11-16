-- Preserve admission identity across edits without a second copy of user text.
-- Existing edited requests have unknown original text; retries fail closed for those IDs.
ALTER TABLE workspace_follow_up ADD COLUMN request_prompt_sha256 bytea;
ALTER TABLE workspace_follow_up ADD CONSTRAINT workspace_request_digest_size
    CHECK (request_prompt_sha256 IS NULL OR octet_length(request_prompt_sha256) = 32);
