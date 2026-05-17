-- Persist EODHD news articles in Postgres so the /news endpoint can serve from the DB and only hit
-- the EODHD wire once per ticker every `refresh-after-hours` (default 6h). EODHD's news endpoint
-- carries its own ~1200 req/day quota shared with svc-agent's chat-driven LLM tool calls; without
-- this cache, sustained chat traffic would exhaust the quota long before the 100K/day EOD price
-- quota gets touched. Articles older than `retention-days` (default 30) are pruned by
-- NewsRetentionSchedule so the table can't grow without bound.
--
-- Schema mirrors the EODHD article shape with one row per article + join tables for the many-to-many
-- ticker tagging (EODHD ships `symbols[]` on every article) and tags. asset_id on the ticker join is
-- nullable because EODHD reports tickers BC doesn't necessarily track (e.g. `0ZG.F`) — we keep the
-- raw symbol so the LLM still sees the cross-name signal, while a populated asset_id makes
-- asset-keyed queries fast.

CREATE TABLE IF NOT EXISTS news_article (
    id              VARCHAR(36)   PRIMARY KEY,
    external_id     VARCHAR(2048) NOT NULL,
    published       TIMESTAMP     NOT NULL,
    title           VARCHAR(1024) NOT NULL,
    content         TEXT          NOT NULL,
    link            VARCHAR(2048) NOT NULL,
    polarity        DECIMAL(7, 4) NOT NULL DEFAULT 0,
    sentiment_pos   DECIMAL(7, 4) NOT NULL DEFAULT 0,
    sentiment_neg   DECIMAL(7, 4) NOT NULL DEFAULT 0,
    sentiment_neu   DECIMAL(7, 4) NOT NULL DEFAULT 0,
    source          VARCHAR(16)   NOT NULL,
    fetched_at      TIMESTAMP     NOT NULL,
    CONSTRAINT uk_news_article_external UNIQUE (external_id)
);

CREATE INDEX IF NOT EXISTS idx_news_article_published ON news_article (published);

CREATE TABLE IF NOT EXISTS news_article_ticker (
    article_id      VARCHAR(36) NOT NULL,
    ticker          VARCHAR(32) NOT NULL,
    asset_id        VARCHAR(36),
    PRIMARY KEY (article_id, ticker),
    CONSTRAINT fk_nat_article FOREIGN KEY (article_id)
        REFERENCES news_article (id) ON DELETE CASCADE,
    CONSTRAINT fk_nat_asset   FOREIGN KEY (asset_id)
        REFERENCES asset (id)        ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_nat_ticker   ON news_article_ticker (ticker);
CREATE INDEX IF NOT EXISTS idx_nat_asset_id ON news_article_ticker (asset_id);

CREATE TABLE IF NOT EXISTS news_article_tag (
    article_id      VARCHAR(36) NOT NULL,
    tag             VARCHAR(64) NOT NULL,
    PRIMARY KEY (article_id, tag),
    CONSTRAINT fk_natg_article FOREIGN KEY (article_id)
        REFERENCES news_article (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_natg_tag ON news_article_tag (tag);

CREATE TABLE IF NOT EXISTS news_fetch (
    ticker           VARCHAR(32) PRIMARY KEY,
    last_fetched_at  TIMESTAMP   NOT NULL,
    articles_found   INTEGER     NOT NULL DEFAULT 0
);
