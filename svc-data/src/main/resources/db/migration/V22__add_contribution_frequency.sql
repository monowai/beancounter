-- Pension/policy contribution cadence. Existing rows default to MONTHLY so
-- the column can be NOT NULL without backfill input. Work Scenario stores
-- the contribution amount; this resolves how it is annualised by the
-- retirement projection (MONTHLY × 12, ANNUAL × 1).
ALTER TABLE private_asset_config
    ADD COLUMN contribution_frequency VARCHAR(10) NOT NULL DEFAULT 'MONTHLY';
