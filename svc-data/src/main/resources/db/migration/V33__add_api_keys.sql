-- BC-issued API keys for programmatic/agent access (MCP, phase 1 - bc-claude/MCP.md).
-- Only the SHA-256 hash of the key is stored; the raw key is never persisted.
CREATE TABLE IF NOT EXISTS api_key (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL REFERENCES system_user (id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    prefix VARCHAR(16) NOT NULL,
    key_hash VARCHAR(64) NOT NULL,
    scopes VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    revoked_at TIMESTAMP,
    CONSTRAINT uk_api_key_hash UNIQUE (key_hash)
);

CREATE INDEX IF NOT EXISTS idx_api_key_owner ON api_key (owner_id);
