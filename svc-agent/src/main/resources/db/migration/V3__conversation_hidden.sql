-- A hidden conversation holds a one-off answer the caller fetches by id (a mobile review
-- that outlived its connection); the history list leaves it out.
ALTER TABLE conversation ADD COLUMN IF NOT EXISTS hidden BOOLEAN NOT NULL DEFAULT FALSE;
