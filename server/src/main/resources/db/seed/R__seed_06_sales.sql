-- Seed customer opening bottles and opening balances for LOCAL and UAT only (never production) – from the prototype
-- (prototype/assets/js/data.js customers, openingHeld; store.js migration on 31/07/2026).
-- 1. Bottles held at migration: customers registered on or before 01/08/2026 hold their usual quantity, unless
--    openingHeld says otherwise. Posted as IMPORT effects (held + With customers stock), so Movements shows them.
-- 2. Balances owed in the old ledger: OPENING_BALANCE bills (B…) that add to the account balance.
-- Runs once: skipped when the opening customer import already exists.

DO $$
DECLARE
    r RECORD;
    seq_value BIGINT;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ledger_effect WHERE source_type = 'IMPORT' AND doc_no = 'OPENING-CUST') THEN
        CREATE TEMP TABLE opening_held (customer_id BIGINT, bottle VARCHAR(10), qty INTEGER) ON COMMIT DROP;
        INSERT INTO opening_held
        SELECT c.id, u.bottle_type_code, coalesce(o.qty, u.qty)
        FROM customer c
        JOIN customer_usual_qty u ON u.customer_id = c.id
        LEFT JOIN (VALUES ('C0003', 'B20', 5), ('C0004', 'B20', 8), ('C0008', 'B20', 12), ('C0018', 'B20', 10),
                          ('C0030', 'B20', 7), ('C0014', 'B20', 5), ('C0014', 'B10', 2)) AS o (code, bottle, qty)
               ON o.code = c.code AND o.bottle = u.bottle_type_code
        WHERE c.registered_on <= DATE '2026-08-01' AND c.code ~ '^C00[0-4][0-9]$';
        DELETE FROM opening_held WHERE qty <= 0;

        INSERT INTO customer_bottle_balance (customer_id, bottle_type_code)
        SELECT customer_id, bottle FROM opening_held ON CONFLICT DO NOTHING;
        UPDATE customer_bottle_balance b SET held = b.held + o.qty
        FROM opening_held o WHERE b.customer_id = o.customer_id AND b.bottle_type_code = o.bottle;
        UPDATE stock_balance s SET qty = s.qty + t.qty
        FROM (SELECT bottle, sum(qty) AS qty FROM opening_held GROUP BY bottle) t
        WHERE s.bottle_type_code = t.bottle AND s.bucket = 'CUSTOMERS';

        INSERT INTO ledger_effect (source_type, source_id, doc_no, target, bottle_type_code, customer_id, field, delta, created_by)
        SELECT 'IMPORT', NULL, 'OPENING-CUST', 'CUSTOMER_BOTTLE', bottle, customer_id, 'HELD', qty, 'seed' FROM opening_held;
        INSERT INTO ledger_effect (source_type, source_id, doc_no, target, bottle_type_code, field, delta, created_by)
        SELECT 'IMPORT', NULL, 'OPENING-CUST', 'STOCK', bottle, 'CUSTOMERS', sum(qty), 'seed' FROM opening_held GROUP BY bottle;
        INSERT INTO stock_movement (movement_date, source_type, source_id, doc_no, item_code, bucket, qty_delta, description, created_by)
        SELECT DATE '2026-07-31', 'IMPORT', NULL, 'OPENING-CUST', bottle, 'CUSTOMERS', sum(qty),
               'Opening customer bottles (migration)', 'seed'
        FROM opening_held GROUP BY bottle;

        -- Opening balances: one OPENING_BALANCE bill each, numbered from the BILL sequence.
        FOR r IN
            SELECT c.id, v.amount, v.on_date
            FROM (VALUES ('C0003', 6200, DATE '2026-07-24'), ('C0004', 7800, DATE '2026-07-31'), ('C0007', 3300, DATE '2026-07-28'),
                         ('C0008', 12760, DATE '2026-07-31'), ('C0010', 4160, DATE '2026-07-31'), ('C0012', 9900, DATE '2026-06-20'),
                         ('C0018', 10560, DATE '2026-07-31'), ('C0021', 12800, DATE '2026-08-01'), ('C0025', 3840, DATE '2026-07-31'),
                         ('C0030', 6960, DATE '2026-07-31')) AS v (code, amount, on_date)
            JOIN customer c ON c.code = v.code
            ORDER BY v.code
        LOOP
            UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'BILL' RETURNING next_value - 1 INTO seq_value;
            INSERT INTO sales_txn (bill_no, kind, txn_date, customer_id, amount, paid_on_bill, note, created_at, created_by)
            VALUES ('B' || lpad(seq_value::text, 6, '0'), 'OPENING_BALANCE', r.on_date, r.id, r.amount, 0,
                    'Opening balance migrated from the old ledger', TIMESTAMPTZ '2026-07-31 16:30:00+05:30', 'seed');
            INSERT INTO customer_account (customer_id, balance) VALUES (r.id, 0) ON CONFLICT DO NOTHING;
            UPDATE customer_account SET balance = balance + r.amount WHERE customer_id = r.id;
        END LOOP;
    END IF;
END $$;
