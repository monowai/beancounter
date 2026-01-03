-- Add provider column to fx_rate table
-- This allows caching FX rates from different providers (FRANKFURTER, EXCHANGE_RATES_API)

-- Add the provider column with a default value
-- Existing records were from EXCHANGE_RATES_API (legacy provider)
ALTER TABLE "fx_rate" ADD COLUMN IF NOT EXISTS "provider" VARCHAR(50) DEFAULT 'EXCHANGE_RATES_API';

-- Update existing records to EXCHANGE_RATES_API (the legacy provider)
UPDATE "fx_rate" SET "provider" = 'EXCHANGE_RATES_API' WHERE "provider" IS NULL;

-- Make the column NOT NULL after setting defaults
ALTER TABLE "fx_rate" ALTER COLUMN "provider" SET NOT NULL;

-- Rename columns to match JPA @JoinColumn names if they exist with old names
DO $$
BEGIN
    -- Rename from_code to from_id if it exists
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'fx_rate' AND column_name = 'from_code') THEN
        ALTER TABLE "fx_rate" RENAME COLUMN "from_code" TO "from_id";
    END IF;

    -- Rename to_code to to_id if it exists
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'fx_rate' AND column_name = 'to_code') THEN
        ALTER TABLE "fx_rate" RENAME COLUMN "to_code" TO "to_id";
    END IF;
END $$;

-- Drop existing unique constraints on the table (JPA will recreate with correct columns)
DO $$
DECLARE
    constraint_rec RECORD;
BEGIN
    FOR constraint_rec IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = '"fx_rate"'::regclass
        AND contype = 'u'
    LOOP
        EXECUTE 'ALTER TABLE "fx_rate" DROP CONSTRAINT IF EXISTS "' || constraint_rec.conname || '"';
    END LOOP;
EXCEPTION WHEN OTHERS THEN
    NULL;
END $$;

-- Note: JPA with ddl-auto:update will recreate the unique constraint
-- with columns (from_id, to_id, date, provider) based on the @Table annotation
