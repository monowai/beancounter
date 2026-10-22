-- An administrator can grant a user unlimited AI turns. weekly_limit is
-- ignored while unlimited is true; a weekly_limit of 0 disables AI for the user.
ALTER TABLE agent_quota_limit
    ADD COLUMN IF NOT EXISTS unlimited BOOLEAN NOT NULL DEFAULT FALSE;

-- An unlimited grant always stores weekly_limit 0, so the two states never mix.
ALTER TABLE agent_quota_limit
    ADD CONSTRAINT ck_agent_quota_limit_unlimited_zero CHECK (NOT unlimited OR weekly_limit = 0);
