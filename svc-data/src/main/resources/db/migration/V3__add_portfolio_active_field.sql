-- Add active field to portfolio table for inactive portfolio support
-- Default to true so existing portfolios remain active
ALTER TABLE portfolio ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;
