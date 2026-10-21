-- V45: V44 backfilled target_direction by comparing the stored target (on its target_on
-- share basis) with the latest close (on its own), without rebasing across splits between
-- them. Flag the armed targets V44 backfilled; the next target check re-derives each side
-- against a close on one share basis and clears the flag. Setting a target clears it too.
-- Targets set on or before the day V44 ran are treated as backfilled. Rerun-safe; runs
-- unchanged on H2 (MODE=MySQL) and Postgres.

ALTER TABLE watchlist_item ADD COLUMN IF NOT EXISTS direction_unverified BOOLEAN DEFAULT FALSE NOT NULL;

UPDATE watchlist_item
SET direction_unverified = TRUE
WHERE target_price IS NOT NULL
  AND target_hit_on IS NULL
  AND target_on <= (SELECT CAST(MAX(installed_on) AS DATE)
                    FROM flyway_schema_history
                    WHERE version = '44');
