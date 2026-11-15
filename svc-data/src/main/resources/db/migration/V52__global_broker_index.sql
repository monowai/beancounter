-- Postgres does not index a foreign key column, and brokers are looked up by the firm they name.
CREATE INDEX IF NOT EXISTS idx_broker_global_broker ON broker (global_broker_id);
