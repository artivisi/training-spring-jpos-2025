-- Security Schema for Day 4: HSM Integration
-- Tables for PIN block storage, key management, and security auditing

-- Key Store for cryptographic keys
CREATE TABLE key_store (
    id BIGSERIAL PRIMARY KEY,
    key_type VARCHAR(20) NOT NULL,          -- ZMK, ZPK, ZAK, TEK, etc
    key_identifier VARCHAR(64) UNIQUE NOT NULL,  -- Key ID/alias
    key_value VARCHAR(512) NOT NULL,       -- Encrypted key value
    key_algorithm VARCHAR(50) NOT NULL,    -- DES, 3DES, AES, etc
    key_length INTEGER NOT NULL,           -- Key length in bits
    key_version INTEGER DEFAULT 1,         -- Key version for rotation
    checksum VARCHAR(64),                  -- Key integrity checksum
    status VARCHAR(20) DEFAULT 'ACTIVE',   -- ACTIVE, EXPIRED, REVOKED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    last_used_at TIMESTAMP,
    created_by VARCHAR(64),
    description TEXT
);

-- PIN Block Storage for secure PIN handling
CREATE TABLE pin_block_store (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) NOT NULL,
    pin_block VARCHAR(256) NOT NULL,       -- Encrypted PIN block
    pin_format INTEGER NOT NULL,           -- 0, 3, 4
    pan_hash VARCHAR(128),                 -- Hashed PAN for security
    salt VARCHAR(64),                      -- Salt for hashing
    encryption_algorithm VARCHAR(50),      -- DES, 3DES, etc
    status VARCHAR(20) DEFAULT 'ACTIVE',   -- ACTIVE, USED, EXPIRED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,                  -- Auto-expire PIN blocks
    used_at TIMESTAMP,                     -- When PIN was verified
    verification_result VARCHAR(20),       -- SUCCESS, FAILED
    FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);

-- Security Audit Log for compliance and debugging
CREATE TABLE security_audit_log (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64),
    operation_type VARCHAR(50) NOT NULL,   -- PIN_ENCRYPT, MAC_GENERATE, KEY_EXCHANGE, etc
    operation_status VARCHAR(20) NOT NULL, -- SUCCESS, FAILED, ERROR
    module VARCHAR(50) NOT NULL,           -- ACQUIRER, GATEWAY, BILLING, HSM
    user_id VARCHAR(64),
    session_id VARCHAR(128),
    client_ip INET,
    request_data TEXT,                      -- Request parameters (sanitized)
    response_data TEXT,                     -- Response data (sanitized)
    error_message TEXT,
    processing_time_ms INTEGER,             -- Operation processing time
    security_level VARCHAR(20),            -- HIGH, MEDIUM, LOW
    compliance_tag VARCHAR(50),             -- PCI_DSS, GDPR, etc
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);

-- Key Exchange History for tracking key distribution
CREATE TABLE key_exchange_history (
    id BIGSERIAL PRIMARY KEY,
    exchange_id VARCHAR(64) UNIQUE NOT NULL,
    key_type VARCHAR(20) NOT NULL,
    source_system VARCHAR(50) NOT NULL,    -- GATEWAY, HSM
    target_system VARCHAR(50) NOT NULL,    -- ACQUIRER, BILLING
    key_identifier VARCHAR(64) NOT NULL,
    wrapped_key VARCHAR(512),              -- Encrypted key for transport
    kek_identifier VARCHAR(64),            -- Key Encryption Key ID
    exchange_status VARCHAR(20) DEFAULT 'PENDING',  -- PENDING, SUCCESS, FAILED
    protocol_version VARCHAR(20),          -- Key exchange protocol
    verification_code VARCHAR(128),        -- Exchange verification
    exchange_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    expires_at TIMESTAMP,
    FOREIGN KEY (key_identifier) REFERENCES key_store(key_identifier)
);

-- MAC Validation Log for message integrity tracking
CREATE TABLE mac_validation_log (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64),
    message_hash VARCHAR(128) NOT NULL,    -- Hash of original message
    provided_mac VARCHAR(64) NOT NULL,     -- MAC received with message
    calculated_mac VARCHAR(64) NOT NULL,   -- MAC calculated by system
    mac_key_identifier VARCHAR(64) NOT NULL,
    validation_result VARCHAR(20) NOT NULL, -- VALID, INVALID, ERROR
    algorithm VARCHAR(50) NOT NULL,        -- ANSI_X9_19, etc
    message_length INTEGER,                -- Length of message
    processing_time_ms INTEGER,
    module VARCHAR(50),                    -- Where validation occurred
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id) REFERENCES transactions(transaction_id)
);

