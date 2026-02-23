-- V15: Add CPF-specific configuration fields
ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS cpf_life_plan VARCHAR(20);

ALTER TABLE private_asset_config
    ADD COLUMN IF NOT EXISTS cpf_payout_start_age INTEGER;
