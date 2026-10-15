-- Device-bound keys: the base64 X.509 SubjectPublicKeyInfo of a P-256 key held
-- in a phone's secure hardware. Null for plain API keys. For a device key,
-- key_hash is the hash of this public key, never of a raw secret.
ALTER TABLE api_key ADD COLUMN IF NOT EXISTS public_key VARCHAR(255);
