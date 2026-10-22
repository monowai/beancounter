-- When each SystemUser was last seen: stamped on their own /me call, at most
-- once an hour. Shown to administrators beside the user list.
CREATE TABLE IF NOT EXISTS user_activity
(
    system_user_id VARCHAR(36)              NOT NULL PRIMARY KEY,
    last_seen      TIMESTAMP WITH TIME ZONE NOT NULL
);
