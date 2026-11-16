-- Unknown historical provenance must never be asserted from today's grants.
ALTER TABLE conversation_thread ADD COLUMN policy_stamp varchar(64);
