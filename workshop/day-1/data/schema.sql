-- Payment System Database Schema
-- Created for Spring Boot + JPos Training Day 1

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Transactions table
CREATE TABLE transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64) UNIQUE NOT NULL,
    amount DECIMAL(15,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'IDR',
    bill_id VARCHAR(64),
    customer_id VARCHAR(64),
    status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    response_code VARCHAR(3),
    error_message TEXT,
    metadata JSONB
);

-- Bills table
CREATE TABLE bills (
    id BIGSERIAL PRIMARY KEY,
    bill_id VARCHAR(64) UNIQUE NOT NULL,
    biller_id VARCHAR(32) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    customer_name VARCHAR(100),
    amount DECIMAL(15,2) NOT NULL,
    fee DECIMAL(15,2) DEFAULT 0,
    due_date TIMESTAMP,
    status VARCHAR(20) DEFAULT 'ACTIVE',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Audit logs table
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(64),
    action VARCHAR(50) NOT NULL,
    module VARCHAR(50) NOT NULL,
    details TEXT,
    user_id VARCHAR(64),
    ip_address INET,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_transactions_transaction_id ON transactions(transaction_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_bills_bill_id ON bills(bill_id);
CREATE INDEX idx_bills_customer_id ON bills(customer_id);
CREATE INDEX idx_bills_status ON bills(status);
CREATE INDEX idx_audit_logs_transaction_id ON audit_logs(transaction_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);

-- Create trigger for updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

CREATE TRIGGER update_transactions_updated_at
    BEFORE UPDATE ON transactions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_bills_updated_at
    BEFORE UPDATE ON bills
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert initial biller data
INSERT INTO bills (bill_id, biller_id, customer_id, customer_name, amount, fee, due_date, description) VALUES
('BILL001', 'PLN', 'CUST001', 'Budi Santoso', 150000.00, 2500.00, '2025-11-15 23:59:59', 'PLN Tagihan Listrik Bulan Oktober 2025'),
('BILL002', 'PDAM', 'CUST002', 'Siti Nurhaliza', 75000.00, 1500.00, '2025-11-10 23:59:59', 'PDAM Tagihan Air Bulan Oktober 2025'),
('BILL003', 'TELKOM', 'CUST001', 'Budi Santoso', 200000.00, 3000.00, '2025-11-20 23:59:59', 'Telkomsel Tagihan Internet Bulan Oktober 2025'),
('BILL004', 'PLN', 'CUST003', 'Ahmad Fadli', 180000.00, 2500.00, '2025-11-18 23:59:59', 'PLN Tagihan Listrik Bulan Oktober 2025'),
('BILL005', 'BPJS', 'CUST002', 'Siti Nurhaliza', 120000.00, 2000.00, '2025-11-25 23:59:59', 'BPJS Kesehatan Tagihan Bulan Oktober 2025');