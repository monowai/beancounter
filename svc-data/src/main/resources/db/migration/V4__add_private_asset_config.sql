-- Configuration for private assets (Real Estate, etc.)
-- Stores income assumptions, expenses, and transaction generation settings

CREATE TABLE IF NOT EXISTS private_asset_config (
    asset_id VARCHAR(255) PRIMARY KEY,
    -- Income settings
    monthly_rental_income DECIMAL(19,4) DEFAULT 0,
    rental_currency VARCHAR(3) DEFAULT 'NZD',
    -- Expense settings
    monthly_management_fee DECIMAL(19,4) DEFAULT 0,
    management_fee_percent DECIMAL(5,4) DEFAULT 0,  -- Alternative: % of rental income
    -- Planning settings
    is_primary_residence BOOLEAN DEFAULT FALSE,
    liquidation_priority INT DEFAULT 100,
    -- Transaction generation settings
    transaction_day_of_month INT DEFAULT 1,         -- Day to generate monthly transaction
    credit_account_id VARCHAR(255),                 -- Cash account to credit rental income
    auto_generate_transactions BOOLEAN DEFAULT FALSE,
    -- Timestamps
    created_date DATE NOT NULL DEFAULT CURRENT_DATE,
    updated_date DATE NOT NULL DEFAULT CURRENT_DATE
);

-- Index for finding configs by owner (joins through asset table)
CREATE INDEX IF NOT EXISTS idx_private_asset_config_asset ON private_asset_config(asset_id);
