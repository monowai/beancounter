-- Add income tax rate to currency table (for jurisdiction-based tax rates)
ALTER TABLE currency ADD COLUMN income_tax_rate DECIMAL(5,4) DEFAULT 0;

-- Add deduct_income_tax flag to private_asset_config
-- When true, income tax is calculated using the rate from the rental currency
ALTER TABLE private_asset_config ADD COLUMN deduct_income_tax BOOLEAN DEFAULT FALSE;
