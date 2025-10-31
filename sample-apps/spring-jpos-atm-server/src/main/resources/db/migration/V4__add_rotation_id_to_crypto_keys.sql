-- Add rotation_id column to crypto_keys table
-- This stores the HSM's rotation ID (UUID) for tracking key rotation lifecycle

ALTER TABLE crypto_keys ADD COLUMN rotation_id VARCHAR(100);

-- Add index for faster lookups by rotation_id
CREATE INDEX idx_crypto_keys_rotation_id ON crypto_keys(rotation_id);

-- Add comment to document the purpose
COMMENT ON COLUMN crypto_keys.rotation_id IS 'HSM rotation ID (UUID) for tracking key rotation lifecycle';
