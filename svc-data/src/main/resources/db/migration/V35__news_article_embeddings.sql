-- Adds per-article embedding storage (rank-time dedup clustering + ingest-time derived-topic
-- tagging — see NewsEmbedder / EodhdNewsService) and a derived-topics join table, kept separate
-- from `news_article_tag` (the vendor's sparse EODHD tags) so the two provenances never mix.
--
-- Both embedding columns are nullable: populated only once
-- `beancounter.market.news.embedding.enabled=true` and an article has been (re)embedded by
-- EodhdNewsService's ingest seam. The feature is DEFAULT-OFF (NewsEmbeddingProperties) — the
-- embedding model runs out-of-process as a standalone bc-embed deployment, not yet shipped — so
-- these columns stay empty in every environment today.
ALTER TABLE news_article ADD COLUMN embedding BYTEA;
ALTER TABLE news_article ADD COLUMN embedding_model VARCHAR(64);

-- No index on `topic`: the only read is EodhdNewsService.matchesTopic, which filters in memory over
-- articles already loaded by ticker (findByTickersAfter). A topic index would be pure ingest-time
-- write overhead today — add one with the migration that introduces a DB-side topic query.
CREATE TABLE IF NOT EXISTS news_article_topic (
    article_id VARCHAR(36) NOT NULL,
    topic      VARCHAR(64) NOT NULL,
    PRIMARY KEY (article_id, topic),
    CONSTRAINT fk_natp_article FOREIGN KEY (article_id)
        REFERENCES news_article (id) ON DELETE CASCADE
);
