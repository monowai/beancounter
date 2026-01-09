-- Add reportingCurrencyCode column to user_preferences
ALTER TABLE user_preferences
ADD COLUMN IF NOT EXISTS reporting_currency_code VARCHAR(3) DEFAULT 'USD';
