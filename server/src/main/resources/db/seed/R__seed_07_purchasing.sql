-- Seed purchasing history for LOCAL and UAT only (never production) – from the prototype (prototype/assets/js/store.js,
-- purchasing events from 08/06/2026 to 01/10/2026):
--   QR-2026-010  steel stands from Prime Steel Works → PO-2026-0039 approved, then cancelled
--   QR-2026-011  120 × 20L bottles, three quotations → PO-2026-0040 received (GRN-0001, 2 damaged), paid in full (SP-0001)
--   QR-2026-012  dispensers and pumps, two quotations → PO-2026-0041 partly received (GRN-0002), part payment (SP-0002)
--   QR-2026-013  600 × 20L + 200 × 10L, three quotations (one does not cover 10L) – waiting for a choice
--   QR-2026-014  stands and pumps, one quotation of two – waiting
-- Goods received are posted as GRN ledger effects (Empty in store / product stock), so Movements shows them.
-- Seed quotations have only the file name of the scanned quotation, no stored file.
-- Runs once: skipped when QR-2026-010 already exists.

DO $$
DECLARE
    s01 BIGINT; s02 BIGINT; s03 BIGINT; s04 BIGINT; s05 BIGINT; s06 BIGINT;
    qr BIGINT; q BIGINT; po BIGINT; grn BIGINT;
