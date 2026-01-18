-- Add pension-specific fields to private_asset_config
-- Using IF NOT EXISTS for H2/PostgreSQL compatibility
ALTER TABLE private_asset_config ADD COLUMN IF NOT EXISTS expected_return_rate DECIMAL(5,4);
ALTER TABLE private_asset_config ADD COLUMN IF NOT EXISTS payout_age INTEGER;
ALTER TABLE private_asset_config ADD COLUMN IF NOT EXISTS monthly_payout_amount DECIMAL(19,4);
ALTER TABLE private_asset_config ADD COLUMN IF NOT EXISTS is_pension BOOLEAN DEFAULT FALSE;
