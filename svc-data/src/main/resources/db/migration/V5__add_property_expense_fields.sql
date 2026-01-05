-- Add additional expense fields for property income calculations
-- These expenses reduce the net rental income available for retirement projections

ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS monthly_body_corporate_fee DECIMAL(19,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS annual_property_tax DECIMAL(19,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS annual_insurance DECIMAL(19,4) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS monthly_other_expenses DECIMAL(19,4) DEFAULT 0;

COMMENT ON COLUMN private_asset_config.monthly_body_corporate_fee IS 'Body corporate/HOA/strata fees (monthly)';
COMMENT ON COLUMN private_asset_config.annual_property_tax IS 'Council rates/property tax (annual amount)';
COMMENT ON COLUMN private_asset_config.annual_insurance IS 'Landlord/building insurance (annual amount)';
COMMENT ON COLUMN private_asset_config.monthly_other_expenses IS 'Maintenance reserve and other recurring expenses (monthly)';
