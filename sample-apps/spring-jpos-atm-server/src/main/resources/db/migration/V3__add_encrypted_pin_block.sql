-- Add encrypted PIN block column to accounts table
-- PIN blocks are encrypted under HSM's LMK (Local Master Key)
ALTER TABLE accounts ADD COLUMN encrypted_pin_block VARCHAR(32);

-- Add comment to explain the field
COMMENT ON COLUMN accounts.encrypted_pin_block IS 'PIN block encrypted under HSM LMK, hex-encoded. Used for PIN verification.';
