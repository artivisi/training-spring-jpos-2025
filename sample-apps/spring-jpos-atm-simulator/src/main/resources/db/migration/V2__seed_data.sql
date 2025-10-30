-- Insert test account
-- PIN: 1234 (hashed with BCrypt)
INSERT INTO accounts (id, pan, pin_hash, balance, status)
VALUES (gen_random_uuid(), '4111111111111111', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 1000.00, 'ACTIVE');

-- Insert another test account
-- PIN: 5678
INSERT INTO accounts (id, pan, pin_hash, balance, status)
VALUES (gen_random_uuid(), '5500000000000004', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 5000.00, 'ACTIVE');

-- Insert initial crypto keys
INSERT INTO crypto_keys (id, key_type, key_value, check_value, status, expires_at)
VALUES
  (gen_random_uuid(), 'TMK', 'encrypted_tmk_value_here', 'ABC123', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '90 days'),
  (gen_random_uuid(), 'TPK', 'encrypted_tpk_value_here', 'DEF456', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '90 days'),
  (gen_random_uuid(), 'TSK', 'encrypted_tsk_value_here', 'GHI789', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '90 days');
