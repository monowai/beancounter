-- Migration: Convert corporate_event.trn_type from ordinal (integer) to string storage
-- This migration handles existing data by mapping ordinal values to their string equivalents
--
-- Note: Hibernate uses snake_case naming strategy, so column names are:
--   trn_type (not trnType)
--
-- Tables affected (ev database):
--   corporate_event: trn_type (TrnType)
--
-- TrnType ordinal mapping:
-- 0=SELL, 1=BUY, 2=SPLIT, 3=DEPOSIT, 4=WITHDRAWAL, 5=DIVI, 6=FX_BUY,
-- 7=IGNORE, 8=BALANCE, 9=ADD, 10=INCOME, 11=DEDUCTION, 12=REDUCE

-- Step 1: Add temporary column
ALTER TABLE "corporate_event" ADD COLUMN "trn_type_new" VARCHAR(20);

-- Step 2: Migrate TrnType data from ordinal to string
UPDATE "corporate_event" SET "trn_type_new" = CASE "trn_type"
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
    ELSE 'DIVI'
END;

-- Step 3: Drop old column and rename new
ALTER TABLE "corporate_event" DROP COLUMN "trn_type";
ALTER TABLE "corporate_event" RENAME COLUMN "trn_type_new" TO "trn_type";

-- Step 4: Add NOT NULL constraint
ALTER TABLE "corporate_event" ALTER COLUMN "trn_type" SET NOT NULL;
ALTER TABLE "corporate_event" ALTER COLUMN "trn_type" SET DEFAULT 'DIVI';
