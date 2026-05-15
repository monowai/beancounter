-- Strip the leading `^` from INDEX-market asset codes so EODHD's `.INDX`
-- exchange suffix composes cleanly (e.g. `GSPC.INDX` instead of `^GSPC.INDX`).
--
-- Originally INDEX assets shipped with Yahoo-style codes (`^GSPC`, `^IXIC`,
-- `^DJI`, `^FTSE`) because AlphaVantage's index time-series required the caret.
-- Kauri's AV key never had INDEX_DATA entitlement so that path was always
-- broken; EODHD's EOD Historical plan covers indices natively but uses bare
-- symbols. Renaming once here keeps the pre-seed and stored rows consistent.
--
-- The seed-runner is idempotent on (code, marketCode) so simply changing the
-- yaml without this migration would orphan existing `^X` rows. Idempotent +
-- safe to re-run: the WHERE clause filters by both the caret prefix and the
-- INDEX market.
UPDATE asset
   SET code = SUBSTR(code, 2)
 WHERE market_code = 'INDEX'
   AND code LIKE '^%';
