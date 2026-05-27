-- Auto-settle cash to linked funding portfolio.
--
-- Adds the funding-portfolio link in two places:
--   - portfolio.cash_portfolio_id  : per-portfolio override
--   - system_user.cash_portfolio_id: per-user default
--
-- Grouping of an auto-settled trio (parent trade + WITHDRAWAL + DEPOSIT) rides
-- the existing caller_ref columns (provider/batch/caller_id) on trn. The W+D
-- pair stamps provider = 'BC-AUTO' and batch = parent.caller_id; the index
-- below speeds sibling lookup for the unsettle / delete prompts.

ALTER TABLE portfolio
    ADD COLUMN IF NOT EXISTS cash_portfolio_id VARCHAR(255) NULL
    REFERENCES portfolio(id);

ALTER TABLE system_user
    ADD COLUMN IF NOT EXISTS cash_portfolio_id VARCHAR(255) NULL
    REFERENCES portfolio(id);

CREATE INDEX IF NOT EXISTS idx_trn_caller_batch_provider
    ON trn (batch, provider);
