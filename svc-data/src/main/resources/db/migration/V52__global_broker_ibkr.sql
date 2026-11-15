-- Interactive Brokers trades many markets in many currencies, so its config names
-- no default market or currency: each holding carries its own.
INSERT INTO global_broker (id, code, name, config)
SELECT 'gbK5wTn8LpB2xVj6HrFs9M', 'IBKR', 'Interactive Brokers',
       '{"aliases":["Interactive Brokers","IBKR","IB"],"market":"","currency":""}'
WHERE NOT EXISTS (SELECT 1 FROM global_broker WHERE code = 'IBKR');
