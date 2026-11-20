-- V56: Recurring payslip. A user marks a payslip as recurring monthly; svc-data keeps one row
-- per user and a daily job publishes a "payslip due" message for each row whose next pay date
-- has arrived. svc-retire calculates the transactions and svc-data records them, then moves
-- next_pay_date on by one month. day_of_month is the day the user is paid; a shorter month
-- clamps next_pay_date to its last day without losing the day.
--
-- FK columns are VARCHAR(255) to match the Hibernate-created system_user.id and portfolio.id.
-- Both FKs are NO ACTION: portfolio delete and offboarding remove the row explicitly first.
-- cash_asset_id carries no FK so an asset delete never blocks on it; the job skips a row whose
-- cash asset has gone.

CREATE TABLE IF NOT EXISTS recurring_payslip (
    id            VARCHAR(36)    PRIMARY KEY,
    owner_id      VARCHAR(255)   NOT NULL REFERENCES system_user (id),
    portfolio_id  VARCHAR(255)   NOT NULL REFERENCES portfolio (id),
    cash_asset_id VARCHAR(255)   NOT NULL,
    gross_salary  NUMERIC(19, 4) NOT NULL,
    tax           NUMERIC(19, 4) NOT NULL DEFAULT 0,
    day_of_month  INTEGER        NOT NULL,
    next_pay_date DATE           NOT NULL,
    CONSTRAINT uk_recurring_payslip_owner UNIQUE (owner_id),
    CONSTRAINT ck_recurring_payslip_day_of_month CHECK (day_of_month BETWEEN 1 AND 31),
    CONSTRAINT ck_recurring_payslip_gross_salary CHECK (gross_salary > 0),
    CONSTRAINT ck_recurring_payslip_tax CHECK (tax >= 0)
);

CREATE INDEX IF NOT EXISTS idx_recurring_payslip_next_pay_date ON recurring_payslip (next_pay_date);
CREATE INDEX IF NOT EXISTS idx_recurring_payslip_portfolio ON recurring_payslip (portfolio_id);
