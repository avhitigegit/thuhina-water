-- Seed customers for LOCAL and UAT only (never production) – the prototype's 40 customers and agreed prices
-- (prototype/assets/js/data.js). Opening bottles and opening balances are posted by the sales seed (M06).
-- Next delivery = the first delivery day from today, plus the prototype's offset in weeks (so nobody starts overdue).
-- Flyway re-runs this file when it changes; existing customers are left as they are.

INSERT INTO customer (code, name, address, area_id, phone, customer_type_id, delivery_day, cycle_weeks, next_delivery_date,
                      pay_type, credit_limit, terms_days, registered_on, status, status_date, status_reason, created_by)
SELECT v.code, v.name, v.address, a.id, v.phone, t.id, v.day, v.cycle,
       current_date + ((v.day - extract(isodow FROM current_date)::int + 7) % 7) + 7 * v.offset_weeks,
       v.pay, v.credit_limit, v.terms, v.since, v.status,
       CASE WHEN v.status = 'INACTIVE' THEN DATE '2026-06-30' END,
       CASE WHEN v.status = 'INACTIVE' THEN 'Moved away – Moved to Kandy, account closed by customer' END,
       'seed'
FROM (VALUES
    ('C0001', 'W.M. Sunil Perera', '23/4, Pagoda Road', 'Nugegoda', '077 412 3381', 'Household', 1, 1, 0, 'CASH', 0, 0, DATE '2024-03-11', 'ACTIVE'),
    ('C0002', 'Kumari Jayawardena', '15, Stanley Thilakaratne Mawatha', 'Nugegoda', '071 558 9024', 'Household', 1, 2, 0, 'CASH', 0, 0, DATE '2024-05-02', 'ACTIVE'),
    ('C0003', 'Sampath Grocery', '112, High Level Road', 'Maharagama', '011 285 6672', 'Shop', 1, 1, 0, 'CREDIT', 25000, 30, DATE '2023-11-20', 'ACTIVE'),
    ('C0004', 'Vertex Software (Pvt) Ltd', 'Level 3, 210 Kotte Road', 'Rajagiriya', '011 452 9031', 'Office', 1, 1, 0, 'MONTHLY_BILL', 60000, 30, DATE '2023-08-01', 'ACTIVE'),
    ('C0005', 'Mohamed Rizwan', '7/2, Dharmarama Road', 'Dehiwala', '076 330 1128', 'Household', 2, 1, 0, 'CASH', 0, 0, DATE '2024-01-15', 'ACTIVE'),
    ('C0006', 'S. Thevarajah', '44, Hill Street', 'Dehiwala', '077 908 4415', 'Household', 2, 2, 1, 'CASH', 0, 0, DATE '2024-06-19', 'ACTIVE'),
    ('C0007', 'Rathna Pharmacy', '56, Old Kesbewa Road', 'Boralesgamuwa', '011 251 7740', 'Shop', 2, 1, 0, 'CREDIT', 20000, 30, DATE '2024-02-08', 'ACTIVE'),
    ('C0008', 'Ceyknit Garments (Pvt) Ltd', 'Lot 12, BOI Zone, Horana Road', 'Piliyandala', '038 224 1180', 'Factory', 2, 1, 0, 'MONTHLY_BILL', 120000, 30, DATE '2023-06-12', 'ACTIVE'),
    ('C0009', 'Nadeesha Gunawardena', '18/1, Temple Road', 'Mount Lavinia', '070 215 6634', 'Household', 3, 1, 0, 'CASH', 0, 0, DATE '2024-08-27', 'ACTIVE'),
    ('C0010', 'Lakwin Insurance Brokers', '2nd Floor, 77 Galle Road', 'Mount Lavinia', '011 273 9902', 'Office', 3, 1, 0, 'MONTHLY_BILL', 40000, 30, DATE '2024-04-03', 'ACTIVE'),
    ('C0011', 'Chaminda Wijesinghe', '9, Siripala Road', 'Mount Lavinia', '077 640 2297', 'Household', 3, 2, 0, 'CASH', 0, 0, DATE '2025-01-10', 'ACTIVE'),
    ('C0012', 'Hiru Salon & Spa', '201, Main Street', 'Maharagama', '075 882 1043', 'Shop', 3, 1, 0, 'CREDIT', 12000, 14, DATE '2025-03-14', 'ACTIVE'),
    ('C0013', 'Ananda Kumarasiri', '31/5, Lake Drive', 'Rajagiriya', '071 903 4472', 'Household', 4, 1, 0, 'CASH', 0, 0, DATE '2023-12-05', 'ACTIVE'),
    ('C0014', 'Green Leaf Montessori', '5, Bandaranayake Mawatha', 'Battaramulla', '011 287 3365', 'Office', 4, 1, 0, 'CREDIT', 30000, 30, DATE '2024-09-09', 'ACTIVE'),
    ('C0015', 'Fathima Nusrath', '12A, Station Road', 'Battaramulla', '076 554 0981', 'Household', 4, 2, 1, 'CASH', 0, 0, DATE '2025-02-21', 'ACTIVE'),
    ('C0016', 'Kotte Hardware Stores', '88, Kotte Road', 'Kotte', '011 286 2219', 'Shop', 4, 1, 0, 'CASH', 0, 0, DATE '2024-07-01', 'ACTIVE'),
    ('C0017', 'R.P. Dissanayake', '62/3, Pannipitiya Road', 'Kottawa', '077 216 8850', 'Household', 5, 1, 0, 'CASH', 0, 0, DATE '2024-02-28', 'ACTIVE'),
    ('C0018', 'Kottawa Rubber Products', 'No. 4, Industrial Estate, Makumbura', 'Kottawa', '011 278 5502', 'Factory', 5, 1, 0, 'MONTHLY_BILL', 100000, 30, DATE '2023-10-16', 'ACTIVE'),
    ('C0019', 'Sanduni Herath', '40, Hospital Road', 'Kottawa', '071 662 9013', 'Household', 5, 2, 0, 'CASH', 0, 0, DATE '2024-10-12', 'ACTIVE'),
    ('C0020', 'Dilshan Fernando', '3/7, Old Road', 'Maharagama', '078 330 2265', 'Household', 5, 1, 0, 'CASH', 0, 0, DATE '2025-04-04', 'ACTIVE'),
    ('C0021', 'New Lanka Stores', '144, Highlevel Road', 'Maharagama', '011 284 7716', 'Shop', 5, 1, 0, 'CREDIT', 25000, 30, DATE '2023-09-25', 'ACTIVE'),
    ('C0022', 'Prof. K. Senanayake', '19, Jambugasmulla Mawatha', 'Nugegoda', '077 129 5508', 'Household', 5, 2, 1, 'CREDIT', 10000, 30, DATE '2024-03-30', 'ACTIVE'),
    ('C0023', 'Pradeep Bakers', '27, Kandawatta Road', 'Nugegoda', '072 447 1906', 'Shop', 6, 1, 0, 'CASH', 0, 0, DATE '2024-05-17', 'ACTIVE'),
    ('C0024', 'Thilini Abeysekara', '6/1, Edirisinghe Road', 'Mount Lavinia', '076 912 3384', 'Household', 6, 1, 0, 'CASH', 0, 0, DATE '2024-11-08', 'ACTIVE'),
    ('C0025', 'Asiri Learning Centre', '75, Piliyandala Road', 'Boralesgamuwa', '011 251 3390', 'Office', 6, 1, 0, 'MONTHLY_BILL', 35000, 30, DATE '2025-01-06', 'ACTIVE'),
    ('C0026', 'M.A. Saman Kumara', '102, Horana Road', 'Piliyandala', '070 338 7741', 'Household', 6, 2, 0, 'CASH', 0, 0, DATE '2024-12-02', 'ACTIVE'),
    ('C0027', 'Gamini Rajapaksha', '14/9, Kesbewa Road', 'Piliyandala', '077 581 6620', 'Household', 1, 1, 0, 'CASH', 0, 0, DATE '2025-05-19', 'ACTIVE'),
    ('C0028', 'Sri Bodhi Auto Parts', '230, Avissawella Road', 'Maharagama', '011 274 6631', 'Shop', 1, 2, 1, 'CREDIT', 15000, 30, DATE '2024-08-12', 'ACTIVE'),
    ('C0029', 'Malini Senaratne', '5, Gregory''s Road', 'Kotte', '071 225 0398', 'Household', 2, 1, 0, 'CASH', 0, 0, DATE '2025-06-01', 'ACTIVE'),
    ('C0030', 'Nippon Lanka Logistics', 'Warehouse 3, Kelani Valley Road', 'Maharagama', '011 280 4427', 'Factory', 3, 1, 0, 'MONTHLY_BILL', 80000, 30, DATE '2024-01-29', 'ACTIVE'),
    ('C0031', 'Ishara Liyanage', '33, Templers Road', 'Mount Lavinia', '075 667 2014', 'Household', 4, 1, 0, 'CASH', 0, 0, DATE '2025-07-07', 'ACTIVE'),
    ('C0032', 'Dr. A. Ramanathan', '4, Vihara Lane', 'Dehiwala', '077 345 9902', 'Household', 4, 2, 0, 'CASH', 0, 0, DATE '2024-04-22', 'ACTIVE'),
    ('C0033', 'Pubudu Communication', '67, Stanley Thilakaratne Mawatha', 'Nugegoda', '076 118 4423', 'Shop', 5, 2, 0, 'CASH', 0, 0, DATE '2025-02-03', 'ACTIVE'),
    ('C0034', 'Ruwanthi Karunaratne', '21, Lake Road', 'Boralesgamuwa', '071 776 3305', 'Household', 5, 4, 0, 'CASH', 0, 0, DATE '2025-08-15', 'ACTIVE'),
    ('C0035', 'Hemas Tuition Hall', '9, School Lane', 'Kottawa', '011 278 9914', 'Office', 6, 2, 0, 'CREDIT', 20000, 30, DATE '2024-06-10', 'ACTIVE'),
    ('C0036', 'Janaka Weerasooriya', '17/2, Siddamulla Road', 'Piliyandala', '077 902 6618', 'Household', 2, 1, 0, 'CASH', 0, 0, DATE '2022-11-02', 'INACTIVE'),
    ('C0037', 'Shehan Madushanka', '8, Lumbini Mawatha', 'Kottawa', '070 902 3346', 'Household', 5, 1, 0, 'CASH', 0, 0, DATE '2026-09-04', 'ACTIVE'),
    ('C0038', 'Blue Ocean Cafe', '2, Beach Road', 'Mount Lavinia', '077 330 5521', 'Shop', 3, 1, 0, 'CREDIT', 20000, 30, DATE '2026-08-19', 'ACTIVE'),
    ('C0039', 'Anoma Wickramaratne', '55/1, Kirulapone Avenue', 'Nugegoda', '071 449 0072', 'Household', 1, 1, 0, 'CASH', 0, 0, DATE '2026-09-14', 'ACTIVE'),
    ('C0040', 'Priyanka Rathnayake', '11, Pragathi Mawatha', 'Maharagama', '076 205 8837', 'Household', 1, 1, 0, 'CASH', 0, 0, DATE '2026-10-02', 'ACTIVE')
     ) AS v (code, name, address, area, phone, type, day, cycle, offset_weeks, pay, credit_limit, terms, since, status)
