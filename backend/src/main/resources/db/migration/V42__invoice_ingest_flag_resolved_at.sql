-- Invoice-ingestion flags become resolvable. A replay with a better PDF matcher can retire a flag
-- whose line now matches, without deleting the append-only anomaly record: the row stays as history
-- and merely leaves the open list. Nullable because production already holds unresolved flags.
alter table invoice_ingest_flag
    add column resolved_at timestamp(6) with time zone;

-- The read API and the reconciliation pass only ever look at open flags, so index just those.
create index idx_invoice_ingest_flag_open
    on invoice_ingest_flag (flag_type, occurred_at desc)
    where resolved_at is null;
