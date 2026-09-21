-- Email-keyed account carrying the password hash and the (department, seniority) pair
-- permission decisions use. The OIDC-keyed staff_profile is dropped in V8, once the code
-- that reads it has been re-keyed (the swap is atomic).
CREATE TABLE user_account (
    id uuid PRIMARY KEY,
    email varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    department varchar(100) NOT NULL,
    seniority varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT user_account_email_key UNIQUE (email)
);

CREATE INDEX idx_user_account_role
    ON user_account (department, seniority) WHERE active = true;
