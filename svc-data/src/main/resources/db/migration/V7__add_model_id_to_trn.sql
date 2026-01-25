-- Add model_id column to track which rebalance model a transaction belongs to
-- This enables filtering positions by model for model-level rebalancing
ALTER TABLE trn ADD COLUMN model_id VARCHAR(36);

-- Index for efficient queries by model
CREATE INDEX idx_trn_model_id ON trn(model_id);

-- Composite index for portfolio + model queries
CREATE INDEX idx_trn_portfolio_model ON trn(portfolio_id, model_id);
