-- V45: OpenFIGI composite FIGI on asset. Null until FIGI resolves the asset; filled on
-- create by the FIGI enricher and for existing assets by POST /assets/figi/backfill.
-- Not unique: BC markets that share a FIGI country (NASDAQ, NYSE, AMEX all map to US)
-- can hold the same instrument. Rerun-safe; runs unchanged on H2 (MODE=MySQL) and Postgres.

ALTER TABLE asset ADD COLUMN IF NOT EXISTS composite_figi VARCHAR(12);
CREATE INDEX IF NOT EXISTS idx_asset_composite_figi ON asset (composite_figi);
