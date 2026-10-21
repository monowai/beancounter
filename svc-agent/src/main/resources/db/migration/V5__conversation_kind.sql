-- What a conversation is: a free chat, an Asset Review, or a Holding Analysis.
-- Set by its first turn; existing conversations are chats.
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS kind VARCHAR(32) NOT NULL DEFAULT 'CHAT';
