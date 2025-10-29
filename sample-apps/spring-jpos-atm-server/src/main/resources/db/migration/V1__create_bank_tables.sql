-- Create accounts table with PIN verification support
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_holder_name VARCHAR(255) NOT NULL,
    balance DECIMAL(19, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL DEFAULT 'IDR',
    account_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    encrypted_pin_block VARCHAR(32),
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

COMMENT ON TABLE accounts IS 'Bank accounts table';
COMMENT ON TABLE transactions IS 'Transaction history for accounts';
COMMENT ON COLUMN accounts.version IS 'Optimistic locking version';
COMMENT ON COLUMN accounts.encrypted_pin_block IS 'PIN block encrypted under HSM LMK, hex-encoded. Used for PIN verification with translation method.';
COMMENT ON COLUMN accounts.pvv IS 'PIN Verification Value - 4 digit value generated from PIN using one-way hash (ISO 9564)';
COMMENT ON COLUMN accounts.pin_verification_type IS 'Type of PIN verification method: ENCRYPTED_PIN_BLOCK (translation) or PVV';
COMMENT ON COLUMN transactions.transaction_type IS 'BALANCE_INQUIRY, WITHDRAWAL, DEPOSIT, etc';
