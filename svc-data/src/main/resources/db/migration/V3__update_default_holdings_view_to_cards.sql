-- Add CARDS to the allowed values for default_holdings_view
ALTER TABLE user_preferences
DROP CONSTRAINT IF EXISTS user_preferences_default_holdings_view_check;

ALTER TABLE user_preferences
ADD CONSTRAINT user_preferences_default_holdings_view_check
CHECK (default_holdings_view IN ('SUMMARY', 'CARDS', 'HEATMAP', 'TABLE', 'ALLOCATION'));

-- Migrate users with SUMMARY default holdings view to CARDS
UPDATE user_preferences
SET default_holdings_view = 'CARDS'
WHERE default_holdings_view = 'SUMMARY';
