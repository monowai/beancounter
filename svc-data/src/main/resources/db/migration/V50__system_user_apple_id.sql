-- V50: the subject Auth0 issues for a Sign in with Apple login, so an Apple user is
-- found without relying on the email claim. Blank until an Apple login is seen, as
-- auth0 and google_id are.
-- No index: system_user is small and the lookup runs once per uncached sign-in, as
-- the auth0 and google_id lookups do.
-- Rerun-safe and runs unchanged on H2 (MODE=MySQL) and Postgres.

ALTER TABLE system_user ADD COLUMN IF NOT EXISTS apple_id VARCHAR(255) NOT NULL DEFAULT '';
