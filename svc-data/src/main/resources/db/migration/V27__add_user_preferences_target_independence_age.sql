-- Move svc-data UserPreferences toward being the master for user demographics
-- (currently svc-retire's UserIndependenceSettings writes and mirrors back
-- through DemographicsBackfillRunner). Target-independence age lived only
-- in svc-retire; add it here so the onboarding flow can persist it
-- against the user account and svc-retire can read it back as the user's
-- account-wide default. See bc-claude/USER_PROFILE.md.
ALTER TABLE user_preferences
    ADD COLUMN target_independence_age INT,
    ADD CONSTRAINT chk_user_preferences_target_independence_age
        CHECK (target_independence_age IS NULL OR target_independence_age BETWEEN 18 AND 100);
