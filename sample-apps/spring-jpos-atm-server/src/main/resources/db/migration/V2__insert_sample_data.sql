-- Insert sample accounts for testing
-- encrypted_pin_block contains PIN encrypted under HSM LMK
-- For testing: account 1234567890 has PIN 1234, account 0987654321 has PIN 5678
INSERT INTO accounts (account_number, account_holder_name, balance, currency, account_type, status, encrypted_pin_block)
VALUES
    ('1234567890', 'John Doe', 5000000.00, 'IDR', 'SAVINGS', 'ACTIVE', 'ABCD1234567890ABCDEF1234567890AB'),
    ('0987654321', 'Jane Smith', 3000000.00, 'IDR', 'CHECKING', 'ACTIVE', '5678ABCDEF1234567890ABCDEF123456'),
    ('5555555555', 'Bob Johnson', 10000000.00, 'IDR', 'SAVINGS', 'ACTIVE', NULL);
