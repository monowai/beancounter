-- Opt-in flag for PRIVATE-market assets in svc-position time-weighted return.
--
-- PRIVATE valuations move on appraisal or snapshot cycles, not market prices, so they
-- stay out of TWR unless the owner sets this. Existing rows default to FALSE (opted out).
-- IF NOT EXISTS keeps this a no-op where Hibernate ddl-auto added the column first, and
-- runs unchanged on H2 (tests, MODE=MySQL) and Postgres (prod) - see
-- AssetIncludeInPerformanceMigrationTest.
ALTER TABLE asset ADD COLUMN IF NOT EXISTS include_in_performance BOOLEAN NOT NULL DEFAULT FALSE;
