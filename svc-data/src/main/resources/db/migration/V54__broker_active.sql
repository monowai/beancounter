-- A user deactivates a broker they no longer trade through: it keeps its
-- transactions and is no longer offered for a new trade. Every existing broker
-- stays active.
ALTER TABLE broker ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
