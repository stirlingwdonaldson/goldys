-- Saved dashboards: declarative dashboard documents. Widgets store query configuration
-- (tool + bounded input) as JSONB, never generated code or embedded snapshot data.

CREATE TABLE saved_dashboard (
    id uuid PRIMARY KEY,
    title varchar(200) NOT NULL,
    description varchar(500),
    layout varchar(32) NOT NULL,
    widgets jsonb NOT NULL,
    created_by varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

-- Gate the saved-dashboards feature: the owner (ALL x OWNER) may read and write dashboards.
INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'dashboards', true, true);
