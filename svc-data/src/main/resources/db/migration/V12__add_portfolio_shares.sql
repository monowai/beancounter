-- Portfolio sharing: client-adviser relationship
-- portfolio_id is nullable for adviser access requests (no portfolio selected yet)
CREATE TABLE IF NOT EXISTS portfolio_share (
    id VARCHAR(36) PRIMARY KEY,
    portfolio_id VARCHAR(255) REFERENCES portfolio(id) ON DELETE CASCADE,
    shared_with_id VARCHAR(255) NOT NULL REFERENCES system_user(id) ON DELETE CASCADE,
    access_level VARCHAR(20) NOT NULL DEFAULT 'FULL',
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_CLIENT_INVITE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id VARCHAR(255) NOT NULL REFERENCES system_user(id),
    target_user_id VARCHAR(255) REFERENCES system_user(id),
    accepted_at TIMESTAMP,
    CONSTRAINT uk_portfolio_share UNIQUE (portfolio_id, shared_with_id)
);

CREATE INDEX IF NOT EXISTS idx_portfolio_share_shared_with ON portfolio_share(shared_with_id, status);
CREATE INDEX IF NOT EXISTS idx_portfolio_share_portfolio ON portfolio_share(portfolio_id, status);
CREATE INDEX IF NOT EXISTS idx_portfolio_share_target ON portfolio_share(target_user_id, status);

-- Future: track who made a trade on a managed portfolio
ALTER TABLE trn ADD COLUMN IF NOT EXISTS created_by_id VARCHAR(255) REFERENCES system_user(id);
