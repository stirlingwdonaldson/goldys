-- Keep revisions after grant deletion so restored grants cannot revive old context.
CREATE TABLE workspace_role_policy (
    department varchar(255) NOT NULL,
    seniority varchar(255) NOT NULL,
    revision uuid NOT NULL DEFAULT gen_random_uuid(),
    PRIMARY KEY (department, seniority)
);
INSERT INTO workspace_role_policy(department, seniority)
SELECT DISTINCT department, seniority FROM permission;

CREATE FUNCTION workspace_advance_role_policy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP <> 'INSERT' THEN
        INSERT INTO workspace_role_policy(department, seniority) VALUES (OLD.department, OLD.seniority)
        ON CONFLICT (department, seniority) DO UPDATE SET revision = gen_random_uuid();
    END IF;
    IF TG_OP <> 'DELETE' THEN
        INSERT INTO workspace_role_policy(department, seniority) VALUES (NEW.department, NEW.seniority)
        ON CONFLICT (department, seniority) DO UPDATE SET revision = gen_random_uuid();
    END IF;
    RETURN NULL;
END;
$$;
CREATE TRIGGER workspace_permission_revision AFTER INSERT OR UPDATE OR DELETE ON permission
FOR EACH ROW EXECUTE FUNCTION workspace_advance_role_policy();

ALTER TABLE user_account ADD COLUMN workspace_policy_revision uuid NOT NULL DEFAULT gen_random_uuid();
CREATE FUNCTION workspace_advance_account_policy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.workspace_policy_revision := gen_random_uuid();
    RETURN NEW;
END;
$$;
CREATE TRIGGER workspace_account_revision BEFORE UPDATE ON user_account
FOR EACH ROW EXECUTE FUNCTION workspace_advance_account_policy();
