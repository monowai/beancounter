-- V8: Add composite policy asset support (CPF, ILP, etc.)
-- Extends private_asset_config with policy type and lock date.
-- Creates sub-account table for composite assets.

-- ============================================
-- Private Asset Config: Composite policy fields
-- ============================================
ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS policy_type VARCHAR(20);

ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS locked_until_date DATE;

-- ============================================
-- Private Asset Sub-Account: Per-bucket balances
-- ============================================
CREATE TABLE IF NOT EXISTS private_asset_sub_account (
    id VARCHAR(36) PRIMARY KEY,
    asset_id VARCHAR(255) NOT NULL,
    code VARCHAR(20) NOT NULL,
    display_name VARCHAR(100),
    balance DECIMAL(19,4) NOT NULL DEFAULT 0,
    expected_return_rate DECIMAL(5,4),
    fee_rate DECIMAL(5,4),
    liquid BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_sub_account_asset
        FOREIGN KEY (asset_id)
        REFERENCES private_asset_config(asset_id)
        ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sub_account_asset_code
    ON private_asset_sub_account(asset_id, code);
