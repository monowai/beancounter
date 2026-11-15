-- Brokerage firms known to every user: reference data with no owner. config is a
-- JSON document read by BrokerConfigConverter.
CREATE TABLE IF NOT EXISTS global_broker (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    code VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    config TEXT NOT NULL,
    CONSTRAINT uk_global_broker_code UNIQUE (code)
);

-- A user's broker may name the firm it is an account with.
ALTER TABLE broker ADD COLUMN IF NOT EXISTS global_broker_id VARCHAR(255)
    CONSTRAINT fk_broker_global_broker REFERENCES global_broker(id);

INSERT INTO global_broker (id, code, name, config)
SELECT 'gb7Qm2XvT4kLs9RdWn3HcA', 'SCHWAB', 'Charles Schwab',
       '{"aliases":["Charles Schwab","TD Ameritrade","TD"],"market":"US","currency":"USD"}'
WHERE NOT EXISTS (SELECT 1 FROM global_broker WHERE code = 'SCHWAB');

INSERT INTO global_broker (id, code, name, config)
SELECT 'gbP8zYe1NfU6oJb5KtVx2w', 'ETRADE', 'E*TRADE',
       '{"aliases":["E*TRADE","ETrade","E-Trade","Morgan Stanley"],"market":"US","currency":"USD"}'
WHERE NOT EXISTS (SELECT 1 FROM global_broker WHERE code = 'ETRADE');

INSERT INTO global_broker (id, code, name, config)
SELECT 'gbD3aGh9SiC1rMq7ZuEy4Q', 'ROBINHOOD', 'Robinhood',
       '{"aliases":["Robinhood","Robin Hood"],"market":"US","currency":"USD"}'
WHERE NOT EXISTS (SELECT 1 FROM global_broker WHERE code = 'ROBINHOOD');
