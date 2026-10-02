-- Agent conversation history: one row per chat, its turns in conversation_message.
-- owner_id is the caller's SystemUser.id (resolved via svc-data /me), never the JWT sub.
CREATE TABLE IF NOT EXISTS conversation
(
    id         VARCHAR(36)              NOT NULL PRIMARY KEY,
    owner_id   VARCHAR(36)              NOT NULL,
    title      VARCHAR(120)             NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- List a user's conversations newest-first; find idle ones for the retention purge.
CREATE INDEX IF NOT EXISTS ix_conversation_owner_updated ON conversation (owner_id, updated_at);
CREATE INDEX IF NOT EXISTS ix_conversation_updated ON conversation (updated_at);

CREATE TABLE IF NOT EXISTS conversation_message
(
    id              VARCHAR(36)              NOT NULL PRIMARY KEY,
    conversation_id VARCHAR(36)              NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    seq             INTEGER                  NOT NULL,
    role            VARCHAR(16)              NOT NULL,
    content         TEXT                     NOT NULL,
    deep_think      BOOLEAN                  NOT NULL,
    error           VARCHAR(64),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_conversation_message_seq UNIQUE (conversation_id, seq)
);