JOIN area a ON lower(a.name) = lower(v.area)
JOIN customer_type t ON lower(t.name) = lower(v.type)
ON CONFLICT (code) DO NOTHING;
UPDATE doc_sequence SET next_value = GREATEST(next_value, 41) WHERE name = 'CUSTOMER';

INSERT INTO customer_usual_qty (customer_id, bottle_type_code, qty)
SELECT c.id, v.bottle, v.qty
FROM (VALUES ('C0001', 'B20', 2), ('C0002', 'B20', 1), ('C0002', 'B10', 1), ('C0003', 'B20', 4), ('C0004', 'B20', 6), ('C0005', 'B20', 2), ('C0006', 'B20', 2), ('C0007', 'B20', 3), ('C0008', 'B20', 10), ('C0009', 'B20', 2), ('C0009', 'B10', 1), ('C0010', 'B20', 3), ('C0011', 'B20', 2), ('C0012', 'B20', 2), ('C0013', 'B20', 3), ('C0014', 'B20', 4), ('C0014', 'B10', 2), ('C0015', 'B20', 1), ('C0016', 'B20', 3), ('C0017', 'B20', 2), ('C0018', 'B20', 8), ('C0019', 'B20', 2), ('C0020', 'B20', 1), ('C0020', 'B10', 1), ('C0021', 'B20', 4), ('C0022', 'B20', 2), ('C0023', 'B20', 3), ('C0024', 'B20', 2), ('C0025', 'B20', 3), ('C0026', 'B20', 2), ('C0027', 'B20', 2), ('C0028', 'B20', 2), ('C0029', 'B20', 1), ('C0029', 'B10', 1), ('C0030', 'B20', 6), ('C0031', 'B20', 2), ('C0032', 'B20', 2), ('C0033', 'B20', 2), ('C0034', 'B20', 2), ('C0035', 'B20', 3), ('C0036', 'B20', 2), ('C0037', 'B20', 2), ('C0038', 'B20', 3), ('C0039', 'B20', 2), ('C0040', 'B20', 2)) AS v (code, bottle, qty)
JOIN customer c ON c.code = v.code
ON CONFLICT DO NOTHING;

