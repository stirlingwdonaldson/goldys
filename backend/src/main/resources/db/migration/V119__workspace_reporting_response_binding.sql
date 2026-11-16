-- Old evidence cannot be retroactively bound to a response. NULL remains unknown.
ALTER TABLE workspace_reporting_evidence
    ADD COLUMN tool_name varchar(128),
    ADD COLUMN response_sha256 bytea,
    ADD CONSTRAINT workspace_reporting_response_binding
        CHECK ((tool_name IS NULL AND response_sha256 IS NULL)
            OR (tool_name IS NOT NULL AND response_sha256 IS NOT NULL
                AND octet_length(response_sha256) = 32));
