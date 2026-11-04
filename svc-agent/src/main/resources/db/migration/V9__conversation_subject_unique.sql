-- One thread per subject for an owner. Earlier duplicates from a findOrCreate
-- race keep their turns as ordinary threads: the most recently updated one
-- stays the subject's, the others lose the subject.
UPDATE conversation c
SET subject = NULL
WHERE c.subject IS NOT NULL
  AND EXISTS (SELECT 1
              FROM conversation n
              WHERE n.owner_id = c.owner_id
                AND n.subject = c.subject
                AND (n.updated_at > c.updated_at OR (n.updated_at = c.updated_at AND n.id > c.id)));

-- NULL subjects are distinct in a unique index on Postgres and H2, so
-- ordinary threads are unconstrained.
DROP INDEX IF EXISTS idx_conversation_owner_subject;
CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_owner_subject ON conversation (owner_id, subject);
