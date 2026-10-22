-- One row per answered AI turn by a caller without beancounter:ai, counted
-- over a rolling window for the weekly quota. owner_id is the SystemUser.id.
CREATE TABLE IF NOT EXISTS agent_usage
(
    id       VARCHAR(36)              NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36)              NOT NULL,
    used_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_agent_usage_owner_used ON agent_usage (owner_id, used_at);

-- An administrator's weekly limit for one user, replacing the configured default.
CREATE TABLE IF NOT EXISTS agent_quota_limit
(
    owner_id     VARCHAR(36)              NOT NULL PRIMARY KEY,
    weekly_limit INTEGER                  NOT NULL,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_agent_quota_limit_non_negative CHECK (weekly_limit >= 0)
);
