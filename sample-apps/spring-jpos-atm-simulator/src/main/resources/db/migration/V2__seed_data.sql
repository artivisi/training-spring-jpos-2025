-- Insert test account
-- PIN: 1234 (hashed with BCrypt)
INSERT INTO accounts (id, pan, pin_hash, balance, status)
VALUES (gen_random_uuid(), '4111111111111111', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 1000.00, 'ACTIVE');

-- Insert another test account
-- PIN: 5678
INSERT INTO accounts (id, pan, pin_hash, balance, status)
VALUES (gen_random_uuid(), '5500000000000004', '$2a$10$92IXUNpkjO0rOQ5byMi.Ye4oKoEa3Ro9llC/.og/at2.uheWG/igi', 5000.00, 'ACTIVE');

-- Insert initial crypto keys
-- These keys must match the server's keys for terminal TRM-ISS001-ATM-001
-- Keys are from server's V2__insert_sample_data.sql
INSERT INTO crypto_keys (id, key_type, key_value, check_value, status, expires_at)
VALUES
  (gen_random_uuid(), 'TMK', '1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF1234567890ABCDEF', 'ABC123', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '90 days'),
  (gen_random_uuid(), 'TPK', '246A31D729B280DD7FCDA3BB7F187ABFA1BB0811D7EF3D68FDCA63579F3748B0', '883F61016', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '90 days'),
  (gen_random_uuid(), 'TSK', '3AC638783EF600FE5E25E8A2EE5B0D222EB810DDF64C3681DD11AFEFAF41614B', 'C8E5D7A2B', 'ACTIVE', CURRENT_TIMESTAMP + INTERVAL '90 days');
