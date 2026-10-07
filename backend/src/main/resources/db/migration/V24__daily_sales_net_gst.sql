-- Carry net and GST through reconciliation so sales.net / sales.gst are resolved metrics.
-- Nullable like total_sales: null while the date is unresolved.
ALTER TABLE resolved_daily_sales
    ADD COLUMN net_sales numeric(14,4),
    ADD COLUMN gst numeric(14,4);
