-- Seed running expenses for LOCAL and UAT only (never production) – from the prototype (prototype/assets/js/data.js
-- "expenses"): July – October 2026. Each expense counts in the month it is for, so the July and August electricity and
-- water bills paid in the next month count for the month before. Rent and salaries were entered by the Admin, the rest by
-- the Accountant (as in the prototype). Numbers EX-0001 … come from the EXPENSE sequence.
-- Runs once: skipped when any expense exists.

DO $$
DECLARE
    r   RECORD;
    v_n BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM expense) THEN
        FOR r IN
            SELECT v.ord, v.paid_date, v.for_month, t.id AS type_id, v.description, v.paid_to, v.amount, v.method, v.reference, v.bill_no
            FROM (VALUES
                (1,  DATE '2026-08-05', DATE '2026-07-01', 'Electricity',                   'Store – July bill',                     'CEB',                 3920,  'CASH',          NULL,            'A/C 4471230981'),
                (2,  DATE '2026-08-06', DATE '2026-07-01', 'Water bill',                    'Store – July bill',                     'NWSDB',               860,   'CASH',          NULL,            '10/22/331/087'),
                (3,  DATE '2026-08-01', DATE '2026-08-01', 'Rent',                          'Store rent – August',                   'Mr. S. Gunasekara',   18000, 'BANK_TRANSFER', 'TRF-770112',    NULL),
                (4,  DATE '2026-08-31', DATE '2026-08-01', 'Salaries',                      'Delivery helper – August',              'Staff',               46000, 'BANK_TRANSFER', 'TRF-779903',    NULL),
                (5,  DATE '2026-08-09', DATE '2026-08-01', 'Vehicle fuel',                  'LH-4521 – week 1–2',                    'Lanka IOC Nugegoda',  8600,  'CASH',          NULL,            'F-55102'),
                (6,  DATE '2026-08-23', DATE '2026-08-01', 'Vehicle fuel',                  'LH-4521 – week 3–4',                    'Lanka IOC Nugegoda',  9250,  'CASH',          NULL,            'F-55988'),
                (7,  DATE '2026-08-14', DATE '2026-08-01', 'Vehicle spare parts & repairs', 'LH-4521 – brake pads and service',      'Dinesh Auto Service', 7500,  'CASH',          NULL,            'DAS-2208'),
                (8,  DATE '2026-08-12', DATE '2026-08-01', 'Telephone & internet',          'Mobile and internet – August',          'Dialog',              2650,  'BANK_TRANSFER', 'TRF-771540',    NULL),
                (9,  DATE '2026-08-20', DATE '2026-08-01', 'Stationery & printing',         'Bill books (10 × 100)',                 'Sarasavi Printers',   3500,  'CASH',          NULL,            'SP-1189'),
                (10, DATE '2026-09-04', DATE '2026-08-01', 'Electricity',                   'Store – August bill',                   'CEB',                 4180,  'CASH',          NULL,            'A/C 4471230981'),
                (11, DATE '2026-09-05', DATE '2026-08-01', 'Water bill',                    'Store – August bill',                   'NWSDB',               910,   'CASH',          NULL,            '10/22/331/087'),
                (12, DATE '2026-09-01', DATE '2026-09-01', 'Rent',                          'Store rent – September',                'Mr. S. Gunasekara',   18000, 'BANK_TRANSFER', 'TRF-780241',    NULL),
                (13, DATE '2026-09-30', DATE '2026-09-01', 'Salaries',                      'Delivery helper – September',           'Staff',               46000, 'BANK_TRANSFER', 'TRF-788310',    NULL),
                (14, DATE '2026-09-08', DATE '2026-09-01', 'Vehicle fuel',                  'LH-4521 – week 1–2',                    'Lanka IOC Nugegoda',  8900,  'CASH',          NULL,            'F-56710'),
                (15, DATE '2026-09-22', DATE '2026-09-01', 'Vehicle fuel',                  'LH-4521 – week 3–4',                    'Lanka IOC Nugegoda',  8750,  'CASH',          NULL,            'F-57322'),
                (16, DATE '2026-09-17', DATE '2026-09-01', 'Vehicle spare parts & repairs', 'LH-4521 – 2 rear tyres',                'Ruwan Tyre House',    16000, 'CHEQUE',        'Cheque 330145', 'RTH-0917'),
                (17, DATE '2026-09-12', DATE '2026-09-01', 'Telephone & internet',          'Mobile and internet – September',       'Dialog',              2650,  'BANK_TRANSFER', 'TRF-781960',    NULL),
                (18, DATE '2026-09-26', DATE '2026-09-01', 'Other',                         'Bottle cleaning brushes and detergent', 'Nugegoda Hardware',   3200,  'CASH',          NULL,            'NH-7731'),
                (19, DATE '2026-10-01', DATE '2026-10-01', 'Rent',                          'Store rent – October',                  'Mr. S. Gunasekara',   18000, 'BANK_TRANSFER', 'TRF-789044',    NULL),
                (20, DATE '2026-10-02', DATE '2026-09-01', 'Electricity',                   'Store – September bill',                'CEB',                 4260,  'CASH',          NULL,            'A/C 4471230981')
            ) AS v (ord, paid_date, for_month, type_name, description, paid_to, amount, method, reference, bill_no)
            JOIN expense_type t ON t.name = v.type_name
            ORDER BY v.ord
        LOOP
            UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'EXPENSE' RETURNING next_value - 1 INTO v_n;
            INSERT INTO expense (expense_no, paid_date, for_month, expense_type_id, description, paid_to, bill_no, amount, method,
                                 reference, created_at, created_by)
            VALUES ('EX-' || lpad(v_n::text, 4, '0'), r.paid_date, r.for_month, r.type_id, r.description, r.paid_to, r.bill_no,
                    r.amount, r.method, r.reference, (r.paid_date + TIME '10:00') AT TIME ZONE 'Asia/Colombo',
                    CASE WHEN r.description LIKE 'Store rent%' OR r.description LIKE 'Delivery helper%' THEN 'nimal.admin' ELSE 'shanika.acc' END);
        END LOOP;
    END IF;
END $$;
