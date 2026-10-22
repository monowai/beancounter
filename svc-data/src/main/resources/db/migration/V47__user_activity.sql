-- When each SystemUser was last seen: stamped on their own /me call, at most
-- once an hour. Shown to administrators beside the user list. The key is
-- VARCHAR(255) to match system_user.id, and cascades with the user like the
-- other user-scoped tables.
CREATE TABLE IF NOT EXISTS user_activity
(
    system_user_id VARCHAR(255)             NOT NULL PRIMARY KEY,
    last_seen      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_activity_user FOREIGN KEY (system_user_id) REFERENCES system_user (id) ON DELETE CASCADE
);
