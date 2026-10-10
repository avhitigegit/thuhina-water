-- Seed opening stock and minimum levels for LOCAL and UAT only (never production) – from the prototype
-- (prototype/assets/js/data.js stockOpening, minLevels). Posted as IMPORT effects so it shows in Movements.
-- Runs once: skipped when an opening stock import already exists. (Product opening stock comes from the product seed.)

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM ledger_effect WHERE source_type = 'IMPORT' AND doc_no = 'OPENING') THEN
        CREATE TEMP TABLE opening (bottle VARCHAR(10), bucket VARCHAR(12), qty INTEGER) ON COMMIT DROP;
        INSERT INTO opening VALUES
            ('B20', 'EMPTY', 30), ('B20', 'FILLED', 95), ('B20', 'WRITTEN_OFF', 46),
            ('B10', 'EMPTY', 18), ('B10', 'FILLED', 30), ('B10', 'WRITTEN_OFF', 7);

        UPDATE stock_balance s SET qty = s.qty + o.qty
        FROM opening o WHERE s.bottle_type_code = o.bottle AND s.bucket = o.bucket;

        INSERT INTO ledger_effect (source_type, source_id, doc_no, target, bottle_type_code, field, delta, created_by)
        SELECT 'IMPORT', NULL, 'OPENING', 'STOCK', o.bottle, o.bucket, o.qty, 'seed' FROM opening o;

        INSERT INTO stock_movement (movement_date, source_type, source_id, doc_no, item_code, bucket, qty_delta, description, created_by)
        SELECT DATE '2026-01-01', 'IMPORT', NULL, 'OPENING', o.bottle, o.bucket, o.qty, 'Opening stock import', 'seed' FROM opening o;
    END IF;
END $$;

INSERT INTO min_stock_level (bottle_type_code, min_filled, updated_by) VALUES
    ('B20', 150, 'seed'),
    ('B10', 25, 'seed')
ON CONFLICT DO NOTHING;
