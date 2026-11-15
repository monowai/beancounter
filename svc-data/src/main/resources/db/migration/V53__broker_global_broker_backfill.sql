-- Links each broker a user already has to the firm its name states: the firm's name, code or
-- an alias seeded in V51 and V52, compared without case or outer spaces.
-- A broker that already names a firm is left alone, so a re-run changes nothing.
UPDATE broker
SET global_broker_id = (SELECT id FROM global_broker WHERE code = 'SCHWAB')
WHERE global_broker_id IS NULL
  AND LOWER(TRIM(name)) IN ('charles schwab', 'schwab', 'td ameritrade', 'td');

UPDATE broker
SET global_broker_id = (SELECT id FROM global_broker WHERE code = 'ETRADE')
WHERE global_broker_id IS NULL
  AND LOWER(TRIM(name)) IN ('e*trade', 'etrade', 'e-trade', 'morgan stanley');

UPDATE broker
SET global_broker_id = (SELECT id FROM global_broker WHERE code = 'ROBINHOOD')
WHERE global_broker_id IS NULL
  AND LOWER(TRIM(name)) IN ('robinhood', 'robin hood');

UPDATE broker
SET global_broker_id = (SELECT id FROM global_broker WHERE code = 'IBKR')
WHERE global_broker_id IS NULL
  AND LOWER(TRIM(name)) IN ('interactive brokers', 'ibkr', 'ib');
