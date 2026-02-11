CREATE TABLE IF NOT EXISTS resource_share (
    id                VARCHAR(36) PRIMARY KEY,
    resource_type     VARCHAR(30) NOT NULL,
    resource_id       VARCHAR(255) NOT NULL,
    resource_name     VARCHAR(255),
    shared_with_id    VARCHAR(255) NOT NULL REFERENCES system_user(id) ON DELETE CASCADE,
    access_level      VARCHAR(20) NOT NULL DEFAULT 'VIEW',
    status            VARCHAR(30) NOT NULL DEFAULT 'PENDING_CLIENT_INVITE',
    created_at        TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by_id     VARCHAR(255) NOT NULL REFERENCES system_user(id),
    resource_owner_id VARCHAR(255) NOT NULL REFERENCES system_user(id),
    target_user_id    VARCHAR(255) REFERENCES system_user(id),
    accepted_at       TIMESTAMP,
    CONSTRAINT uk_resource_share UNIQUE (resource_type, resource_id, shared_with_id)
);

CREATE INDEX IF NOT EXISTS idx_resource_share_shared_with
    ON resource_share(shared_with_id, status);
CREATE INDEX IF NOT EXISTS idx_resource_share_resource
    ON resource_share(resource_type, resource_id, status);
CREATE INDEX IF NOT EXISTS idx_resource_share_target
    ON resource_share(target_user_id, status);
CREATE INDEX IF NOT EXISTS idx_resource_share_owner
    ON resource_share(resource_owner_id, resource_type, status);
