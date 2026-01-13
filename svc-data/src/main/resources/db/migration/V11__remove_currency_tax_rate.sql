-- Remove income_tax_rate from currency table
-- Tax rates are now stored per-user per-country in the tax_rate table
ALTER TABLE currency DROP COLUMN IF EXISTS income_tax_rate;
