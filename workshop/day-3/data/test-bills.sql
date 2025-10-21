-- Test Bills for Day 3 End-to-End Payment Flow
-- Additional test data for various payment scenarios

-- Active bills for successful payment tests
INSERT INTO bills (bill_id, biller_id, customer_id, customer_name, amount, fee, due_date, description, status) VALUES
('TEST001', 'PLN', 'TEST001', 'Test User 1', 125000.00, 2000.00, '2025-12-01 23:59:59', 'PLN Tagihan Listrik Test', 'ACTIVE'),
('TEST002', 'PDAM', 'TEST002', 'Test User 2', 85000.00, 1500.00, '2025-11-30 23:59:59', 'PDAM Tagihan Air Test', 'ACTIVE'),
('TEST003', 'TELKOM', 'TEST003', 'Test User 3', 299000.00, 4000.00, '2025-12-05 23:59:59', 'Telkomsel Internet Test', 'ACTIVE'),
('TEST004', 'BPJS', 'TEST004', 'Test User 4', 155000.00, 2500.00, '2025-12-10 23:59:59', 'BPJS Kesehatan Test', 'ACTIVE'),
('TEST005', 'INDIHOME', 'TEST005', 'Test User 5', 425000.00, 6000.00, '2025-12-15 23:59:59', 'Indihome Paket Test', 'ACTIVE');

-- Bills for error scenarios (already paid/invalid)
INSERT INTO bills (bill_id, biller_id, customer_id, customer_name, amount, fee, due_date, description, status) VALUES
('ERROR001', 'PLN', 'TEST001', 'Test User 1', 100000.00, 2000.00, '2025-10-01 23:59:59', 'PLN Already Paid', 'PAID'),
('ERROR002', 'PDAM', 'TEST002', 'Test User 2', 75000.00, 1500.00, '2025-09-15 23:59:59', 'PDAM Expired Bill', 'EXPIRED'),
('ERROR003', 'TELKOM', 'INVALID', 'Invalid Customer', 200000.00, 3000.00, '2025-12-01 23:59:59', 'Invalid Customer', 'ACTIVE');

-- Bills for edge case testing
INSERT INTO bills (bill_id, biller_id, customer_id, customer_name, amount, fee, due_date, description, status) VALUES
('EDGE001', 'PLN', 'EDGE001', 'Edge Test 1', 0.01, 0.00, '2025-12-01 23:59:59', 'Minimum Amount Test', 'ACTIVE'),
('EDGE002', 'PDAM', 'EDGE002', 'Edge Test 2', 999999.99, 10000.00, '2025-12-01 23:59:59', 'Maximum Amount Test', 'ACTIVE'),
('EDGE003', 'TELKOM', 'EDGE003', 'Edge Test 3', 100000.00, 0.00, '2025-12-01 23:59:59', 'Zero Fee Test', 'ACTIVE');

-- Create additional audit log entries for testing
INSERT INTO audit_logs (transaction_id, action, module, details, user_id, created_at) VALUES
('TXN20251021001', 'CREATE_TRANSACTION', 'ACQUIRER', 'Test transaction for END-TO-END flow', 'system', CURRENT_TIMESTAMP),
('TXN20251021001', 'PROCESS_PAYMENT', 'BILLING', 'Processing bill TEST001', 'system', CURRENT_TIMESTAMP),
('TXN20251021001', 'COMPLETE_TRANSACTION', 'ACQUIRER', 'Transaction completed successfully', 'system', CURRENT_TIMESTAMP);

-- Update bill counts for verification
-- Active bills: 8 (5 test + 3 edge cases)
-- Paid bills: 1 (for error test)
-- Expired bills: 1 (for error test)

-- Verify test data
SELECT
    status,
    COUNT(*) as count,
    SUM(amount) as total_amount
FROM bills
WHERE bill_id LIKE 'TEST%' OR bill_id LIKE 'ERROR%' OR bill_id LIKE 'EDGE%'
GROUP BY status
ORDER BY status;