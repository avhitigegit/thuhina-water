-- Seed master data for LOCAL and UAT only (never production) – from the prototype (prototype/assets/js/data.js).
-- Flyway re-runs this file when it changes; rows that already exist are left as they are.

INSERT INTO bottle_type (code, name, litres, active, created_by) VALUES
    ('B20', '20L Bottle', 20, TRUE,  'seed'),
    ('B10', '10L Bottle', 10, TRUE,  'seed'),
    ('B5',  '5L Bottle',  5,  FALSE, 'seed')
ON CONFLICT DO NOTHING;

INSERT INTO product (code, name, selling_price, cost_price, stock_qty, active, created_by) VALUES
    ('P01', 'Hot & Cold Water Dispenser (Floor)',  38500, 31000,  9, TRUE,  'seed'),
    ('P02', 'Table-top Dispenser (Normal & Cold)', 14900, 11500, 14, TRUE,  'seed'),
    ('P03', 'Bottle Stand – Steel',                 4500,  3100, 22, TRUE,  'seed'),
    ('P04', 'Manual Bottle Pump',                   1250,   750, 48, TRUE,  'seed'),
    ('P05', 'Rechargeable Electric Pump',           3900,  2700, 17, TRUE,  'seed'),
    ('P06', 'Dispenser Cleaning Kit',                950,   520,  0, FALSE, 'seed')
ON CONFLICT DO NOTHING;
-- The next product gets P07.
UPDATE doc_sequence SET next_value = GREATEST(next_value, 7) WHERE name = 'PRODUCT';

INSERT INTO customer_type (name, description, created_by)
SELECT v.name, v.description, 'seed'
FROM (VALUES (1, 'Household', 'Homes and apartments'),
             (2, 'Shop',      'Retail shops, pharmacies, salons'),
             (3, 'Office',    'Offices, banks, schools'),
             (4, 'Factory',   'Factories and large workplaces')) AS v (ord, name, description)
WHERE NOT EXISTS (SELECT 1 FROM customer_type t WHERE lower(t.name) = lower(v.name))
ORDER BY v.ord;

INSERT INTO area (name, created_by)
SELECT v.name, 'seed'
FROM (VALUES ('Nugegoda'), ('Maharagama'), ('Kottawa'), ('Dehiwala'), ('Mount Lavinia'), ('Rajagiriya'),
             ('Battaramulla'), ('Kotte'), ('Boralesgamuwa'), ('Piliyandala')) AS v (name)
WHERE NOT EXISTS (SELECT 1 FROM area a WHERE lower(a.name) = lower(v.name));

-- Price history: the January 2026 list, the July 2026 increase, and a 10L Factory change scheduled for 01/11/2026.
INSERT INTO price_entry (kind, bottle_type_code, customer_type_id, price, effective_from, reason, created_by)
SELECT v.kind, v.bottle, t.id, v.price, v.eff::date, v.reason, 'seed'
FROM (VALUES
        ('WATER', 'B20', 'Household', 325, '2026-01-01', 'Opening price list'),
        ('WATER', 'B20', 'Shop',      310, '2026-01-01', 'Opening price list'),
        ('WATER', 'B20', 'Office',    300, '2026-01-01', 'Opening price list'),
        ('WATER', 'B20', 'Factory',   285, '2026-01-01', 'Opening price list'),
        ('WATER', 'B10', 'Household', 185, '2026-01-01', 'Opening price list'),
        ('WATER', 'B10', 'Shop',      175, '2026-01-01', 'Opening price list'),
        ('WATER', 'B10', 'Office',    170, '2026-01-01', 'Opening price list'),
        ('WATER', 'B10', 'Factory',   160, '2026-01-01', 'Opening price list'),
        ('WATER', 'B20', 'Household', 350, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B20', 'Shop',      330, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B20', 'Office',    320, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B20', 'Factory',   300, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B10', 'Household', 200, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B10', 'Shop',      190, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B10', 'Office',    185, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B10', 'Factory',   175, '2026-07-01', 'Factory charge increase'),
        ('WATER', 'B10', 'Factory',   180, '2026-11-01', 'Scheduled revision – approved by owner')
     ) AS v (kind, bottle, type_name, price, eff, reason)
JOIN customer_type t ON lower(t.name) = lower(v.type_name)
ON CONFLICT DO NOTHING;

INSERT INTO price_entry (kind, bottle_type_code, customer_type_id, price, effective_from, reason, created_by) VALUES
    ('DEPOSIT', 'B20', NULL, 1000, '2026-01-01', 'Opening price list', 'seed'),
    ('DEPOSIT', 'B10', NULL,  600, '2026-01-01', 'Opening price list', 'seed')
ON CONFLICT DO NOTHING;

INSERT INTO old_bottle_brand (name, bottle_type_code, note, active, added_on, created_by)
SELECT 'American Water', 'B20', 'Accepted in place of the Rs. 1,000 deposit', TRUE, DATE '2026-01-01', 'seed'
WHERE NOT EXISTS (SELECT 1 FROM old_bottle_brand WHERE lower(name) = 'american water' AND bottle_type_code = 'B20');
