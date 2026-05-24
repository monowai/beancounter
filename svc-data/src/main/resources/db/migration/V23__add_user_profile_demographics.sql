-- Profile demographics mirrored from svc-retire's UserIndependenceSettings.
-- Source of truth still lives in svc-retire; svc-data holds a denormalised
-- read copy so screens scoped to svc-data (Edit Asset, holdings views) can
-- compute the user's age without a runtime dependency on svc-retire.
-- CHECK constraints are the simplest defence-in-depth against garbage
-- values (negative ages, month 13, life expectancy 999) poisoning the
-- age math downstream.
ALTER TABLE user_preferences
    ADD COLUMN year_of_birth INT,
    ADD COLUMN month_of_birth INT,
    ADD COLUMN life_expectancy INT,
    -- Literal upper bound (not EXTRACT(YEAR FROM CURRENT_DATE)) keeps the
    -- constraint portable across H2 (test) and Postgres (prod); a far-future
    -- ceiling still catches anything physically plausible going wrong.
    ADD CONSTRAINT chk_user_preferences_year_of_birth
        CHECK (year_of_birth IS NULL OR year_of_birth BETWEEN 1900 AND 2150),
    ADD CONSTRAINT chk_user_preferences_month_of_birth
        CHECK (month_of_birth IS NULL OR month_of_birth BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_user_preferences_life_expectancy
        CHECK (life_expectancy IS NULL OR life_expectancy BETWEEN 1 AND 130);
