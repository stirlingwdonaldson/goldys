-- Add time-of-day to canonical sale items and deleted sales, parsed from the Lightspeed
-- Insights "Minute 10" report dimensions (an HHMM integer in venue-local time, e.g. 2150 = 21:50).
--
-- Nullable: rows delivered by reports that predate the minute fields, or that carry a blank
-- value, stay null. There is no second-level precision in the source; "Minute 10" is the finest
-- granularity available (10-minute buckets).
ALTER TABLE canonical_sale_item
    ADD COLUMN sale_closed_time time without time zone;

ALTER TABLE canonical_deleted_sale
    ADD COLUMN sale_opened_time time without time zone;