-- Session Key Management for active security contexts
CREATE TABLE session_keys (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(128) UNIQUE NOT NULL,
    transaction_id VARCHAR(64),
    key_type VARCHAR(20) NOT NULL,
    key_identifier VARCHAR(64) NOT NULL,
    participant_a VARCHAR(50) NOT NULL,    -- Initiating system
    participant_b VARCHAR(50) NOT NULL,    -- Receiving system
    session_status VARCHAR(20) DEFAULT 'ACTIVE', -- ACTIVE, EXPIRED, TERMINATED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    last_activity_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    usage_count INTEGER DEFAULT 0,
    max_usage_count INTEGER DEFAULT 1000,
    FOREIGN KEY (key_identifier) REFERENCES key_store(key_identifier)
);

-- Create indexes for performance
CREATE INDEX idx_key_store_type ON key_store(key_type);
CREATE INDEX idx_key_store_identifier ON key_store(key_identifier);
CREATE INDEX idx_key_store_status ON key_store(status);
CREATE INDEX idx_key_store_expires ON key_store(expires_at);

CREATE INDEX idx_pin_block_transaction ON pin_block_store(transaction_id);
CREATE INDEX idx_pin_block_status ON pin_block_store(status);
CREATE INDEX idx_pin_block_expires ON pin_block_store(expires_at);
CREATE INDEX idx_pin_block_pan_hash ON pin_block_store(pan_hash);

CREATE INDEX idx_security_audit_transaction ON security_audit_log(transaction_id);
CREATE INDEX idx_security_audit_operation ON security_audit_log(operation_type);
CREATE INDEX idx_security_audit_status ON security_audit_log(operation_status);
CREATE INDEX idx_security_audit_timestamp ON security_audit_log(created_at);
CREATE INDEX idx_security_audit_module ON security_audit_log(module);

CREATE INDEX idx_key_exchange_source ON key_exchange_history(source_system);
CREATE INDEX idx_key_exchange_target ON key_exchange_history(target_system);
CREATE INDEX idx_key_exchange_status ON key_exchange_history(exchange_status);
CREATE INDEX idx_key_exchange_timestamp ON key_exchange_history(exchange_timestamp);

CREATE INDEX idx_mac_validation_transaction ON mac_validation_log(transaction_id);
CREATE INDEX idx_mac_validation_result ON mac_validation_log(validation_result);
CREATE INDEX idx_mac_validation_timestamp ON mac_validation_log(created_at);

CREATE INDEX idx_session_keys_session_id ON session_keys(session_id);
CREATE INDEX idx_session_keys_transaction ON session_keys(transaction_id);
CREATE INDEX idx_session_keys_participants ON session_keys(participant_a, participant_b);
CREATE INDEX idx_session_keys_status ON session_keys(session_status);
CREATE INDEX idx_session_keys_expires ON session_keys(expires_at);

-- Create trigger for auto-updating last_activity_at
CREATE OR REPLACE FUNCTION update_session_activity()
RETURNS TRIGGER AS $$
BEGIN
    NEW.last_activity_at = CURRENT_TIMESTAMP;
    NEW.usage_count = OLD.usage_count + 1;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_session_activity_trigger
    BEFORE UPDATE ON session_keys
    FOR EACH ROW EXECUTE FUNCTION update_session_activity();

-- Insert initial keys for testing
INSERT INTO key_store (key_type, key_identifier, key_value, key_algorithm, key_length, description) VALUES
('ZMK', 'ZMK_001', 'ENCRYPTED_ZMK_VALUE_PLACEHOLDER', 'DESede', 192, 'Zone Master Key for key exchange'),
('ZPK', 'ZPK_001', 'ENCRYPTED_ZPK_VALUE_PLACEHOLDER', 'DESede', 192, 'Zone PIN Key for PIN encryption'),
('ZAK', 'ZAK_001', 'ENCRYPTED_ZAK_VALUE_PLACEHOLDER', 'DESede', 192, 'Zone Authentication Key for MAC');

-- Create view for active sessions
CREATE VIEW active_sessions AS
SELECT
    s.session_id,
    s.transaction_id,
    s.key_type,
    s.participant_a,
    s.participant_b,
    s.created_at,
    s.expires_at,
    s.last_activity_at,
    s.usage_count,
    s.max_usage_count,
    k.key_algorithm,
    CASE
        WHEN s.expires_at < CURRENT_TIMESTAMP THEN 'EXPIRED'
        WHEN s.usage_count >= s.max_usage_count THEN 'EXPIRED_BY_USAGE'
        ELSE s.session_status
    END as effective_status
FROM session_keys s
JOIN key_store k ON s.key_identifier = k.key_identifier
WHERE s.session_status = 'ACTIVE';

-- Create view for security statistics
CREATE VIEW security_statistics AS
SELECT
    DATE_TRUNC('day', created_at) as log_date,
    operation_type,
    operation_status,
    COUNT(*) as operation_count,
    AVG(processing_time_ms) as avg_processing_time,
    MAX(processing_time_ms) as max_processing_time
FROM security_audit_log
WHERE created_at >= CURRENT_DATE - INTERVAL '30 days'
GROUP BY DATE_TRUNC('day', created_at), operation_type, operation_status
ORDER BY log_date DESC, operation_type;