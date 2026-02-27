-- V16: Add milestone tracking tables and milestone mode preference

-- Earned milestones (one row per milestone per user)
CREATE TABLE user_milestone (
    id          VARCHAR(36) PRIMARY KEY,
    owner_id    VARCHAR(36) NOT NULL REFERENCES system_user(id),
    milestone_id VARCHAR(64) NOT NULL,
    tier        INT NOT NULL DEFAULT 1,
    earned_at   DATE NOT NULL,
    CONSTRAINT uk_user_milestone UNIQUE(owner_id, milestone_id)
);

-- Explorer action flags (tracks feature discovery)
CREATE TABLE user_explorer_action (
    id          VARCHAR(36) PRIMARY KEY,
    owner_id    VARCHAR(36) NOT NULL REFERENCES system_user(id),
    action_id   VARCHAR(64) NOT NULL,
    recorded_at DATE NOT NULL,
    CONSTRAINT uk_user_explorer_action UNIQUE(owner_id, action_id)
);

-- Add milestone mode to user preferences
ALTER TABLE user_preferences
    ADD COLUMN IF NOT EXISTS milestone_mode VARCHAR(10) DEFAULT 'ACTIVE';
