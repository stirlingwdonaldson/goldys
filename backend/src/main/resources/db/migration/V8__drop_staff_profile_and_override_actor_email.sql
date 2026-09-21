-- The OIDC-keyed staff_profile is superseded by user_account (V7). Dropping it here keeps the
-- swap atomic: the code that read it is re-keyed in this same task.
DROP TABLE staff_profile;

-- The override actor identity was OIDC issuer+subject; with email+password auth it is the email.
ALTER TABLE daily_sales_override DROP COLUMN actor_oidc_issuer;
ALTER TABLE daily_sales_override DROP COLUMN actor_oidc_subject;
ALTER TABLE daily_sales_override ADD COLUMN actor_email varchar(255) NOT NULL;
