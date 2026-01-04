-- Migrate users with SUMMARY default holdings view to CARDS
UPDATE user_preferences
SET default_holdings_view = 'CARDS'
WHERE default_holdings_view = 'SUMMARY';
