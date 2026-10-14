-- Review classification: read-only ML output keyed by logical review identity (the Review ID
-- string), mirroring how product_category_assignment is keyed by product_name_key. Derived state,
-- not bitemporal: re-classification upserts the single row for a review (latest-wins), so there is
-- no valid_from/valid_to/superseded_at/recorded_at or logical_entity_id here.

CREATE TABLE review_classification (
    review_id varchar(512) PRIMARY KEY,
    sentiment varchar(16) NOT NULL,
    sentiment_confidence numeric(6,4) NOT NULL,
    predicted_rating numeric(3,2),
    aspects jsonb NOT NULL,
    classified_at timestamp(6) with time zone NOT NULL
);

-- Observability for the backfill runner only (on-ingest classification is accounted for by the
-- existing ingestion-run stage). Mirrors classification_run.
CREATE TABLE review_classification_run (
    id uuid PRIMARY KEY,
    kind varchar(16) NOT NULL,
    status varchar(16) NOT NULL,
    review_count int,
    started_at timestamp(6) with time zone NOT NULL,
    finished_at timestamp(6) with time zone,
    error_message text
);
