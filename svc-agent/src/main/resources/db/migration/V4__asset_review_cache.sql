-- Canned single-asset reviews, kept per asset, day and model so a repeat review
-- is served without an LLM call. The review reads no user data, so one row
-- serves every caller. market is '' when the page sent none.
CREATE TABLE IF NOT EXISTS asset_review_cache
(
    id          VARCHAR(36)              NOT NULL PRIMARY KEY,
    market      VARCHAR(32)              NOT NULL,
    ticker      VARCHAR(64)              NOT NULL,
    review_date DATE                     NOT NULL,
    model       VARCHAR(100)             NOT NULL,
    content     TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_asset_review_cache UNIQUE (market, ticker, review_date, model)
);

-- Clear old days on write.
CREATE INDEX IF NOT EXISTS ix_asset_review_cache_date ON asset_review_cache (review_date);
