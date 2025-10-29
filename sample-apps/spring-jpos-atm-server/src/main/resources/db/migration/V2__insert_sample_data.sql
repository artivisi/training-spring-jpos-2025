-- Insert sample accounts for testing
-- Accounts use different PIN verification methods for testing both strategies

-- Account 1: Uses ENCRYPTED_PIN_BLOCK method (PIN: 1234)
-- Account 2: Uses PVV method (PIN: 5678, PVV: 1234)
-- Account 3: No PIN configured

INSERT INTO accounts (account_number, account_holder_name, balance, currency, account_type, status, encrypted_pin_block, pvv, pin_verification_type)
VALUES
    ('1234567890', 'John Doe', 5000000.00, 'IDR', 'SAVINGS', 'ACTIVE', 'ABCD1234567890ABCDEF1234567890AB', NULL, 'ENCRYPTED_PIN_BLOCK'),
    ('0987654321', 'Jane Smith', 3000000.00, 'IDR', 'CHECKING', 'ACTIVE', NULL, '1234', 'PVV'),
    ('5555555555', 'Bob Johnson', 10000000.00, 'IDR', 'SAVINGS', 'ACTIVE', NULL, NULL, 'ENCRYPTED_PIN_BLOCK');