BEGIN
    IF EXISTS (SELECT 1 FROM quotation_request WHERE qr_no = 'QR-2026-010') THEN
        RETURN;
    END IF;
    SELECT id INTO s01 FROM supplier WHERE code = 'S01';
    SELECT id INTO s02 FROM supplier WHERE code = 'S02';
    SELECT id INTO s03 FROM supplier WHERE code = 'S03';
    SELECT id INTO s04 FROM supplier WHERE code = 'S04';
    SELECT id INTO s05 FROM supplier WHERE code = 'S05';
    SELECT id INTO s06 FROM supplier WHERE code = 'S06';

    -- ------------------------------------------------------------ QR-2026-010 → PO-2026-0039 cancelled
    INSERT INTO quotation_request (qr_no, request_date, note, status, created_at, created_by)
    VALUES ('QR-2026-010', DATE '2026-06-08', 'Stands for new office customers', 'CLOSED', TIMESTAMPTZ '2026-06-08 10:00:00+05:30', 'nimal.admin')
    RETURNING id INTO qr;
    INSERT INTO qr_item (qr_id, line_no, item_type, item_code, qty) VALUES (qr, 1, 'PRODUCT', 'P03', 20);
    INSERT INTO qr_supplier (qr_id, supplier_id) VALUES (qr, s05);

    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, selected, selection_reason,
                           selected_by, selected_on, created_at, created_by)
    VALUES ('SQ-0001', qr, s05, 'PSW/Q/118', DATE '2026-06-10', DATE '2026-07-10', 21, TRUE, 'Only supplier for steel stands',
            'nimal.admin', DATE '2026-06-11', TIMESTAMPTZ '2026-06-10 11:00:00+05:30', 'nimal.admin')
    RETURNING id INTO q;
    INSERT INTO quotation_line (quotation_id, item_code, unit_price) VALUES (q, 'P03', 3100);

    INSERT INTO purchase_order (po_no, qr_id, quotation_id, supplier_id, po_date, expected_date, status, approved_by, approved_on, cancel_reason,
                                created_at, created_by)
    VALUES ('PO-2026-0039', qr, q, s05, DATE '2026-06-11', DATE '2026-07-02', 'CANCELLED', 'nimal.admin', DATE '2026-06-11',
            'Supplier could not deliver before July – re-order later', TIMESTAMPTZ '2026-06-11 10:05:00+05:30', 'nimal.admin')
    RETURNING id INTO po;
    INSERT INTO po_line (po_id, line_no, item_type, item_code, qty, unit_price) VALUES (po, 1, 'PRODUCT', 'P03', 20, 3100);
    INSERT INTO po_status_history (po_id, status, ts, username, note) VALUES
        (po, 'DRAFT',     TIMESTAMPTZ '2026-06-11 10:05:00+05:30', 'nimal.admin', NULL),
        (po, 'APPROVED',  TIMESTAMPTZ '2026-06-11 10:10:00+05:30', 'nimal.admin', NULL),
        (po, 'CANCELLED', TIMESTAMPTZ '2026-07-02 09:30:00+05:30', 'nimal.admin', 'Supplier could not deliver before July – re-order later');

    -- ------------------------------------------------------------ QR-2026-011 → PO-2026-0040 received, paid
    INSERT INTO quotation_request (qr_no, request_date, required_by, note, status, created_at, created_by)
    VALUES ('QR-2026-011', DATE '2026-07-20', DATE '2026-08-05', 'Pool top-up before school term', 'CLOSED',
            TIMESTAMPTZ '2026-07-20 10:00:00+05:30', 'nimal.admin')
    RETURNING id INTO qr;
    INSERT INTO qr_item (qr_id, line_no, item_type, item_code, qty) VALUES (qr, 1, 'BOTTLE', 'B20', 120);
    INSERT INTO qr_supplier (qr_id, supplier_id) VALUES (qr, s01), (qr, s02), (qr, s03);

    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, notes, attachment_name, selected,
                           selection_reason, selected_by, selected_on, created_at, created_by)
    VALUES ('SQ-0002', qr, s01, 'LPC-Q-2207', DATE '2026-07-22', DATE '2026-08-21', 10, 'Free delivery to Nugegoda', 'LPC-Q-2207.pdf', TRUE,
            'Lowest price with free delivery; good quality on last order', 'nimal.admin', DATE '2026-07-24',
            TIMESTAMPTZ '2026-07-22 11:00:00+05:30', 'nimal.admin')
    RETURNING id INTO q;
    INSERT INTO quotation_line (quotation_id, item_code, unit_price) VALUES (q, 'B20', 620);
    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, notes, attachment_name,
                           created_at, created_by)
    VALUES ('SQ-0003', qr, s02, 'CPI/2026/311', DATE '2026-07-22', DATE '2026-08-15', 7, 'Transport Rs. 4,500 extra', 'CPI-2026-311.pdf',
            TIMESTAMPTZ '2026-07-22 11:10:00+05:30', 'nimal.admin');
    INSERT INTO quotation_line (quotation_id, item_code, unit_price) SELECT id, 'B20', 655 FROM quotation WHERE quote_no = 'SQ-0003';
    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, attachment_name,
                           created_at, created_by)
    VALUES ('SQ-0004', qr, s03, 'KP-0723', DATE '2026-07-23', DATE '2026-08-07', 14, 'KP-0723.jpg',
            TIMESTAMPTZ '2026-07-23 11:00:00+05:30', 'nimal.admin');
    INSERT INTO quotation_line (quotation_id, item_code, unit_price) SELECT id, 'B20', 640 FROM quotation WHERE quote_no = 'SQ-0004';

    INSERT INTO purchase_order (po_no, qr_id, quotation_id, supplier_id, po_date, expected_date, status, approved_by, approved_on, sent_on,
                                created_at, created_by)
    VALUES ('PO-2026-0040', qr, q, s01, DATE '2026-07-24', DATE '2026-08-04', 'RECEIVED', 'nimal.admin', DATE '2026-07-24', DATE '2026-07-25',
            TIMESTAMPTZ '2026-07-24 10:05:00+05:30', 'nimal.admin')
    RETURNING id INTO po;
    INSERT INTO po_line (po_id, line_no, item_type, item_code, qty, unit_price, received_qty, damaged_qty)
    VALUES (po, 1, 'BOTTLE', 'B20', 120, 620, 120, 2);
    INSERT INTO po_status_history (po_id, status, ts, username, note) VALUES
        (po, 'DRAFT',    TIMESTAMPTZ '2026-07-24 10:05:00+05:30', 'nimal.admin', NULL),
        (po, 'APPROVED', TIMESTAMPTZ '2026-07-24 10:10:00+05:30', 'nimal.admin', NULL),
        (po, 'SENT',     TIMESTAMPTZ '2026-07-25 09:00:00+05:30', 'nimal.admin', NULL),
        (po, 'RECEIVED', TIMESTAMPTZ '2026-08-04 14:20:00+05:30', 'nimal.admin', 'GRN-0001');

    INSERT INTO goods_receipt (grn_no, po_id, supplier_id, received_date, delivery_note, note, value, created_at, created_by)
    VALUES ('GRN-0001', po, s01, DATE '2026-08-04', 'LPC-DN-5531', '2 bottles cracked on arrival – supplier informed', 73160,
            TIMESTAMPTZ '2026-08-04 14:20:00+05:30', 'nimal.admin')
    RETURNING id INTO grn;
    INSERT INTO grn_line (grn_id, po_line_id, item_type, item_code, received, damaged, unit_price)
    SELECT grn, id, 'BOTTLE', 'B20', 120, 2, 620 FROM po_line WHERE po_id = po;
    UPDATE stock_balance SET qty = qty + 118 WHERE bottle_type_code = 'B20' AND bucket = 'EMPTY';
    INSERT INTO ledger_effect (source_type, source_id, doc_no, target, bottle_type_code, field, delta, created_at, created_by)
    VALUES ('GRN', grn, 'GRN-0001', 'STOCK', 'B20', 'EMPTY', 118, TIMESTAMPTZ '2026-08-04 14:20:00+05:30', 'nimal.admin');
    INSERT INTO stock_movement (movement_date, source_type, source_id, doc_no, item_code, bucket, qty_delta, description, created_at, created_by)
    VALUES (DATE '2026-08-04', 'GRN', grn, 'GRN-0001', 'B20', 'EMPTY', 118, 'Goods received – PO-2026-0040',
            TIMESTAMPTZ '2026-08-04 14:20:00+05:30', 'nimal.admin');

    INSERT INTO supplier_payment (sp_no, supplier_id, po_id, pay_date, amount, method, reference, note, created_at, created_by)
    VALUES ('SP-0001', s01, po, DATE '2026-08-28', 73160, 'BANK_TRANSFER', 'BOC-TRF-882140', 'Full settlement PO PO-2026-0040',
            TIMESTAMPTZ '2026-08-28 15:00:00+05:30', 'shanika.acc');

    -- ------------------------------------------------------------ QR-2026-012 → PO-2026-0041 partly received, part paid
    INSERT INTO quotation_request (qr_no, request_date, required_by, note, status, created_at, created_by)
    VALUES ('QR-2026-012', DATE '2026-08-18', DATE '2026-09-05', 'Dispensers for office customers', 'CLOSED',
            TIMESTAMPTZ '2026-08-18 10:00:00+05:30', 'nimal.admin')
    RETURNING id INTO qr;
    INSERT INTO qr_item (qr_id, line_no, item_type, item_code, qty) VALUES
        (qr, 1, 'PRODUCT', 'P01', 6), (qr, 2, 'PRODUCT', 'P02', 8), (qr, 3, 'PRODUCT', 'P05', 12);
    INSERT INTO qr_supplier (qr_id, supplier_id) VALUES (qr, s04), (qr, s06);

    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, notes, attachment_name, selected,
                           selection_reason, selected_by, selected_on, created_at, created_by)
    VALUES ('SQ-0005', qr, s04, 'HC/TQ/0820', DATE '2026-08-20', DATE '2026-09-20', 14, '1-year warranty on dispensers', 'HomeCool-TQ-0820.pdf',
            TRUE, 'Better warranty and faster delivery; total close to CoolTech', 'nimal.admin', DATE '2026-08-24',
            TIMESTAMPTZ '2026-08-20 11:00:00+05:30', 'nimal.admin')
    RETURNING id INTO q;
    INSERT INTO quotation_line (quotation_id, item_code, unit_price) VALUES (q, 'P01', 30500), (q, 'P02', 11200), (q, 'P05', 2650);
    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, notes, created_at, created_by)
    VALUES ('SQ-0006', qr, s06, 'CT-Q-4471', DATE '2026-08-20', DATE '2026-09-05', 30, '6-month warranty only',
            TIMESTAMPTZ '2026-08-20 11:10:00+05:30', 'nimal.admin');
    INSERT INTO quotation_line (quotation_id, item_code, unit_price)
    SELECT x.id, v.item, v.price FROM quotation x
    CROSS JOIN (VALUES ('P01', 29800), ('P02', 11900), ('P05', 2800)) AS v (item, price) WHERE x.quote_no = 'SQ-0006';

    INSERT INTO purchase_order (po_no, qr_id, quotation_id, supplier_id, po_date, expected_date, status, approved_by, approved_on, sent_on,
                                created_at, created_by)
    VALUES ('PO-2026-0041', qr, q, s04, DATE '2026-08-24', DATE '2026-09-07', 'PARTLY_RECEIVED', 'nimal.admin', DATE '2026-08-24',
            DATE '2026-08-25', TIMESTAMPTZ '2026-08-24 10:05:00+05:30', 'nimal.admin')
    RETURNING id INTO po;
    INSERT INTO po_line (po_id, line_no, item_type, item_code, qty, unit_price, received_qty, damaged_qty) VALUES
        (po, 1, 'PRODUCT', 'P01', 6, 30500, 6, 0),
        (po, 2, 'PRODUCT', 'P02', 8, 11200, 4, 0),
        (po, 3, 'PRODUCT', 'P05', 12, 2650, 12, 1);
    INSERT INTO po_status_history (po_id, status, ts, username, note) VALUES
        (po, 'DRAFT',           TIMESTAMPTZ '2026-08-24 10:05:00+05:30', 'nimal.admin', NULL),
        (po, 'APPROVED',        TIMESTAMPTZ '2026-08-24 10:10:00+05:30', 'nimal.admin', NULL),
        (po, 'SENT',            TIMESTAMPTZ '2026-08-25 09:00:00+05:30', 'nimal.admin', NULL),
        (po, 'PARTLY_RECEIVED', TIMESTAMPTZ '2026-09-10 13:40:00+05:30', 'nimal.admin', 'GRN-0002');

    INSERT INTO goods_receipt (grn_no, po_id, supplier_id, received_date, delivery_note, note, value, created_at, created_by)
    VALUES ('GRN-0002', po, s04, DATE '2026-09-10', 'HC-DN-20931', 'Balance 4 table-top dispensers to follow', 256950,
            TIMESTAMPTZ '2026-09-10 13:40:00+05:30', 'nimal.admin')
    RETURNING id INTO grn;
    INSERT INTO grn_line (grn_id, po_line_id, item_type, item_code, received, damaged, unit_price)
    SELECT grn, l.id, 'PRODUCT', l.item_code, v.received, v.damaged, l.unit_price
    FROM po_line l JOIN (VALUES ('P01', 6, 0), ('P02', 4, 0), ('P05', 12, 1)) AS v (item, received, damaged) ON v.item = l.item_code
    WHERE l.po_id = po ORDER BY l.line_no;
    CREATE TEMP TABLE grn_products (item VARCHAR(10), qty INTEGER) ON COMMIT DROP;
    INSERT INTO grn_products VALUES ('P01', 6), ('P02', 4), ('P05', 11);
    UPDATE product p SET stock_qty = p.stock_qty + g.qty FROM grn_products g WHERE p.code = g.item;
    INSERT INTO ledger_effect (source_type, source_id, doc_no, target, product_code, field, delta, created_at, created_by)
    SELECT 'GRN', grn, 'GRN-0002', 'PRODUCT', item, 'STOCK', qty, TIMESTAMPTZ '2026-09-10 13:40:00+05:30', 'nimal.admin' FROM grn_products;
    INSERT INTO stock_movement (movement_date, source_type, source_id, doc_no, item_code, bucket, qty_delta, description, created_at, created_by)
    SELECT DATE '2026-09-10', 'GRN', grn, 'GRN-0002', item, 'STOCK', qty, 'Goods received – PO-2026-0041',
           TIMESTAMPTZ '2026-09-10 13:40:00+05:30', 'nimal.admin' FROM grn_products;

    INSERT INTO supplier_payment (sp_no, supplier_id, po_id, pay_date, amount, method, reference, note, created_at, created_by)
    VALUES ('SP-0002', s04, po, DATE '2026-09-15', 150000, 'CHEQUE', 'Cheque 004512 (Sampath Bank)', 'Part payment',
            TIMESTAMPTZ '2026-09-15 15:00:00+05:30', 'shanika.acc');

    -- ------------------------------------------------------------ QR-2026-013 – three quotations, waiting for a choice
    INSERT INTO quotation_request (qr_no, request_date, required_by, note, status, created_at, created_by)
    VALUES ('QR-2026-013', DATE '2026-09-22', DATE '2026-10-15', 'Bottles for the new Kottawa route and replacement of written-off bottles',
            'QUOTES_RECEIVED', TIMESTAMPTZ '2026-09-22 10:00:00+05:30', 'nimal.admin')
    RETURNING id INTO qr;
    INSERT INTO qr_item (qr_id, line_no, item_type, item_code, qty) VALUES (qr, 1, 'BOTTLE', 'B20', 600), (qr, 2, 'BOTTLE', 'B10', 200);
    INSERT INTO qr_supplier (qr_id, supplier_id) VALUES (qr, s01), (qr, s02), (qr, s03);

    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, notes, attachment_name,
                           created_at, created_by)
    VALUES ('SQ-0007', qr, s02, 'CPI/2026/402', DATE '2026-09-24', DATE '2026-10-24', 7,
            'Price includes transport. 2% discount if paid within 7 days.', 'CPI-2026-402.pdf', TIMESTAMPTZ '2026-09-24 11:00:00+05:30', 'nimal.admin'),
           ('SQ-0008', qr, s01, 'LPC-Q-2509', DATE '2026-09-25', DATE '2026-10-25', 12,
            'Free delivery. 10L stock limited – 2 shipments.', 'LPC-Q-2509.pdf', TIMESTAMPTZ '2026-09-25 11:00:00+05:30', 'nimal.admin'),
           ('SQ-0009', qr, s03, 'KP-0928', DATE '2026-09-28', DATE '2026-10-12', 21,
            'Cannot supply 10L bottles. Advance 30% required.', 'KP-0928.jpg', TIMESTAMPTZ '2026-09-28 11:00:00+05:30', 'nimal.admin');
    INSERT INTO quotation_line (quotation_id, item_code, unit_price)
    SELECT x.id, v.item, v.price FROM quotation x
    JOIN (VALUES ('SQ-0007', 'B20', 640), ('SQ-0007', 'B10', 410),
                 ('SQ-0008', 'B20', 615), ('SQ-0008', 'B10', 430),
                 ('SQ-0009', 'B20', 605), ('SQ-0009', 'B10', NULL)) AS v (quote_no, item, price) ON v.quote_no = x.quote_no;

    -- ------------------------------------------------------------ QR-2026-014 – one quotation of two
    INSERT INTO quotation_request (qr_no, request_date, required_by, note, status, created_at, created_by)
    VALUES ('QR-2026-014', DATE '2026-09-29', DATE '2026-10-20', 'Stands and pumps for Sinhala & Tamil New Year promotion stock',
            'QUOTES_RECEIVED', TIMESTAMPTZ '2026-09-29 10:00:00+05:30', 'nimal.admin')
    RETURNING id INTO qr;
    INSERT INTO qr_item (qr_id, line_no, item_type, item_code, qty) VALUES (qr, 1, 'PRODUCT', 'P03', 20), (qr, 2, 'PRODUCT', 'P04', 50);
    INSERT INTO qr_supplier (qr_id, supplier_id) VALUES (qr, s04), (qr, s05);
    INSERT INTO quotation (quote_no, qr_id, supplier_id, supplier_ref, quote_date, valid_until, delivery_days, notes, created_at, created_by)
    VALUES ('SQ-0010', qr, s05, 'PSW/Q/162', DATE '2026-10-01', DATE '2026-10-31', 21, 'Stands only', TIMESTAMPTZ '2026-10-01 11:00:00+05:30',
            'nimal.admin')
    RETURNING id INTO q;
    INSERT INTO quotation_line (quotation_id, item_code, unit_price) VALUES (q, 'P03', 3200), (q, 'P04', NULL);

    -- ------------------------------------------------------------ next numbers
    UPDATE doc_sequence SET next_value = GREATEST(next_value, 15), period_key = '2026' WHERE name = 'QUOTATION_REQUEST';
    UPDATE doc_sequence SET next_value = GREATEST(next_value, 11) WHERE name = 'SUPPLIER_QUOTATION';
    UPDATE doc_sequence SET next_value = GREATEST(next_value, 42), period_key = '2026' WHERE name = 'PURCHASE_ORDER';
    UPDATE doc_sequence SET next_value = GREATEST(next_value, 3) WHERE name = 'GOODS_RECEIPT';
    UPDATE doc_sequence SET next_value = GREATEST(next_value, 3) WHERE name = 'SUPPLIER_PAYMENT';
END $$;
