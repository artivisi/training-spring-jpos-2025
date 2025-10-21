-- Sample Data for Testing
-- Additional test data for Day 1 exercises

-- Insert more bills for testing
INSERT INTO bills (bill_id, biller_id, customer_id, customer_name, amount, fee, due_date, description, status) VALUES
('BILL006', 'INDIHOME', 'CUST004', 'Rizki Pratama', 350000.00, 5000.00, '2025-11-22 23:59:59', 'Indihome Paket Family Bulan Oktober 2025', 'ACTIVE'),
('BILL007', 'PLN', 'CUST005', 'Diana Putri', 95000.00, 2000.00, '2025-11-12 23:59:59', 'PLN Token Listrik 50kVA', 'ACTIVE'),
('BILL008', 'GOJEK', 'CUST006', 'Eko Widodo', 25000.00, 1000.00, '2025-11-08 23:59:59', 'GoPay Top-up', 'ACTIVE'),
('BILL009', 'PDAM', 'CUST007', 'Maya Sari', 62000.00, 1500.00, '2025-11-16 23:59:59', 'PDAM Tagihan Air Bulan Oktober 2025', 'ACTIVE'),
('BILL010', 'TELKOMSEL', 'CUST008', 'Fajar Hidayat', 100000.00, 2000.00, '2025-11-19 23:59:59', 'Telkomsel Tagihan Pascabayar', 'ACTIVE');

-- Sample successful transactions
INSERT INTO transactions (transaction_id, amount, currency, bill_id, customer_id, status, response_code, created_at) VALUES
('TXN20251021001', 152500.00, 'IDR', 'BILL001', 'CUST001', 'SUCCESS', '00', '2025-10-21 09:15:30'),
('TXN20251021002', 76500.00, 'IDR', 'BILL002', 'CUST002', 'SUCCESS', '00', '2025-10-21 10:22:45'),
('TXN20251021003', 203000.00, 'IDR', 'BILL003', 'CUST001', 'SUCCESS', '00', '2025-10-21 11:30:12'),
('TXN20251021004', 182500.00, 'IDR', 'BILL004', 'CUST003', 'SUCCESS', '00', '2025-10-21 13:45:20'),
('TXN20251021005', 122000.00, 'IDR', 'BILL005', 'CUST002', 'SUCCESS', '00', '2025-10-21 14:20:55');

-- Sample failed transactions
INSERT INTO transactions (transaction_id, amount, currency, bill_id, customer_id, status, response_code, error_message, created_at) VALUES
('TXN20251021006', 150000.00, 'IDR', 'BILL001', 'CUST001', 'FAILED', '05', 'Insufficient funds', '2025-10-21 15:10:33'),
('TXN20251021007', 75000.00, 'IDR', 'BILL002', 'CUST002', 'FAILED', '14', 'Invalid bill number', '2025-10-21 16:25:41'),
('TXN20251021008', 200000.00, 'IDR', 'BILL003', 'CUST001', 'FAILED', '91', 'Issuer or switch is inoperative', '2025-10-21 17:30:18');

-- Sample pending transactions
INSERT INTO transactions (transaction_id, amount, currency, bill_id, customer_id, status, created_at) VALUES
('TXN20251021009', 355000.00, 'IDR', 'BILL006', 'CUST004', 'PENDING', '2025-10-21 18:45:22'),
('TXN20251021010', 97000.00, 'IDR', 'BILL007', 'CUST005', 'PENDING', '2025-10-21 19:15:09');

-- Audit log samples
INSERT INTO audit_logs (transaction_id, action, module, details, user_id, created_at) VALUES
('TXN20251021001', 'CREATE_TRANSACTION', 'ACQUIRER', 'Payment request received for bill BILL001', 'user001', '2025-10-21 09:15:30'),
('TXN20251021001', 'PROCESS_PAYMENT', 'BILLING', 'Bill payment processed successfully', 'system', '2025-10-21 09:15:45'),
('TXN20251021001', 'COMPLETE_TRANSACTION', 'ACQUIRER', 'Transaction completed successfully', 'user001', '2025-10-21 09:15:50'),
('TXN20251021006', 'CREATE_TRANSACTION', 'ACQUIRER', 'Payment request received for bill BILL001', 'user002', '2025-10-21 15:10:33'),
('TXN20251021006', 'PAYMENT_FAILED', 'BILLING', 'Payment failed: Insufficient funds', 'system', '2025-10-21 15:10:40'),
('TXN20251021006', 'TRANSACTION_FAILED', 'ACQUIRER', 'Transaction marked as failed', 'user002', '2025-10-21 15:10:45');

-- Update bill statuses based on successful transactions
UPDATE bills SET status = 'PAID', updated_at = '2025-10-21 09:15:50' WHERE bill_id = 'BILL001';
UPDATE bills SET status = 'PAID', updated_at = '2025-10-21 10:22:55' WHERE bill_id = 'BILL002';
UPDATE bills SET status = 'PAID', updated_at = '2025-10-21 11:30:25' WHERE bill_id = 'BILL003';
UPDATE bills SET status = 'PAID', updated_at = '2025-10-21 13:45:35' WHERE bill_id = 'BILL004';
UPDATE bills SET status = 'PAID', updated_at = '2025-10-21 14:21:05' WHERE bill_id = 'BILL005';