INSERT INTO customer_bottle_balance (customer_id, bottle_type_code)
SELECT c.id, b.code FROM customer c CROSS JOIN bottle_type b
ON CONFLICT DO NOTHING;

INSERT INTO customer_account (customer_id, balance)
SELECT c.id, 0 FROM customer c
ON CONFLICT DO NOTHING;

-- Agreed prices (FR-05): C0004, C0008, C0018, C0021 in force; C0030 until 31/12/2026; C0014 10L; C0012 ended 31/08/2026;
-- C0010 scheduled from 01/11/2026. standard_at_set = the standard price on the start date.
INSERT INTO customer_price (cp_no, customer_id, bottle_type_code, price, standard_at_set, valid_from, valid_until, reason, created_by)
SELECT v.cp_no, c.id, v.bottle, v.price,
       (SELECT p.price FROM price_entry p WHERE p.kind = 'WATER' AND p.bottle_type_code = v.bottle
          AND p.customer_type_id = c.customer_type_id AND p.effective_from <= v.valid_from
        ORDER BY p.effective_from DESC LIMIT 1),
       v.valid_from, v.valid_until, v.reason, 'nimal.admin'
FROM (VALUES
    ('CP0001', 'C0004', 'B20', 300, DATE '2026-07-01', NULL, 'About 26 bottles a month – customer since 2023'),
    ('CP0002', 'C0008', 'B20', 270, DATE '2026-07-01', NULL, 'Volume price – about 43 bottles a month'),
    ('CP0003', 'C0018', 'B20', 280, DATE '2026-09-01', NULL, 'Volume price – about 35 bottles a month'),
    ('CP0004', 'C0021', 'B20', 320, DATE '2026-07-01', NULL, 'Long-standing shop (since 2023)'),
    ('CP0005', 'C0030', 'B20', 290, DATE '2026-07-01', DATE '2026-12-31', 'Six-month agreement – review in December'),
    ('CP0006', 'C0014', 'B10', 165, DATE '2026-07-01', NULL, 'School – 2 × 10L every week'),
    ('CP0007', 'C0012', 'B20', 320, DATE '2026-07-01', DATE '2026-08-31', 'Opening offer for two months'),
    ('CP0008', 'C0010', 'B20', 310, DATE '2026-11-01', NULL, 'New contract from November (signed 28/09/2026)')
     ) AS v (cp_no, code, bottle, price, valid_from, valid_until, reason)
JOIN customer c ON c.code = v.code
ON CONFLICT (cp_no) DO NOTHING;
UPDATE doc_sequence SET next_value = GREATEST(next_value, 9) WHERE name = 'CUSTOMER_PRICE';
