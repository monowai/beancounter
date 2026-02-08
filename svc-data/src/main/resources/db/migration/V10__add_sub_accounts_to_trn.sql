-- Add sub_accounts JSONB column to trn table
ALTER TABLE trn ADD COLUMN sub_accounts JSONB;

-- Backfill existing DEPOSIT transactions for POLICY assets
-- with sub-account data from the asset config
UPDATE trn t
SET sub_accounts = (
    SELECT jsonb_object_agg(sa.code, sa.balance)
    FROM private_asset_sub_account sa
    WHERE sa.asset_id = t.asset_id
      AND sa.balance > 0
)
WHERE t.trn_type = 'DEPOSIT'
  AND EXISTS (
    SELECT 1 FROM private_asset_sub_account sa
    WHERE sa.asset_id = t.asset_id
      AND sa.balance > 0
  );
