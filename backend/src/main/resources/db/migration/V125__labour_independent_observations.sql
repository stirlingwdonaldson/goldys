-- A roster and a worked shift are distinct observations. Missing hours remain unknown.
ALTER TABLE canonical_labour_entry ALTER COLUMN scheduled_hours DROP NOT NULL;
ALTER TABLE canonical_labour_entry ALTER COLUMN actual_hours DROP NOT NULL;
ALTER TABLE canonical_labour_entry ADD COLUMN measure varchar(32) NOT NULL DEFAULT 'COMBINED';
ALTER TABLE canonical_labour_entry ADD CONSTRAINT labour_measure_valid
  CHECK (measure IN ('COMBINED', 'ROSTER', 'WORKED'));
ALTER TABLE canonical_labour_entry ALTER COLUMN scheduled_hours TYPE numeric(10,4);
ALTER TABLE canonical_labour_entry ALTER COLUMN actual_hours TYPE numeric(10,4);
