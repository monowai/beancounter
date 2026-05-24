-- Profile demographics mirrored from svc-retire's UserIndependenceSettings.
-- Source of truth still lives in svc-retire; svc-data holds a denormalised
-- read copy so screens scoped to svc-data (Edit Asset, holdings views) can
-- compute the user's age without a runtime dependency on svc-retire.
ALTER TABLE user_preferences
    ADD COLUMN year_of_birth INT,
    ADD COLUMN month_of_birth INT,
    ADD COLUMN life_expectancy INT;
