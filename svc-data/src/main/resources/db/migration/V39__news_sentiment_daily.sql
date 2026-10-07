-- EODHD daily aggregated news sentiment per asset (GET /api/sentiments), refreshed by
-- NewsSentimentSchedule and served read-only by GET /news/sentiment. One row per
-- (asset_id, price_date); re-fetches overwrite the row so late-arriving article counts
-- land on the same day rather than duplicating it.
--
-- Mirrors market_data's composite-key style: surrogate id (KeyGenUtils) plus a UNIQUE
-- constraint on the natural key. No FK to asset — offboarding / asset deletion must not
-- be blocked by a sentiment cache row; the daily refresh simply stops writing for it.
--
-- symbol:        the fully-qualified EODHD ticker the row was fetched under (CODE.EXCHANGE),
--                kept for debugging symbol routing.
-- article_count: EODHD `count` — articles aggregated into that day's score.
-- normalized:    EODHD `normalized`, in [-1, 1].

CREATE TABLE IF NOT EXISTS news_sentiment_daily (
    id            VARCHAR(36)   PRIMARY KEY,
    asset_id      VARCHAR(36)   NOT NULL,
    price_date    DATE          NOT NULL,
    symbol        VARCHAR(32)   NOT NULL,
    article_count INTEGER       NOT NULL,
    normalized    NUMERIC(6, 4) NOT NULL,
    fetched_at    TIMESTAMP     NOT NULL,
    CONSTRAINT uk_news_sentiment_daily UNIQUE (asset_id, price_date)
);
