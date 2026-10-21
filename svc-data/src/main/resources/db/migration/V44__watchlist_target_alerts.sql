-- V44: Watchlist target alerts. A target is reached in the direction fixed when it is set
-- (target_direction: ABOVE when the target is at or above the price then, else BELOW) and
-- fires once: target_hit_on holds the close date it was reached on, null while armed.
-- Each firing is a watchlist_alert row, with the target and close on the close's share basis.
--
-- Existing targets are backfilled against the asset's latest close, falling back to the
-- added price. Statements are rerun-safe (IF [NOT] EXISTS) and run unchanged on H2
-- (MODE=MySQL) and Postgres. FK columns are VARCHAR(255) to match system_user.id and
-- asset.id. Alerts cascade with their item; asset deletes and offboarding clear them
-- explicitly first (the asset and owner FKs are NO ACTION, as on watchlist_item).

ALTER TABLE watchlist_item ADD COLUMN IF NOT EXISTS target_direction VARCHAR(5);
ALTER TABLE watchlist_item ADD COLUMN IF NOT EXISTS target_hit_on DATE;

UPDATE watchlist_item
SET target_direction =
    CASE
        WHEN target_price >= COALESCE(
            (SELECT md.close
             FROM market_data md
             WHERE md.asset_id = watchlist_item.asset_id
               AND md.close > 0
             ORDER BY md.price_date DESC
             LIMIT 1),
            added_price)
        THEN 'ABOVE'
        ELSE 'BELOW'
    END
WHERE target_price IS NOT NULL
  AND target_direction IS NULL;

ALTER TABLE watchlist_item DROP CONSTRAINT IF EXISTS ck_watchlist_item_target_direction;
-- A target always carries its direction.
ALTER TABLE watchlist_item ADD CONSTRAINT ck_watchlist_item_target_direction
    CHECK (target_price IS NULL OR target_direction IS NOT NULL);

ALTER TABLE watchlist_item DROP CONSTRAINT IF EXISTS ck_watchlist_item_direction_value;
ALTER TABLE watchlist_item ADD CONSTRAINT ck_watchlist_item_direction_value
    CHECK (target_direction IS NULL OR target_direction IN ('ABOVE', 'BELOW'));

CREATE TABLE IF NOT EXISTS watchlist_alert (
    id           VARCHAR(36)    PRIMARY KEY,
    owner_id     VARCHAR(255)   NOT NULL REFERENCES system_user (id),
    item_id      VARCHAR(36)    NOT NULL REFERENCES watchlist_item (id) ON DELETE CASCADE,
    asset_id     VARCHAR(255)   NOT NULL REFERENCES asset (id),
    target_price NUMERIC(19, 6) NOT NULL,
    direction    VARCHAR(5)     NOT NULL,
    close        NUMERIC(19, 6) NOT NULL,
    price_date   DATE           NOT NULL,
    created_at   TIMESTAMP      NOT NULL,
    CONSTRAINT ck_watchlist_alert_direction CHECK (direction IN ('ABOVE', 'BELOW'))
);

CREATE INDEX IF NOT EXISTS idx_watchlist_alert_owner ON watchlist_alert (owner_id, created_at);
CREATE INDEX IF NOT EXISTS idx_watchlist_alert_item ON watchlist_alert (item_id);
CREATE INDEX IF NOT EXISTS idx_watchlist_alert_asset ON watchlist_alert (asset_id);
