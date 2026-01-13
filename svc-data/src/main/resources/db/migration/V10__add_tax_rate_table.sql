-- Add country_code to private_asset_config for tax jurisdiction
ALTER TABLE private_asset_config ADD COLUMN country_code VARCHAR(2) DEFAULT 'NZ';

-- Create tax_rate table for user-defined tax rates per country
-- Each user can configure their own tax rates for different jurisdictions

CREATE TABLE tax_rate (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL,
    country_code VARCHAR(2) NOT NULL,
    rate DECIMAL(5,4) NOT NULL DEFAULT 0,
    created_date DATE NOT NULL,
    updated_date DATE NOT NULL,
    CONSTRAINT fk_tax_rate_owner FOREIGN KEY (owner_id) REFERENCES system_user(id),
    CONSTRAINT uk_tax_rate_owner_country UNIQUE (owner_id, country_code)
);

CREATE INDEX idx_tax_rate_owner ON tax_rate(owner_id);
