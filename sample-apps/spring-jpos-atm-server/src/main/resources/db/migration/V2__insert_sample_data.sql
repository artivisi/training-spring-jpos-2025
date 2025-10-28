-- Insert sample accounts for testing
INSERT INTO accounts (account_number, account_holder_name, balance, currency, account_type, status)
VALUES
    ('1234567890', 'John Doe', 5000000.00, 'IDR', 'SAVINGS', 'ACTIVE'),
    ('0987654321', 'Jane Smith', 3000000.00, 'IDR', 'CHECKING', 'ACTIVE'),
    ('5555555555', 'Bob Johnson', 10000000.00, 'IDR', 'SAVINGS', 'ACTIVE');
