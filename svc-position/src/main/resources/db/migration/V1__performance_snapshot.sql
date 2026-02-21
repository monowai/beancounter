CREATE TABLE IF NOT EXISTS performance_snapshot (
    id VARCHAR(36) PRIMARY KEY,
    portfolio_id VARCHAR(36) NOT NULL,
    valuation_date DATE NOT NULL,
    market_value DECIMAL(19,4) NOT NULL,
    external_cash_flow DECIMAL(19,4) NOT NULL DEFAULT 0,
    net_contributions DECIMAL(19,4) NOT NULL DEFAULT 0,
    cumulative_dividends DECIMAL(19,4) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_portfolio_date UNIQUE (portfolio_id, valuation_date)
);

CREATE INDEX idx_perf_portfolio ON performance_snapshot(portfolio_id);
CREATE INDEX idx_perf_date ON performance_snapshot(valuation_date);
