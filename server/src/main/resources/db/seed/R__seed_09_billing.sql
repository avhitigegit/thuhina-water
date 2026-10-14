-- Seed customer payments and the August monthly invoices for LOCAL and UAT only (never production) – from the prototype
-- (prototype/assets/js/store.js simulation: "Customer payments", generateInvoices('2026-08') on 01/09/2026).
-- The seed has no daily bills (only the opening balances of R__seed_06), so the amounts follow the prototype's rules on the
-- opening balances: Monthly bill customers pay their July balance on 08/08 (Ceyknit part only); Credit customers pay part
-- on the 10th and 25th. August invoices: only customers with something due get one (the others are skipped as
-- "No deliveries and nothing due"). Every payment lowers customer_account in the same way as the application.
-- Runs once: skipped when seed payments already exist.

DO $$
DECLARE
    r       RECORD;
    v_n     BIGINT;
    v_bal   NUMERIC(14,2);
    v_inv   BIGINT;
    v_count INTEGER := 0;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM customer_payment WHERE created_by = 'seed') THEN
        FOR r IN
            SELECT c.id, v.pay_date, v.amount, v.method, v.reference, v.bank, v.note, v.at
            FROM (VALUES
                (DATE '2026-08-08', 'C0004', 7800,  'BANK_TRANSFER', 'TRF-771200',    NULL,              'July invoice',        TIMESTAMPTZ '2026-08-08 10:05:00+05:30'),
                (DATE '2026-08-08', 'C0010', 4160,  'CHEQUE',        'Cheque 120331', 'Commercial Bank', 'July invoice',        TIMESTAMPTZ '2026-08-08 10:12:00+05:30'),
                (DATE '2026-08-08', 'C0018', 10560, 'BANK_TRANSFER', 'TRF-771226',    NULL,              'July invoice',        TIMESTAMPTZ '2026-08-08 10:20:00+05:30'),
                (DATE '2026-08-08', 'C0025', 3840,  'CHEQUE',        'Cheque 120333', 'Sampath Bank',    'July invoice',        TIMESTAMPTZ '2026-08-08 10:31:00+05:30'),
                (DATE '2026-08-08', 'C0030', 6960,  'BANK_TRANSFER', 'TRF-771252',    NULL,              'July invoice',        TIMESTAMPTZ '2026-08-08 10:40:00+05:30'),
                (DATE '2026-08-08', 'C0008', 6000,  'CHEQUE',        'Cheque 330981', 'HNB',             'Part payment – July', TIMESTAMPTZ '2026-08-08 11:02:00+05:30'),
                (DATE '2026-08-10', 'C0003', 4900,  'CHEQUE',        'Cheque 440074', 'Sampath Bank',    'Part payment',        TIMESTAMPTZ '2026-08-10 15:10:00+05:30'),
                (DATE '2026-08-10', 'C0007', 2600,  'CASH',          NULL,            NULL,              'Part payment',        TIMESTAMPTZ '2026-08-10 15:22:00+05:30'),
                (DATE '2026-08-10', 'C0021', 6400,  'CHEQUE',        'Cheque 440740', 'BOC',             'Part payment',        TIMESTAMPTZ '2026-08-10 15:40:00+05:30'),
                (DATE '2026-08-25', 'C0003', 1000,  'CHEQUE',        'Cheque 440081', 'Sampath Bank',    'Part payment',        TIMESTAMPTZ '2026-08-25 14:05:00+05:30'),
                (DATE '2026-08-25', 'C0007', 500,   'CASH',          NULL,            NULL,              'Part payment',        TIMESTAMPTZ '2026-08-25 14:18:00+05:30'),
                (DATE '2026-08-25', 'C0021', 3000,  'CHEQUE',        'Cheque 440752', 'BOC',             'Part payment',        TIMESTAMPTZ '2026-08-25 14:30:00+05:30'),
                (DATE '2026-09-10', 'C0012', 2000,  'CHEQUE',        'Cheque 440407', 'People''s Bank',  'Part payment',        TIMESTAMPTZ '2026-09-10 15:02:00+05:30'),
                (DATE '2026-09-10', 'C0021', 1500,  'CHEQUE',        'Cheque 440768', 'BOC',             'Part payment',        TIMESTAMPTZ '2026-09-10 15:15:00+05:30'),
                (DATE '2026-09-25', 'C0021', 500,   'CASH',          NULL,            NULL,              'Part payment',        TIMESTAMPTZ '2026-09-25 14:40:00+05:30')
            ) AS v (pay_date, code, amount, method, reference, bank, note, at)
            JOIN customer c ON c.code = v.code
            ORDER BY v.at
        LOOP
            -- The August invoices are generated on 01/09, before the September payments.
            IF r.pay_date >= DATE '2026-09-01' AND v_count = 0 THEN
                v_count := 1;
                FOR v_inv IN
                    SELECT c.id FROM customer c WHERE c.pay_type = 'MONTHLY_BILL' ORDER BY lower(c.name), c.id
                LOOP
                    WITH t AS (
                        SELECT
                            (SELECT count(*) FROM sales_txn s WHERE s.customer_id = v_inv AND s.txn_date BETWEEN DATE '2026-08-01' AND DATE '2026-08-31'
                                AND s.kind <> 'OPENING_BALANCE') AS deliveries,
                            (SELECT coalesce(sum(s.amount), 0) FROM sales_txn s WHERE s.customer_id = v_inv
                                AND s.txn_date BETWEEN DATE '2026-08-01' AND DATE '2026-08-31' AND s.kind <> 'OPENING_BALANCE') AS charges,
                            (SELECT coalesce(sum(s.paid_on_bill), 0) FROM sales_txn s WHERE s.customer_id = v_inv
                                AND s.txn_date BETWEEN DATE '2026-08-01' AND DATE '2026-08-31' AND s.kind <> 'OPENING_BALANCE') AS paid,
                            (SELECT coalesce(sum(s.amount - s.paid_on_bill), 0) FROM sales_txn s WHERE s.customer_id = v_inv
                                AND (s.txn_date < DATE '2026-08-01' OR (s.kind = 'OPENING_BALANCE' AND s.txn_date <= DATE '2026-08-31')))
                              - (SELECT coalesce(sum(p.amount), 0) FROM customer_payment p WHERE p.customer_id = v_inv
                                AND p.pay_date < DATE '2026-08-01') AS opening,
                            (SELECT coalesce(sum(p.amount), 0) FROM customer_payment p WHERE p.customer_id = v_inv
                                AND p.pay_date BETWEEN DATE '2026-08-01' AND DATE '2026-08-31') AS payments
                    ), due AS (
                        SELECT t.*, t.charges - t.paid AS amount, t.opening + t.charges - t.paid - t.payments AS total FROM t
                    ), num AS (
                        UPDATE doc_sequence
                        SET next_value = CASE WHEN period_key = '2608' THEN next_value + 1 ELSE 2 END, period_key = '2608'
                        WHERE name = 'MONTHLY_INVOICE' AND EXISTS (SELECT 1 FROM due WHERE deliveries > 0 OR total > 0)
                        RETURNING next_value - 1 AS n
                    )
                    INSERT INTO monthly_invoice (invoice_no, customer_id, period, period_from, period_to, invoice_date, due_date,
                                                 opening_balance, charges, paid_on_bills, payments, amount, total_due, created_at, created_by)
                    SELECT 'INV-2608-' || lpad(num.n::text, 4, '0'), v_inv, '2026-08', DATE '2026-08-01', DATE '2026-08-31',
                           DATE '2026-09-01', DATE '2026-09-01' + (SELECT CASE WHEN terms_days > 0 THEN terms_days ELSE 30 END FROM customer WHERE id = v_inv),
                           due.opening, due.charges, due.paid, due.payments, due.amount, due.total,
                           TIMESTAMPTZ '2026-09-01 09:15:00+05:30', 'seed'
                    FROM due, num;
                END LOOP;
                INSERT INTO invoice_txn (invoice_id, txn_id)
                SELECT i.id, s.id FROM monthly_invoice i
                JOIN sales_txn s ON s.customer_id = i.customer_id AND s.txn_date BETWEEN i.period_from AND i.period_to
                    AND s.kind <> 'OPENING_BALANCE'
                WHERE i.created_by = 'seed';
            END IF;

            INSERT INTO customer_account (customer_id, balance) VALUES (r.id, 0) ON CONFLICT DO NOTHING;
            SELECT balance INTO v_bal FROM customer_account WHERE customer_id = r.id;
            UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'RECEIPT' RETURNING next_value - 1 INTO v_n;
            INSERT INTO customer_payment (receipt_no, customer_id, pay_date, amount, method, reference, bank, note, balance_before,
                                          created_at, created_by)
            VALUES ('RC-' || lpad(v_n::text, 5, '0'), r.id, r.pay_date, r.amount, r.method, r.reference, r.bank, r.note, v_bal,
                    r.at, 'seed');
            UPDATE customer_account SET balance = balance - r.amount WHERE customer_id = r.id;
        END LOOP;
    END IF;
END $$;
