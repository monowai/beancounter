-- V48: a widget key names the device key it was registered under. Null for every
-- other key. Revoking the parent revokes its children in ApiKeyService (revoke is a
-- soft delete), and a hard delete of the parent drops them here.
-- No index: a parent has at most one active child and lookups run per revoke.
-- Rerun-safe and runs unchanged on H2 (MODE=MySQL) and Postgres.

ALTER TABLE api_key ADD COLUMN IF NOT EXISTS parent_id VARCHAR(36) REFERENCES api_key (id) ON DELETE CASCADE;
