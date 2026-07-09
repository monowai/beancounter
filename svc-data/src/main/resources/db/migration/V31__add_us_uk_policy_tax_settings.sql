-- V31: US 401k/IRA + UK ISA support (svc-retire#152 parent).
-- Adds tax-treatment + accumulation assumption columns to private_asset_config
-- so composite policy configs can model pre-tax/Roth/tax-free retirement
-- accounts alongside the existing CPF/GENERIC types.
ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS tax_treatment VARCHAR(20);

ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS employee_deferral_percent DECIMAL(5,4);

ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS employer_match_percent DECIMAL(5,4);

ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS employer_match_cap_percent DECIMAL(5,4);

ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS withdrawal_tax_rate DECIMAL(5,4);
