-- Migration: Convert all enum columns from ordinal (integer) to string storage
-- This migration handles existing data by mapping ordinal values to their string equivalents
--
-- Note: Hibernate uses snake_case naming strategy, so column names are:
--   trn_type (not trnType), etc.
--
-- Tables affected (bc database):
--   trn: trn_type (TrnType), status (TrnStatus)
--   asset: status (Status)
--
-- Note: corporate_event table is in ev database (svc-event)
--
-- TrnType ordinal mapping:
-- 0=SELL, 1=BUY, 2=SPLIT, 3=DEPOSIT, 4=WITHDRAWAL, 5=DIVI, 6=FX_BUY,
-- 7=IGNORE, 8=BALANCE, 9=ADD, 10=INCOME, 11=DEDUCTION, 12=REDUCE
--
-- TrnStatus ordinal mapping:
-- 0=CONFIRMED, 1=PROPOSED
--
-- Status ordinal mapping:
-- 0=Active, 1=Inactive

-- Step 1: Add temporary columns for new string values
ALTER TABLE "trn" ADD COLUMN "trn_type_new" VARCHAR(20);
ALTER TABLE "trn" ADD COLUMN "status_new" VARCHAR(20);

-- Step 2: Migrate TrnType data from ordinal to string
UPDATE "trn" SET "trn_type_new" = CASE "trn_type"
    WHEN 0 THEN 'SELL'
    WHEN 1 THEN 'BUY'
    WHEN 2 THEN 'SPLIT'
    WHEN 3 THEN 'DEPOSIT'
    WHEN 4 THEN 'WITHDRAWAL'
    WHEN 5 THEN 'DIVI'
    WHEN 6 THEN 'FX_BUY'
    WHEN 7 THEN 'IGNORE'
    WHEN 8 THEN 'BALANCE'
    WHEN 9 THEN 'ADD'
    WHEN 10 THEN 'INCOME'
    WHEN 11 THEN 'DEDUCTION'
    WHEN 12 THEN 'REDUCE'
    ELSE 'BUY'
END;

-- Step 3: Migrate TrnStatus data from ordinal to string
UPDATE "trn" SET "status_new" = CASE "status"
    WHEN 0 THEN 'CONFIRMED'
    WHEN 1 THEN 'PROPOSED'
    ELSE 'CONFIRMED'
END;

-- Step 4: Drop old columns
ALTER TABLE "trn" DROP COLUMN "trn_type";
ALTER TABLE "trn" DROP COLUMN "status";

-- Step 5: Rename new columns to original names
ALTER TABLE "trn" RENAME COLUMN "trn_type_new" TO "trn_type";
ALTER TABLE "trn" RENAME COLUMN "status_new" TO "status";

-- Step 6: Add NOT NULL constraints
ALTER TABLE "trn" ALTER COLUMN "trn_type" SET NOT NULL;
ALTER TABLE "trn" ALTER COLUMN "status" SET NOT NULL;
ALTER TABLE "trn" ALTER COLUMN "status" SET DEFAULT 'CONFIRMED';

-- ============================================================================
-- ASSET TABLE: Migrate status from ordinal to string
-- ============================================================================

-- Step 7: Add temporary column
ALTER TABLE "asset" ADD COLUMN "status_new" VARCHAR(20);

-- Step 8: Migrate Status data
UPDATE "asset" SET "status_new" = CASE "status"
    WHEN 0 THEN 'Active'
    WHEN 1 THEN 'Inactive'
    ELSE 'Active'
END;

-- Step 9: Drop old column and rename new
ALTER TABLE "asset" DROP COLUMN "status";
ALTER TABLE "asset" RENAME COLUMN "status_new" TO "status";

-- Step 10: Add NOT NULL constraint
ALTER TABLE "asset" ALTER COLUMN "status" SET NOT NULL;
ALTER TABLE "asset" ALTER COLUMN "status" SET DEFAULT 'Active';
