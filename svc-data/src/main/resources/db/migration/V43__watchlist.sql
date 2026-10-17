-- V43: Watchlist. Each user has one watchlist: the set of watchlist_item rows they own. An
-- item is an asset with no quantity, the price it was added at (in the asset's market
-- currency), the date added and an optional target price with the date it was entered.
-- Prices stay on the share basis of the day entered (raw, like market_data); reads rebase
-- them across later splits.
--
-- FK columns are VARCHAR(255) to match the Hibernate-created system_user.id and asset.id.
-- The asset FK is NO ACTION: asset deletes (AssetCascadeDeleter, offboarding) clear
-- watchlist_item rows explicitly first.

CREATE TABLE IF NOT EXISTS watchlist_item (
    id           VARCHAR(36)    PRIMARY KEY,
    owner_id     VARCHAR(255)   NOT NULL REFERENCES system_user (id),
    asset_id     VARCHAR(255)   NOT NULL REFERENCES asset (id),
    added_price  NUMERIC(19, 6) NOT NULL,
    added_on     DATE           NOT NULL,
    target_price NUMERIC(19, 6),
    target_on    DATE,
    CONSTRAINT uk_watchlist_item_owner_asset UNIQUE (owner_id, asset_id),
    -- A target always carries the date it was entered (its share basis).
    CONSTRAINT ck_watchlist_item_target_on CHECK (target_price IS NULL OR target_on IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_watchlist_item_asset ON watchlist_item (asset_id);
