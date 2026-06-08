-- Backfill for the POLICY BALANCE tradeCurrency leak.
--
-- bc-view's link-composite flow hardcoded `currency = "USD"` as a placeholder
-- on every standalone composite config. That value rode the BALANCE trn into
-- svc-data, baking USD into `trn.trade_currency_code` for SGD-denominated CPF
-- positions. svc-position then computed the PORTFOLIO and BASE buckets by
-- multiplying the TRADE amount by an unintended FX rate (~1.28 USD->SGD for
-- Mary's CPF), inflating market value by ~28% across /wealth and the plan
-- projection's liquidAssets.
--
-- Server-side defense (TrnInputMapper) now overrides tradeCurrency from the
-- asset's accountingType for POLICY assets, so no new wrong rows can land.
-- This migration repairs any pre-existing rows by:
--   1. Replacing trade_currency_code with the asset's accounting_type currency
--      for POLICY BALANCE trns where the two diverge.
--   2. Resetting trade_portfolio_rate / trade_base_rate to 1 when the now-
--      corrected trade currency matches the portfolio currency / base. Cross-
--      currency POLICY (none today, but ILP later) keeps its existing rates;
--      operators recompute manually if a future case appears.

WITH bad_trns AS (
    SELECT t.id,
           at.currency_code AS correct_currency,
           p.currency_code  AS portfolio_currency,
           p.base_code      AS portfolio_base
    FROM trn t
    JOIN asset a              ON a.id = t.asset_id
    JOIN accounting_type at   ON at.id = a.accounting_type_id
    JOIN portfolio p          ON p.id = t.portfolio_id
    WHERE t.trn_type = 'BALANCE'
      AND a.category = 'POLICY'
      AND t.trade_currency_code <> at.currency_code
)
UPDATE trn t
SET trade_currency_code  = bt.correct_currency,
    trade_portfolio_rate = CASE WHEN bt.correct_currency = bt.portfolio_currency
                                THEN 1
                                ELSE t.trade_portfolio_rate END,
    trade_base_rate      = CASE WHEN bt.correct_currency = bt.portfolio_base
                                THEN 1
                                ELSE t.trade_base_rate END,
    trade_cash_rate      = CASE WHEN bt.correct_currency = bt.portfolio_currency
                                THEN 1
                                ELSE t.trade_cash_rate END,
    cash_currency_code   = CASE WHEN t.cash_currency_code IS NOT NULL
                                  AND t.cash_currency_code <> bt.correct_currency
                                THEN bt.correct_currency
                                ELSE t.cash_currency_code END
FROM bad_trns bt
WHERE t.id = bt.id;
