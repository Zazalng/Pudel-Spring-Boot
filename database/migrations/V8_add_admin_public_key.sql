-- V8: Add public_key_pem column to admin_whitelist for Mutual RSA Authentication
-- Each admin has their own RSA keypair - they sign challenges with their private key,
-- Pudel verifies with this public key stored in the database.

ALTER TABLE admin_whitelist
ADD COLUMN IF NOT EXISTS public_key_pem TEXT;

COMMENT ON COLUMN admin_whitelist.public_key_pem IS 'RSA public key in PEM format for mutual authentication';
