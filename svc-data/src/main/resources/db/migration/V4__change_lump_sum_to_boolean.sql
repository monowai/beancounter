-- Change lump_sum_payout_amount (DECIMAL) to lump_sum (BOOLEAN)
-- Data preservation not required

-- Drop old column if it exists
ALTER TABLE private_asset_config DROP COLUMN IF EXISTS lump_sum_payout_amount;

-- Add new boolean column
ALTER TABLE private_asset_config ADD COLUMN IF NOT EXISTS lump_sum BOOLEAN DEFAULT FALSE;
