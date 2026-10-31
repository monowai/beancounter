-- What a conversation is about when one thread belongs to it, such as a
-- Portfolio Review of an exact set of portfolios.
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS subject VARCHAR(512);
CREATE INDEX IF NOT EXISTS idx_conversation_owner_subject ON conversation (owner_id, subject);
