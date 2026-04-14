-- V2__seed_inventory.sql — Demo inventory data (matching product_catalog IDs)

INSERT INTO inventory (product_id, quantity) VALUES
    ('a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d', 50),  -- iPhone 15 Pro Max
    ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d6e', 30),  -- Samsung Galaxy S24 Ultra
    ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e7f', 20),  -- MacBook Pro 14"
    ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f8a', 100), -- AirPods Max
    ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a9b', 40);  -- iPad Pro 12.9"
