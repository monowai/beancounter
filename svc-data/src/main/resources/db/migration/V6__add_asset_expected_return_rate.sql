-- Add expected return rate field to asset table
ALTER TABLE asset ADD COLUMN expected_return_rate DOUBLE PRECISION;
