-- Add monthly_contribution column for pension/insurance contribution tracking
-- This stores the regular contribution amount set during onboarding
ALTER TABLE "private_asset_config" ADD COLUMN "monthly_contribution" DECIMAL(19,4);
