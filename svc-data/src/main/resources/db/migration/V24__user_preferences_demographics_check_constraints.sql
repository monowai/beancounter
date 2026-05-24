-- Defence-in-depth CHECK constraints for the demographics added in V23.
-- Originally landed in V23 (PR #891) but that retroactively broke the
-- checksum on already-deployed kauri — V23 was applied without CHECK,
-- so the in-jar V23 must stay byte-for-byte identical. Splitting the
-- constraints into V24 lets them deploy cleanly forward.
--
-- Literal upper bound on year_of_birth (not EXTRACT(YEAR FROM CURRENT_DATE))
-- keeps the migration portable across H2 (test) and Postgres (prod); the
-- far-future ceiling still catches anything physically plausible going wrong.
ALTER TABLE user_preferences
    ADD CONSTRAINT chk_user_preferences_year_of_birth
        CHECK (year_of_birth IS NULL OR year_of_birth BETWEEN 1900 AND 2150),
    ADD CONSTRAINT chk_user_preferences_month_of_birth
        CHECK (month_of_birth IS NULL OR month_of_birth BETWEEN 1 AND 12),
    ADD CONSTRAINT chk_user_preferences_life_expectancy
        CHECK (life_expectancy IS NULL OR life_expectancy BETWEEN 1 AND 130);
