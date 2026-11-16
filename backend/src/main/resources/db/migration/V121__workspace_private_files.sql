-- Private exploratory bytes are not source ingestion or authoritative business records.
CREATE TABLE workspace_file (
    id uuid PRIMARY KEY,
    job_id uuid NOT NULL REFERENCES workspace_job(id) ON DELETE CASCADE,
    name varchar(120) NOT NULL,
    kind varchar(16) NOT NULL CHECK (kind IN ('UPLOAD','ARTIFACT')),
    payload bytea,
    byte_length bigint NOT NULL CHECK (byte_length BETWEEN 1 AND 26214400),
    sha256 bytea NOT NULL,
    dependency_kind varchar(40) NOT NULL DEFAULT 'WHOLE_CONTEXT_UNCLASSIFIED',
    kept boolean NOT NULL DEFAULT false,
    expires_at timestamp(6) with time zone NOT NULL DEFAULT now() + interval '30 days',
    created_at timestamp(6) with time zone NOT NULL DEFAULT now()
);
CREATE INDEX workspace_file_job ON workspace_file(job_id);
INSERT INTO permission(id,department,seniority,resource,can_read,can_write)
VALUES (gen_random_uuid(),'ALL','OWNER','workspace.private-files',true,true);
