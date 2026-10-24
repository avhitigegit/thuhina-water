-- Seed suppliers and filling factories for LOCAL and UAT only (never production) – from the prototype
-- (prototype/assets/js/data.js and store.js). Flyway re-runs this file when it changes; existing rows are left as they are.

INSERT INTO supplier (code, name, contact, phone, email, address, terms_days, created_by) VALUES
    ('S01', 'Lanka Polymer Containers (Pvt) Ltd', 'Mr. Asanka Silva',  '011 223 6614', 'sales@lankapolymer.lk',    'No. 18, Ekala Industrial Estate, Ja-Ela',   30, 'seed'),
    ('S02', 'Ceylon PET Industries',              'Ms. Roshini de Mel', '011 246 8830', 'orders@ceylonpet.lk',      'Lot 7, Biyagama Export Zone, Biyagama',     30, 'seed'),
    ('S03', 'Kelani Plastics',                    'Mr. Nuwan Peiris',   '011 291 0457', 'kelaniplastics@gmail.com', '45, Kandy Road, Kelaniya',                  14, 'seed'),
    ('S04', 'HomeCool Appliances (Pvt) Ltd',      'Mr. Faiz Hameed',    '011 268 3375', 'trade@homecool.lk',        '201, Sri Sangaraja Mawatha, Colombo 10',    45, 'seed'),
    ('S05', 'Prime Steel Works',                  'Mr. Lalith Gamage',  '038 223 4590', 'primesteel@sltnet.lk',     '12, Galle Road, Panadura',                  30, 'seed'),
    ('S06', 'CoolTech Distributors',              'Ms. Hasini Perera',  '011 252 6603', 'sales@cooltech.lk',        '88, Nawala Road, Nugegoda',                 30, 'seed')
ON CONFLICT DO NOTHING;
UPDATE doc_sequence SET next_value = GREATEST(next_value, 7) WHERE name = 'SUPPLIER';

INSERT INTO supplier_item (supplier_id, item_type, item_code)
SELECT s.id, v.item_type, v.item_code
FROM (VALUES ('S01', 'BOTTLE', 'B20'), ('S01', 'BOTTLE', 'B10'),
             ('S02', 'BOTTLE', 'B20'), ('S02', 'BOTTLE', 'B10'),
             ('S03', 'BOTTLE', 'B20'),
             ('S04', 'PRODUCT', 'P01'), ('S04', 'PRODUCT', 'P02'), ('S04', 'PRODUCT', 'P04'), ('S04', 'PRODUCT', 'P05'),
             ('S05', 'PRODUCT', 'P03'), ('S05', 'PRODUCT', 'P04'),
             ('S06', 'PRODUCT', 'P01'), ('S06', 'PRODUCT', 'P02'), ('S06', 'PRODUCT', 'P05')) AS v (code, item_type, item_code)
JOIN supplier s ON s.code = v.code
ON CONFLICT DO NOTHING;

INSERT INTO factory (code, name, address, contact, phone, email, licence, terms_days, created_by) VALUES
    ('F01', 'AquaSeal Bottling (Pvt) Ltd', 'No. 6, Kandy Road, Kadawatha', 'Mr. Priyantha Jayalath', '011 292 7788', 'dispatch@aquaseal.lk', 'SLS 614 / FDA-W-2291', 14, 'seed'),
    ('F02', 'Pure Lanka Fillers',          '23, Negombo Road, Ja-Ela',     'Ms. Chathurika Perera',  '011 223 9045', 'orders@purelanka.lk',  'SLS 614 / FDA-W-3105', 30, 'seed')
ON CONFLICT DO NOTHING;
UPDATE doc_sequence SET next_value = GREATEST(next_value, 3) WHERE name = 'FACTORY';

INSERT INTO factory_charge (factory_id, bottle_type_code, charge, effective_from, created_by)
SELECT f.id, v.bottle, v.charge, DATE '2026-01-01', 'seed'
FROM (VALUES ('F01', 'B20', 60), ('F01', 'B10', 35), ('F02', 'B20', 62), ('F02', 'B10', 34)) AS v (code, bottle, charge)
JOIN factory f ON f.code = v.code
WHERE NOT EXISTS (SELECT 1 FROM factory_charge c WHERE c.factory_id = f.id AND c.bottle_type_code = v.bottle);
