-- Insert sample products
insert into product (id, code, name) values 
    ('a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 'PLN', 'Listrik PLN'),
    ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 'PDAM', 'Air PDAM'),
    ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 'TELKOM', 'Telepon Rumah'),
    ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', 'INTERNET', 'Internet Fiber');

-- Insert sample billing data
insert into billing (id, id_product, billing_period, customer_number, amount, paid, description) values
    -- Billing untuk PLN
    ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', '2025-10-01', '123456789', 450000.00, false, 'Tagihan Listrik Oktober 2025'),
    ('f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b0c', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', '2025-09-01', '123456789', 425000.00, false, 'Tagihan Listrik September 2025'),
    ('a7b8c9d0-e1f2-4a3b-4c5d-6e7f8a9b0c1d', 'a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', '2025-11-01', '123456789', 475000.00, false, 'Tagihan Listrik November 2025'),
    
    -- Billing untuk PDAM
    ('b8c9d0e1-f2a3-4b4c-5d6e-7f8a9b0c1d2e', 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', '2025-10-01', 'PDAM001', 125000.00, false, 'Tagihan Air Oktober 2025'),
    ('c9d0e1f2-a3b4-4c5d-6e7f-8a9b0c1d2e3f', 'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', '2025-11-01', 'PDAM001', 135000.00, false, 'Tagihan Air November 2025'),
    
    -- Billing untuk Telkom
    ('d0e1f2a3-b4c5-4d6e-7f8a-9b0c1d2e3f4a', 'c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', '2025-10-01', '0215551234', 175000.00, false, 'Tagihan Telepon Oktober 2025'),
    ('e1f2a3b4-c5d6-4e7f-8a9b-0c1d2e3f4a5b', 'c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', '2025-11-01', '0215551234', 175000.00, false, 'Tagihan Telepon November 2025'),
    
    -- Billing untuk Internet
    ('f2a3b4c5-d6e7-4f8a-9b0c-1d2e3f4a5b6c', 'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', '2025-10-01', 'NET123456', 350000.00, false, 'Tagihan Internet Oktober 2025'),
    ('a3b4c5d6-e7f8-4a9b-0c1d-2e3f4a5b6c7d', 'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', '2025-11-01', 'NET123456', 350000.00, false, 'Tagihan Internet November 2025'),
    ('b4c5d6e7-f8a9-4b0c-1d2e-3f4a5b6c7d8e', 'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', '2025-09-01', 'NET123456', 350000.00, false, 'Tagihan Internet September 2025');
