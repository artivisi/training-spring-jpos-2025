-- Add account_number column to accounts table
ALTER TABLE accounts ADD COLUMN account_number VARCHAR(20);

-- Update existing records with account_number (temporary, just for migration)
-- In production, you would need to populate this with real data
UPDATE accounts SET account_number = '1234567890' WHERE pan = '4111111111111111';
UPDATE accounts SET account_number = '0987654321' WHERE pan = '5500000000000004';

-- Make account_number NOT NULL and UNIQUE after populating data
ALTER TABLE accounts ALTER COLUMN account_number SET NOT NULL;
ALTER TABLE accounts ADD CONSTRAINT uk_accounts_account_number UNIQUE (account_number);

-- Add index for performance
CREATE INDEX idx_accounts_account_number ON accounts(account_number);

COMMENT ON COLUMN accounts.account_number IS 'Account number used in field 102 of ISO-8583 messages';
