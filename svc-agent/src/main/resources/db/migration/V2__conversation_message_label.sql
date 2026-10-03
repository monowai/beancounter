-- Display text shown in place of a canned prompt (e.g. "News & Sentiment — NATO").
-- content keeps what the model was actually asked.
ALTER TABLE conversation_message ADD COLUMN IF NOT EXISTS label VARCHAR(200);
