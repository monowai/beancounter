-- V11: Introduce AccountingType entity
-- Combines category + currency + settlement metadata into a normalized entity.
-- Market is YAML config (no DB table), so only CASH/PRIVATE assets can be
-- seeded from existing data. Standard market assets get their accountingType
-- populated lazily by enrichers on next access.

-- ============================================
-- 1. Create accounting_type table and FK column on asset
-- ============================================
CREATE TABLE IF NOT EXISTS "accounting_type" (
    "id" VARCHAR(255) NOT NULL PRIMARY KEY,
    "category" VARCHAR(255) NOT NULL,
    "currency_code" VARCHAR(255) NOT NULL
        CONSTRAINT "fk_accounting_type_currency" REFERENCES "currency",
    "board_lot" INT NOT NULL DEFAULT 1,
    "settlement_days" INT NOT NULL DEFAULT 1,
    CONSTRAINT "uk_accounting_type_cat_ccy" UNIQUE ("category", "currency_code")
);

ALTER TABLE "asset" ADD COLUMN IF NOT EXISTS "accounting_type_id" VARCHAR(255)
    CONSTRAINT "fk_asset_accounting_type" REFERENCES "accounting_type";

-- ============================================
-- 2. Seed accounting_type rows for CASH/PRIVATE assets
-- ============================================
-- These assets store their currency in price_symbol; standard market assets
-- derive currency from Market config at runtime, so they are handled by
-- the enrichers (DefaultEnricher, PrivateMarketEnricher, etc.)
INSERT INTO "accounting_type" ("id", "category", "currency_code", "board_lot", "settlement_days")
SELECT
    gen_random_uuid()::text,
    UPPER(a."category"),
    a."price_symbol",
    1,
    0
FROM "asset" a
WHERE a."market_code" IN ('CASH', 'PRIVATE')
  AND a."price_symbol" IS NOT NULL
GROUP BY UPPER(a."category"), a."price_symbol"
ON CONFLICT DO NOTHING;

-- ============================================
-- 3. Populate asset.accounting_type_id FK for CASH/PRIVATE
-- ============================================
UPDATE "asset" a
SET "accounting_type_id" = (
    SELECT ac."id"
    FROM "accounting_type" ac
    WHERE ac."category" = UPPER(a."category")
      AND ac."currency_code" = a."price_symbol"
    LIMIT 1
)
WHERE a."market_code" IN ('CASH', 'PRIVATE')
  AND a."price_symbol" IS NOT NULL
  AND a."accounting_type_id" IS NULL;

-- ============================================
-- 4. Clear priceSymbol on CASH/PRIVATE assets
--    where it was only storing currency (not a real price symbol)
-- ============================================
UPDATE "asset" a
SET "price_symbol" = NULL
WHERE a."market_code" IN ('CASH', 'PRIVATE')
  AND a."price_symbol" IS NOT NULL;
