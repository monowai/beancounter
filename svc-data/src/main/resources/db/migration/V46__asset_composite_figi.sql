-- V46: OpenFIGI composite FIGI on asset. Null until FIGI resolves the asset. Filled on
-- create by the FIGI enricher and for existing assets by POST /assets/figi/backfill.
-- Not unique: BC markets that share a FIGI country (NASDAQ, NYSE, AMEX all map to US)
-- can hold the same instrument. No index: nothing looks assets up by it yet.
-- Rerun-safe and runs unchanged on H2 (MODE=MySQL) and Postgres.

ALTER TABLE asset ADD COLUMN IF NOT EXISTS composite_figi VARCHAR(12);
