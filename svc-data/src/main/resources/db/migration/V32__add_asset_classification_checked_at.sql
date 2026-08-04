-- V32: resume-aware classification refresh (bc-claude fix/classification-refresh-resume).
-- Records when an asset's classification/sector-exposure data was last attempted so successive
-- refresh runs make forward progress instead of re-processing the same head of the list every
-- time (and burning AlphaVantage's 25 requests/day free-tier quota on the same 15 assets).
ALTER TABLE asset
    ADD COLUMN IF NOT EXISTS classification_checked_at DATE;
