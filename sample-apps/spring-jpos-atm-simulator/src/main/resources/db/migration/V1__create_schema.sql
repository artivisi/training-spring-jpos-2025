-- Create accounts table
CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pan VARCHAR(19) NOT NULL UNIQUE,
    pin_hash VARCHAR(255) NOT NULL,
    balance DECIMAL(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_account_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);

CREATE INDEX idx_accounts_pan ON accounts(pan);
CREATE INDEX idx_accounts_status ON accounts(status);

COMMENT ON TABLE accounts IS 'Stores card and account information';
COMMENT ON COLUMN accounts.id IS 'Unique account identifier';
COMMENT ON COLUMN accounts.pan IS 'Primary Account Number (card number)';
COMMENT ON COLUMN accounts.pin_hash IS 'Hashed PIN for verification';
COMMENT ON COLUMN accounts.balance IS 'Current account balance';
COMMENT ON COLUMN accounts.status IS 'Account status (ACTIVE/BLOCKED)';

-- Create transactions table
CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_accounts UUID NOT NULL,
    type VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2),
    status VARCHAR(20) NOT NULL,
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_msg TEXT,
    response_msg TEXT,
    response_code VARCHAR(10),
    terminal_id VARCHAR(50),
    CONSTRAINT fk_transactions_accounts FOREIGN KEY (id_accounts)
        REFERENCES accounts(id) ON DELETE CASCADE,
    CONSTRAINT check_transaction_type CHECK (type IN ('BALANCE', 'WITHDRAWAL')),
    CONSTRAINT check_transaction_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX idx_transactions_id_accounts ON transactions(id_accounts);
CREATE INDEX idx_transactions_timestamp ON transactions(timestamp);
CREATE INDEX idx_transactions_type ON transactions(type);

COMMENT ON TABLE transactions IS 'Logs all ATM transactions';
COMMENT ON COLUMN transactions.id IS 'Unique transaction identifier';
COMMENT ON COLUMN transactions.id_accounts IS 'Reference to accounts table';
COMMENT ON COLUMN transactions.type IS 'Transaction type (BALANCE/WITHDRAWAL)';
COMMENT ON COLUMN transactions.amount IS 'Transaction amount (null for balance inquiry)';
COMMENT ON COLUMN transactions.status IS 'Transaction status (SUCCESS/FAILED)';
COMMENT ON COLUMN transactions.request_msg IS 'ISO 8583 request message';
COMMENT ON COLUMN transactions.response_msg IS 'ISO 8583 response message';

-- Create crypto_keys table
CREATE TABLE crypto_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type VARCHAR(10) NOT NULL,
    key_value VARCHAR(500) NOT NULL,
    check_value VARCHAR(50),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    CONSTRAINT check_key_type CHECK (key_type IN ('TMK', 'TPK', 'TSK')),
    CONSTRAINT check_key_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'REVOKED'))
);

CREATE INDEX idx_crypto_keys_type_status ON crypto_keys(key_type, status);
CREATE INDEX idx_crypto_keys_expires_at ON crypto_keys(expires_at);

COMMENT ON TABLE crypto_keys IS 'Manages cryptographic keys for secure transactions';
COMMENT ON COLUMN crypto_keys.id IS 'Unique key identifier';
COMMENT ON COLUMN crypto_keys.key_type IS 'Key type (TMK/TPK/TSK)';
COMMENT ON COLUMN crypto_keys.key_value IS 'Encrypted key value';
COMMENT ON COLUMN crypto_keys.check_value IS 'Key check value for verification';
COMMENT ON COLUMN crypto_keys.status IS 'Key status (ACTIVE/EXPIRED/REVOKED)';
