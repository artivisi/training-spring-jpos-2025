-- Create accounts table with PIN verification support
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_holder_name VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'IDR',
    account_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    encrypted_pin_block VARCHAR(64),
    pvv VARCHAR(4),
    pin_verification_type VARCHAR(20) DEFAULT 'ENCRYPTED_PIN_BLOCK' NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_accounts_account_number ON accounts(account_number);
CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_verification_type ON accounts(pin_verification_type);

-- Create transactions table
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    account_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    balance_before DECIMAL(19, 2) NOT NULL,
    balance_after DECIMAL(19, 2) NOT NULL,
    description VARCHAR(500),
    reference_number VARCHAR(50) UNIQUE,
    transaction_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE
);

CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transactions_transaction_date ON transactions(transaction_date);
CREATE INDEX idx_transactions_reference_number ON transactions(reference_number);
CREATE INDEX idx_transactions_type ON transactions(transaction_type);

-- Create crypto_keys table for key management and rotation
CREATE TABLE crypto_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key_type VARCHAR(10) NOT NULL,
    terminal_id VARCHAR(50) NOT NULL,
    bank_uuid VARCHAR(50) NOT NULL,
    key_value VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    key_version INTEGER NOT NULL,
    effective_from TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_until TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_crypto_keys_type CHECK (key_type IN ('TPK', 'TSK')),
    CONSTRAINT chk_crypto_keys_status CHECK (status IN ('ACTIVE', 'PENDING', 'EXPIRED')),
    CONSTRAINT uq_crypto_keys_terminal_type_version UNIQUE (terminal_id, key_type, key_version)
);

CREATE INDEX idx_crypto_keys_terminal_type ON crypto_keys(terminal_id, key_type);
CREATE INDEX idx_crypto_keys_status ON crypto_keys(status);
CREATE INDEX idx_crypto_keys_effective_dates ON crypto_keys(effective_from, effective_until);

COMMENT ON TABLE accounts IS 'Bank accounts table';
COMMENT ON TABLE transactions IS 'Transaction history for accounts';
COMMENT ON TABLE crypto_keys IS 'Cryptographic keys for terminal operations (TPK for PIN, TSK for MAC) with rotation support';
COMMENT ON COLUMN accounts.version IS 'Optimistic locking version';
COMMENT ON COLUMN accounts.encrypted_pin_block IS 'PIN block encrypted under HSM LMK using AES-CBC-PKCS5, hex-encoded (64 chars = 32 bytes: IV + ciphertext). Used for PIN verification with translation method.';
COMMENT ON COLUMN accounts.pvv IS 'PIN Verification Value - 4 digit value generated from PIN using one-way hash (ISO 9564)';
COMMENT ON COLUMN accounts.pin_verification_type IS 'Type of PIN verification method: ENCRYPTED_PIN_BLOCK (translation) or PVV';
COMMENT ON COLUMN transactions.transaction_type IS 'BALANCE_INQUIRY, WITHDRAWAL, DEPOSIT, etc';
COMMENT ON COLUMN crypto_keys.key_type IS 'TPK (Terminal PIN Key) or TSK (Terminal Security Key for MAC)';
COMMENT ON COLUMN crypto_keys.key_value IS 'Master key in hex format (64 hex chars = 32 bytes for AES-256)';
COMMENT ON COLUMN crypto_keys.status IS 'ACTIVE: currently in use, PENDING: scheduled for activation, EXPIRED: no longer valid';
COMMENT ON COLUMN crypto_keys.key_version IS 'Incremental version number for key rotation tracking';
COMMENT ON COLUMN crypto_keys.effective_from IS 'Timestamp when key becomes valid';
COMMENT ON COLUMN crypto_keys.effective_until IS 'Timestamp when key expires (NULL for current active key)';
