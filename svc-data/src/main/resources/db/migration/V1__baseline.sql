-- V1: Baseline schema supplements for svc-data
-- Hibernate generates the core schema from entities; this adds constraints, indexes, and additional tables.
-- Consolidated from V2-V11 migrations (2024-2025)

-- ============================================
-- FX Rate: Add provider column for multi-provider support
-- ============================================
ALTER TABLE "fx_rate" ADD COLUMN IF NOT EXISTS "provider" VARCHAR(50) NOT NULL DEFAULT 'EXCHANGE_RATES_API';

-- ============================================
-- User Preferences: Add holdings view constraint and reporting currency
-- ============================================
ALTER TABLE user_preferences
DROP CONSTRAINT IF EXISTS user_preferences_default_holdings_view_check;

ALTER TABLE user_preferences
ADD CONSTRAINT user_preferences_default_holdings_view_check
CHECK (default_holdings_view IN ('CARDS', 'HEATMAP', 'TABLE', 'ALLOCATION'));

ALTER TABLE user_preferences
ADD COLUMN IF NOT EXISTS reporting_currency_code VARCHAR(3) DEFAULT 'USD';

-- ============================================
-- System User: Unique email constraint
-- ============================================
ALTER TABLE system_user
DROP CONSTRAINT IF EXISTS uk_system_user_email;

ALTER TABLE system_user
ADD CONSTRAINT uk_system_user_email UNIQUE (email);

-- ============================================
-- Asset: Expected return rate for retirement projections
-- ============================================
ALTER TABLE asset ADD COLUMN IF NOT EXISTS expected_return_rate DOUBLE PRECISION;

-- ============================================
-- Private Asset Config: RE and private asset management
-- ============================================
CREATE TABLE IF NOT EXISTS private_asset_config (
    asset_id VARCHAR(255) PRIMARY KEY,
    -- Income settings
    monthly_rental_income DECIMAL(19,4) DEFAULT 0,
    rental_currency VARCHAR(3) DEFAULT 'NZD',
    -- Expense settings
    monthly_management_fee DECIMAL(19,4) DEFAULT 0,
    management_fee_percent DECIMAL(5,4) DEFAULT 0,
    monthly_body_corporate_fee DECIMAL(19,4) DEFAULT 0,
    annual_property_tax DECIMAL(19,4) DEFAULT 0,
    annual_insurance DECIMAL(19,4) DEFAULT 0,
    monthly_other_expenses DECIMAL(19,4) DEFAULT 0,
    -- Tax settings
    deduct_income_tax BOOLEAN DEFAULT FALSE,
    country_code VARCHAR(2) DEFAULT 'NZ',
    -- Planning settings
    is_primary_residence BOOLEAN DEFAULT FALSE,
    liquidation_priority INT DEFAULT 100,
    -- Transaction generation settings
    transaction_day_of_month INT DEFAULT 1,
    credit_account_id VARCHAR(255),
    auto_generate_transactions BOOLEAN DEFAULT FALSE,
    -- Timestamps
    created_date DATE NOT NULL DEFAULT CURRENT_DATE,
    updated_date DATE NOT NULL DEFAULT CURRENT_DATE
);

CREATE INDEX IF NOT EXISTS idx_private_asset_config_asset ON private_asset_config(asset_id);

-- ============================================
-- Tax Rate: User-defined tax rates per country
-- ============================================
CREATE TABLE IF NOT EXISTS tax_rate (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    rate DECIMAL(5,4) NOT NULL DEFAULT 0,
    created_date DATE NOT NULL,
    updated_date DATE NOT NULL,
    CONSTRAINT fk_tax_rate_owner FOREIGN KEY (owner_id) REFERENCES system_user(id),
    CONSTRAINT uk_tax_rate_owner_country UNIQUE (owner_id, country_code)
);

CREATE INDEX IF NOT EXISTS idx_tax_rate_owner ON tax_rate(owner_id);
