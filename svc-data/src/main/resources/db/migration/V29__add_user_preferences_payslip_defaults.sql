-- Remember the bc-view "Enter Payslip" feature's default target portfolio and
-- cash asset against the user's account-wide preferences (served by GET /me).
-- Both nullable, no default — absent until the user first saves a payslip.
ALTER TABLE user_preferences
    ADD COLUMN default_payslip_portfolio_id VARCHAR(255),
    ADD COLUMN default_payslip_cash_asset_id VARCHAR(255);
