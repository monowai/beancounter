-- One fiscal-year fundamentals snapshot per asset, sourced from SEC EDGAR companyfacts (XBRL)
-- by SecClassificationEnricher. Keyed by asset_id so a refresh overwrites in place; every
-- measure is nullable because a filer that does not report a tag must still get a row.
-- Mirrors macro_observation's style (V34): IF NOT EXISTS keeps this a no-op where Hibernate
-- ddl-auto created the table first, and it runs unchanged on H2 (MODE=MySQL) and Postgres.
--
-- source:              provider discriminator, "SEC" today.
-- fiscal_year_end:     period end of the fiscal year the measures describe.
-- eps_diluted:         EarningsPerShareDiluted (USD/share).
-- revenue:             Revenues, else RevenueFromContractWithCustomerExcludingAssessedTax.
-- net_income:          NetIncomeLoss.
-- dividends_per_share: CommonStockDividendsPerShareDeclared (USD/share).
-- shares_outstanding:  dei:EntityCommonStockSharesOutstanding, latest instant.
-- as_of:               date the snapshot was taken.

CREATE TABLE IF NOT EXISTS asset_fundamentals (
    asset_id            VARCHAR(255)   PRIMARY KEY,
    source              VARCHAR(16)    NOT NULL,
    fiscal_year_end     DATE           NOT NULL,
    fiscal_year         INTEGER        NOT NULL,
    eps_diluted         NUMERIC(19, 4),
    revenue             NUMERIC(24, 2),
    net_income          NUMERIC(24, 2),
    dividends_per_share NUMERIC(19, 6),
    shares_outstanding  BIGINT,
    as_of               DATE           NOT NULL
);
