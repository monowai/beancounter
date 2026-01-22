-- Create broker settlement account table for currency -> account mapping
CREATE TABLE IF NOT EXISTS broker_settlement_account (
    id VARCHAR(255) NOT NULL,
    broker_id VARCHAR(255) NOT NULL,
    currency_code VARCHAR(10) NOT NULL,
    account_id VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_settlement_broker FOREIGN KEY (broker_id) REFERENCES broker(id),
    CONSTRAINT fk_settlement_account FOREIGN KEY (account_id) REFERENCES asset(id),
    CONSTRAINT uk_broker_currency UNIQUE (broker_id, currency_code)
);

CREATE INDEX IF NOT EXISTS idx_settlement_broker ON broker_settlement_account(broker_id);
