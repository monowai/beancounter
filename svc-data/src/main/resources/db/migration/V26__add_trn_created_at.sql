-- Adds a server-generated creation timestamp to trn so the cash-ladder
-- (and any future chronological view of transactions) can order
-- consistently within the same tradeDate. Trn ids are random strings
-- (UUID-style) so an `id desc` tie-break does NOT reflect chronology;
-- this column does.
--
-- Existing rows get a sensible-but-imperfect backfill: NOW() at
-- migration time. Order within historical same-date trns is unrecoverable
-- so they will all share the migration timestamp and sort by id within
-- that ms — acceptable for legacy data.

ALTER TABLE trn
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMP WITH TIME ZONE
    NOT NULL DEFAULT now();

-- Index supports the cash-ladder query's `tradeDate desc, createdAt desc`
-- secondary sort over the (portfolio, cashAsset) hot path.
CREATE INDEX IF NOT EXISTS idx_trn_tradedate_createdat
    ON trn (trade_date DESC, created_at DESC);
