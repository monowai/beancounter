-- When a transaction's cost was paid, for performance only. A holding recorded on a
-- statement's record date keeps the date it was bought at its cost here, so IRR runs
-- from then while quantity, splits and dividends follow trade_date. Null means
-- trade_date.
ALTER TABLE trn ADD COLUMN IF NOT EXISTS cost_date DATE;
