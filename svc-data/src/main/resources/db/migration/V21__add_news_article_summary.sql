-- Add a `summary` column for the AI-bot read path so the projection can serve a
-- pre-condensed essence instead of truncating `content` at query time. Nullable
-- because EODHD's `/api/news` doesn't currently ship a summary field — the
-- column is forward-compatible for an upstream summary or a future ingest-time
-- summariser (LLM or otherwise). Read path falls back to `content.take(N)`
-- when summary IS NULL so existing rows keep working.
ALTER TABLE news_article ADD COLUMN summary VARCHAR(1024);
