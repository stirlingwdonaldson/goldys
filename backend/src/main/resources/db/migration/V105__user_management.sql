-- One role for now: every active account is an owner. The existing account rows were
-- created by the old public sign-up at ALL x STAFF, so promote them all (including
-- inactive rows, so reactivating one cannot leave an account with no access).
ALTER TABLE user_account
    ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;

UPDATE user_account SET department = 'ALL', seniority = 'OWNER';

INSERT INTO permission (id, department, seniority, resource, can_read, can_write) VALUES
    (gen_random_uuid(), 'ALL', 'OWNER', 'users', true, true);
