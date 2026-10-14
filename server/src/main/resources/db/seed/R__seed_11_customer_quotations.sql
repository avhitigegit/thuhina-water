-- Seed customer quotations for LOCAL and UAT only (never production) – the prototype's five samples (prototype/assets/js/
-- store.js "Customer quotations"): a government office (accepted, not yet registered), a factory (sent by hand), a hotel
-- (sent by email, now expired), a hospital tender (not accepted) and an existing customer (Vertex Software, draft).
-- Quoted prices are the prototype's; the "current price" of each line is the price on the quotation date from the seeded
-- price list (the customer's agreed price for an existing customer). Runs once: skipped when any quotation exists.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM customer_quotation) THEN
        INSERT INTO customer_quotation (cq_no, customer_id, org_name, contact, designation, address, area_id, phone, email,
                                        customer_type_id, quote_date, valid_until, subject, delivery_text, terms_days, conditions,
                                        status, sent_via, sent_on, accepted_on, reject_reason, created_at, created_by)
        SELECT v.cq_no, c.id,
               coalesce(c.name, v.org), v.contact, v.designation,
               coalesce(c.address || ', ' || ca.name, v.address), coalesce(c.area_id, a.id), coalesce(c.phone, v.phone), v.email,
               t.id, v.quote_date, v.valid_until, v.subject, v.delivery, v.terms,
               'Prices include delivery within our delivery areas.' || chr(10)
                   || 'A bottle deposit applies to each new bottle given, unless an accepted old bottle is handed in.' || chr(10)
                   || 'Water is filled and sealed by an SLS-certified filling factory.' || chr(10)
                   || 'Prices are valid until the date shown above.',
               v.status, v.via, v.sent_on, v.accepted_on, v.reject_reason,
               (v.quote_date + TIME '10:00') AT TIME ZONE 'Asia/Colombo', 'nimal.admin'
        FROM (VALUES
            ('QT-2026-001', NULL, 'Divisional Secretariat – Maharagama', 'Mrs. W.A. Dilrukshi', 'Administrative Officer',
             'Divisional Secretariat, High Level Road, Maharagama', 'Maharagama', '011 285 0153', 'dsmaharagama@gov.lk', 'Office',
             DATE '2026-09-08', DATE '2026-10-08', 'Supply of drinking water – 20L bottles, monthly', 'Every Monday, about 10 bottles a week', 30,
             'ACCEPTED', 'EMAIL', DATE '2026-09-09', DATE '2026-09-24', NULL),
            ('QT-2026-002', NULL, 'Lanka Tiles (Pvt) Ltd – Piliyandala factory', 'Mr. Ranjith Fonseka', 'HR & Admin Manager',
             'No. 215, Horana Road, Piliyandala', 'Piliyandala', '011 261 4420', 'admin.piliyandala@lankatiles.lk', 'Factory',
             DATE '2026-09-18', DATE '2026-10-18', 'Drinking water for factory staff', 'Twice a week (Tuesday and Friday) – about 30 bottles a week', 30,
             'SENT', 'BY_HAND', DATE '2026-09-18', NULL, NULL),
            ('QT-2026-003', NULL, 'Royal Seaside Hotel', 'Mr. Imran Cassim', 'Purchasing Officer',
             '41, De Saram Road, Mount Lavinia', 'Mount Lavinia', '011 273 6650', 'purchasing@royalseaside.lk', 'Shop',
             DATE '2026-08-20', DATE '2026-09-19', 'Drinking water for staff areas', 'Every Wednesday', 14,
             'SENT', 'EMAIL', DATE '2026-08-21', NULL, NULL),
            ('QT-2026-004', NULL, 'Base Hospital – Homagama', 'Dr. N. Wijeratne', 'Medical Superintendent',
             'Base Hospital, Homagama', 'Kottawa', '011 285 5321', NULL, 'Office',
             DATE '2026-09-02', DATE '2026-09-30', 'Quotation for drinking water supply – tender ref. BHH/PRO/2026/14', 'Daily, Monday to Saturday', 45,
             'REJECTED', 'POST', DATE '2026-09-03', NULL, 'Tender given to the lowest bidder (Rs. 270 per bottle)'),
            ('QT-2026-005', 'C0004', NULL, 'Ms. Hiruni Peris', 'Office Manager',
             NULL, NULL, NULL, 'admin@vertexsoftware.lk', 'Office',
             DATE '2026-10-01', DATE '2026-10-31', 'Dispensers for the new 4th floor', 'With the Monday delivery', 30,
             'DRAFT', NULL, NULL, NULL, NULL)
        ) AS v (cq_no, customer_code, org, contact, designation, address, area, phone, email, type_name, quote_date, valid_until,
                subject, delivery, terms, status, via, sent_on, accepted_on, reject_reason)
        JOIN customer_type t ON t.name = v.type_name
        LEFT JOIN customer c ON c.code = v.customer_code
        LEFT JOIN area ca ON ca.id = c.area_id
        LEFT JOIN area a ON a.name = v.area
        ORDER BY v.cq_no;

        -- Lines; unit_price NULL = the current price. current_price: product selling price, deposit, or water – the customer's
        -- agreed price on the date, else the standard price of the quotation's customer type.
        INSERT INTO cq_line (quotation_id, line_no, item_kind, bottle_type_code, product_code, description, qty, unit, unit_price,
                             current_price, current_agreed)
        SELECT q.id, v.line_no, v.kind, v.bottle, v.product, coalesce(v.description, p.name), v.qty, v.unit,
               coalesce(v.price, cur.price), cur.price, cur.agreed
        FROM (VALUES
            ('QT-2026-001', 1, 'WATER',   'B20', NULL,  '20L Bottle – drinking water (refill, delivered)', 40,  'per month', 300::numeric),
            ('QT-2026-001', 2, 'DEPOSIT', 'B20', NULL,  '20L Bottle – bottle deposit (one time)',          12,  'bottles',   NULL),
            ('QT-2026-001', 3, 'PRODUCT', NULL,  'P01', NULL,                                              2,   'units',     36500),
            ('QT-2026-002', 1, 'WATER',   'B20', NULL,  '20L Bottle – drinking water (refill, delivered)', 120, 'per month', 300),
            ('QT-2026-002', 2, 'DEPOSIT', 'B20', NULL,  '20L Bottle – bottle deposit (one time)',          30,  'bottles',   NULL),
            ('QT-2026-002', 3, 'PRODUCT', NULL,  'P03', NULL,                                              6,   'units',     NULL),
            ('QT-2026-003', 1, 'WATER',   'B20', NULL,  '20L Bottle – drinking water (refill, delivered)', 24,  'per month', 330),
            ('QT-2026-003', 2, 'DEPOSIT', 'B20', NULL,  '20L Bottle – bottle deposit (one time)',          6,   'bottles',   NULL),
            ('QT-2026-004', 1, 'WATER',   'B20', NULL,  '20L Bottle – drinking water (refill, delivered)', 200, 'per month', 290),
            ('QT-2026-004', 2, 'DEPOSIT', 'B20', NULL,  '20L Bottle – bottle deposit (one time)',          40,  'bottles',   NULL),
            ('QT-2026-005', 1, 'PRODUCT', NULL,  'P01', NULL,                                              2,   'units',     36900),
            ('QT-2026-005', 2, 'PRODUCT', NULL,  'P02', NULL,                                              2,   'units',     NULL),
            ('QT-2026-005', 3, 'WATER',   'B20', NULL,  '20L Bottle – drinking water (refill, delivered)', 16,  'per month', NULL)
        ) AS v (cq_no, line_no, kind, bottle, product, description, qty, unit, price)
        JOIN customer_quotation q ON q.cq_no = v.cq_no
        LEFT JOIN product p ON p.code = v.product
        CROSS JOIN LATERAL (
            SELECT CASE
                       WHEN v.kind = 'PRODUCT' THEN p.selling_price
                       WHEN v.kind = 'DEPOSIT' THEN (SELECT e.price FROM price_entry e WHERE e.kind = 'DEPOSIT' AND e.bottle_type_code = v.bottle
                                                     AND e.effective_from <= q.quote_date ORDER BY e.effective_from DESC LIMIT 1)
                       ELSE coalesce(
                           (SELECT cp.price FROM customer_price cp WHERE cp.customer_id = q.customer_id AND cp.bottle_type_code = v.bottle
                              AND NOT cp.cancelled AND cp.valid_from <= q.quote_date AND (cp.valid_until IS NULL OR cp.valid_until >= q.quote_date)
                            ORDER BY cp.valid_from DESC LIMIT 1),
                           (SELECT e.price FROM price_entry e WHERE e.kind = 'WATER' AND e.bottle_type_code = v.bottle
                              AND e.customer_type_id = coalesce((SELECT cu.customer_type_id FROM customer cu WHERE cu.id = q.customer_id), q.customer_type_id)
                              AND e.effective_from <= q.quote_date ORDER BY e.effective_from DESC LIMIT 1))
                   END AS price,
                   v.kind = 'WATER' AND EXISTS (SELECT 1 FROM customer_price cp WHERE cp.customer_id = q.customer_id AND cp.bottle_type_code = v.bottle
                              AND NOT cp.cancelled AND cp.valid_from <= q.quote_date AND (cp.valid_until IS NULL OR cp.valid_until >= q.quote_date)) AS agreed
        ) cur;

        -- History: the steps with the prototype's dates.
        INSERT INTO cq_history (quotation_id, event, note, ts, username)
        SELECT q.id, h.event, h.note, (h.d + h.t) AT TIME ZONE 'Asia/Colombo', 'nimal.admin'
        FROM (VALUES
            ('QT-2026-001', 'CREATED',  NULL, DATE '2026-09-08', TIME '10:00'),
            ('QT-2026-001', 'SENT',     'by email', DATE '2026-09-09', TIME '09:30'),
            ('QT-2026-001', 'ACCEPTED', NULL, DATE '2026-09-24', TIME '14:10'),
            ('QT-2026-002', 'CREATED',  NULL, DATE '2026-09-18', TIME '10:00'),
            ('QT-2026-002', 'SENT',     'by hand', DATE '2026-09-18', TIME '15:20'),
            ('QT-2026-003', 'CREATED',  NULL, DATE '2026-08-20', TIME '10:00'),
            ('QT-2026-003', 'SENT',     'by email', DATE '2026-08-21', TIME '09:15'),
            ('QT-2026-004', 'CREATED',  NULL, DATE '2026-09-02', TIME '10:00'),
            ('QT-2026-004', 'SENT',     'by post', DATE '2026-09-03', TIME '11:00'),
            ('QT-2026-004', 'REJECTED', 'Tender given to the lowest bidder (Rs. 270 per bottle)', DATE '2026-09-29', TIME '12:40'),
            ('QT-2026-005', 'CREATED',  NULL, DATE '2026-10-01', TIME '10:00')
        ) AS h (cq_no, event, note, d, t)
        JOIN customer_quotation q ON q.cq_no = h.cq_no;

        UPDATE doc_sequence SET next_value = GREATEST(CASE WHEN period_key = '2026' THEN next_value ELSE 1 END, 6), period_key = '2026'
        WHERE name = 'CUSTOMER_QUOTATION';
    END IF;
END $$;
