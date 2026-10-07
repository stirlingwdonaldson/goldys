-- Dashboards v2: filters, visibility, pinning, revision history, role sharing.
-- Widgets move from {id, tool, input} to {id, renderType, queries[], layout}. Pre-launch:
-- no production dashboards exist, so the known tools are mapped and any other widget is dropped.

ALTER TABLE saved_dashboard
    ADD COLUMN filters jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN visibility varchar(16) NOT NULL DEFAULT 'PRIVATE',
    ADD COLUMN pinned boolean NOT NULL DEFAULT false,
    ADD COLUMN current_revision integer NOT NULL DEFAULT 1;

CREATE TABLE saved_dashboard_revision (
    id uuid PRIMARY KEY,
    dashboard_id uuid NOT NULL REFERENCES saved_dashboard(id) ON DELETE CASCADE,
    revision integer NOT NULL,
    document jsonb NOT NULL,
    created_by varchar(255) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uq_dashboard_revision UNIQUE (dashboard_id, revision)
);

CREATE TABLE saved_dashboard_share (
    id uuid PRIMARY KEY,
    dashboard_id uuid NOT NULL REFERENCES saved_dashboard(id) ON DELETE CASCADE,
    department varchar(64) NOT NULL,
    seniority varchar(64) NOT NULL,
    CONSTRAINT uq_dashboard_share UNIQUE (dashboard_id, department, seniority)
);

-- Map the four known tools to MetricQuery[]; drop anything else (pre-launch test data).
-- jsonb_agg(...) FILTER (WHERE ...) drops the NULL rows (unknown tools) cleanly, so a dashboard
-- whose widgets are all unknown becomes '[]' rather than '[null]'.
UPDATE saved_dashboard
SET widgets = COALESCE(
  (SELECT jsonb_agg(mapped) FILTER (WHERE mapped IS NOT NULL)
   FROM (
     SELECT CASE
       WHEN w->>'tool' = 'GET_SALES_BY_PERIOD' THEN
         jsonb_build_object(
           'id', w->>'id', 'renderType', 'time-series', 'layout', '{"w":6,"h":2}'::jsonb,
           'queries', jsonb_build_array(jsonb_build_object(
             'metric', COALESCE(w->'input'->>'metric','sales.gross'),
             'range', jsonb_build_object('from', w->'input'->>'startDate','to', w->'input'->>'endDate','calendar','CALENDAR'),
             'grain', 'DAY', 'dimensions', '[]'::jsonb)))
       WHEN w->>'tool' IN ('GET_LABOUR_VARIANCE','GET_FOOD_COST','GET_RESERVATION_SUMMARY') THEN
         jsonb_build_object(
           'id', w->>'id', 'renderType', 'table', 'layout', '{"w":12,"h":2}'::jsonb,
           'queries', jsonb_build_array(jsonb_build_object(
             'metric', 'sales.gross',
             'range', jsonb_build_object('from', COALESCE(w->'input'->>'startDate', w->'input'->>'date'),'to', COALESCE(w->'input'->>'endDate', w->'input'->>'date'),'calendar','CALENDAR'),
             'grain', 'DAY', 'dimensions', '[]'::jsonb)))
       ELSE NULL
     END AS mapped
     FROM jsonb_array_elements(widgets) AS w) AS mapped_widgets),
  '[]'::jsonb);
