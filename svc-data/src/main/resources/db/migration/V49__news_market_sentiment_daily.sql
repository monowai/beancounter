-- Daily Positive / Neutral / Negative article counts per market scope — one macro topic
-- (e.g. inflation) or one sector (e.g. technology) — rolled up from news_article by
-- MarketSentimentService and served read-only by GET /news/sentiment/market.
--
-- news_article is pruned after 30 days; this table is not, so the sentiment trend outlives the
-- articles it was counted from. One row per (scope_kind, scope_name, news_date); a re-run
-- overwrites the row so late-arriving articles land on the same day.
--
-- scope_kind: TOPIC or SECTOR. Scopes are never summed together — each keeps its own series.
-- polarity:   mean article polarity for that scope and day, in [-1, 1].

CREATE TABLE IF NOT EXISTS news_market_sentiment_daily (
    id         VARCHAR(36)   PRIMARY KEY,
    scope_kind VARCHAR(16)   NOT NULL,
    scope_name VARCHAR(64)   NOT NULL,
    news_date  DATE          NOT NULL,
    positive   INTEGER       NOT NULL,
    neutral    INTEGER       NOT NULL,
    negative   INTEGER       NOT NULL,
    polarity   NUMERIC(6, 4) NOT NULL,
    updated_at TIMESTAMP     NOT NULL,
    CONSTRAINT uk_news_market_sentiment_daily UNIQUE (scope_kind, scope_name, news_date)
);
