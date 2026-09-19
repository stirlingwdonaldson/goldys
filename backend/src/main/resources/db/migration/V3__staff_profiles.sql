-- Local staff profile keyed by OIDC issuer + subject. Department and seniority are
-- application-enforced codes; the concrete field-to-role matrix remains a stakeholder input and
-- this table ships empty, exactly like the V1 permission table.
CREATE TABLE staff_profile (
    id uuid PRIMARY KEY,
    oidc_issuer varchar(512) NOT NULL,
    oidc_subject varchar(255) NOT NULL,
    display_name varchar(255) NOT NULL,
    department varchar(100) NOT NULL,
    seniority varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT staff_profile_oidc_identity_key UNIQUE (oidc_issuer, oidc_subject)
);

CREATE INDEX idx_staff_profile_role
    ON staff_profile (department, seniority) WHERE active = true;
