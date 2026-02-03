-- V9: Merge PENSION category into POLICY (now "Retirement Fund")
-- Existing PENSION assets are re-categorized to POLICY.

UPDATE asset
SET category = 'POLICY'
WHERE category = 'PENSION';
