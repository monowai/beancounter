-- V43: Watchlists. A user owns many named watchlists (id/code/name, like portfolios); each
-- holds assets with no quantity. watchlist_item records the price the asset was added at (in
-- the asset's market currency), the date added and an optional target price with the date
-- it was entered. Prices stay on the share basis of the day entered (raw, like market_data);
-- reads rebase them across later splits.
--
-- FK columns are VARCHAR(255) to match the Hibernate-created system_user.id and asset.id.
-- Deleting a watchlist cascades to its items. The asset FK is NO ACTION: asset deletes
-- (AssetCascadeDeleter, offboarding) clear watchlist_item rows explicitly first.

CREATE TABLE IF NOT EXISTS watchlist (
    id         VARCHAR(36)  PRIMARY KEY,
    owner_id   VARCHAR(255) NOT NULL REFERENCES system_user (id),
    code       VARCHAR(255) NOT NULL,
    name       VARCHAR(255) NOT NULL,
    created_at DATE         NOT NULL,
    CONSTRAINT uk_watchlist_owner_code UNIQUE (owner_id, code)
);

CREATE TABLE IF NOT EXISTS watchlist_item (
    id           VARCHAR(36)    PRIMARY KEY,
    watchlist_id VARCHAR(36)    NOT NULL REFERENCES watchlist (id) ON DELETE CASCADE,
    asset_id     VARCHAR(255)   NOT NULL REFERENCES asset (id),
    added_price  NUMERIC(19, 6) NOT NULL,
    added_on     DATE           NOT NULL,
    target_price NUMERIC(19, 6),
    target_on    DATE,
    CONSTRAINT uk_watchlist_item_asset UNIQUE (watchlist_id, asset_id),
    -- A target always carries the date it was entered (its share basis).
    CONSTRAINT ck_watchlist_item_target_on CHECK (target_price IS NULL OR target_on IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_watchlist_item_asset ON watchlist_item (asset_id);
