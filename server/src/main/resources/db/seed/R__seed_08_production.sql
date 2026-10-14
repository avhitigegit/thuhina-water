-- Seed Filling Factory history for LOCAL and UAT only (never production) – modelled on the prototype
-- (prototype/assets/js/store.js: empties go to the factory every Saturday – 20L to AquaSeal (F01), 10L to Pure Lanka (F02) –
-- and come back filled on Sunday; about 1 in 75 is rejected):
--   * 9 weekly loads 01/08 … 26/09/2026 (two dispatches each Saturday, one per factory), returned the next day;
--   * the 20L batch of 26/09 was short-shipped by 6 – those 6 are still at the factory;
--   * 01/10/2026: one dispatch with both bottle types to AquaSeal (mid-week top-up) – still at the factory;
--   * AquaSeal's August filling cost paid on 10/09/2026 (FP-0001).
-- The prototype takes the quantities from its daily sales simulation, which the seed does not replay, so the quantities here are
-- fixed. Every dispatch and return is posted to the ledger (BATCH / RETURN effects, Movements shows them). Because customer
-- deliveries and empties collected in this period are not replayed, one IMPORT posting "SEED-FACTORY" moves the filled bottles
-- that came back out to customers again as empties (FILLED −filled, EMPTY +filled), so Filled in store stays as seeded and
-- Empty in store goes down only by the bottles still at the factory and the rejects.
-- Runs once: skipped when FD-0001 already exists.

DO $$
DECLARE
    f01 BIGINT; f02 BIGINT;
    b20 NUMERIC := 60; b10_f02 NUMERIC := 34; b20_f01 NUMERIC := 60; b10_f01 NUMERIC := 35;
    q20 INTEGER[] := ARRAY[280, 300, 290, 310, 300, 320, 300, 310, 300];
    q10 INTEGER[] := ARRAY[40, 35, 45, 40, 38, 42, 40, 44, 40];
    sat DATE;
    i INTEGER;
    disp BIGINT; batch BIGINT; ret BIGINT;
    n BIGINT;
    v_qty INTEGER; v_rej INTEGER; v_short INTEGER; v_filled INTEGER;
    fd TEXT; fb TEXT; fr TEXT;
    aug NUMERIC := 0;
    v_bt TEXT; v_fac BIGINT; v_chg NUMERIC;
BEGIN
    IF EXISTS (SELECT 1 FROM factory_dispatch WHERE dispatch_no = 'FD-0001') THEN
        RETURN;
    END IF;
    SELECT id INTO f01 FROM factory WHERE code = 'F01';
    SELECT id INTO f02 FROM factory WHERE code = 'F02';
    IF f01 IS NULL OR f02 IS NULL THEN
        RETURN;
    END IF;

    CREATE TEMP TABLE seed_fx (source_type VARCHAR(20), source_id BIGINT, doc_no VARCHAR(30), bottle VARCHAR(10), bucket VARCHAR(12),
                               delta INTEGER, mdate DATE, descr VARCHAR(300)) ON COMMIT DROP;

    FOR i IN 1..9 LOOP
        sat := DATE '2026-08-01' + (i - 1) * 7;
        FOREACH v_bt IN ARRAY ARRAY['B20', 'B10'] LOOP
            IF v_bt = 'B20' THEN v_fac := f01; v_chg := b20; v_qty := q20[i]; ELSE v_fac := f02; v_chg := b10_f02; v_qty := q10[i]; END IF;
            UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'FACTORY_DISPATCH' RETURNING next_value - 1 INTO n;
            fd := 'FD-' || lpad(n::text, 4, '0');
            INSERT INTO factory_dispatch (dispatch_no, factory_id, dispatch_date, vehicle_no, note, created_at, created_by)
            VALUES (fd, v_fac, sat, 'LH-4521', 'Weekly batch', sat + TIME '09:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin')
            RETURNING id INTO disp;
            UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'FACTORY_BATCH' RETURNING next_value - 1 INTO n;
            fb := 'FB-' || lpad(n::text, 4, '0');
            INSERT INTO factory_batch (batch_no, dispatch_id, factory_id, dispatch_date, bottle_type_code, qty_sent, charge_per_bottle,
                                       vehicle_no, note, created_at, created_by)
            VALUES (fb, disp, v_fac, sat, v_bt, v_qty, v_chg, 'LH-4521', 'Weekly batch', sat + TIME '09:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin')
            RETURNING id INTO batch;
            INSERT INTO seed_fx VALUES
                ('BATCH', disp, fd, v_bt, 'EMPTY', -v_qty, sat, 'Dispatched to ' || (SELECT name FROM factory WHERE id = v_fac)),
                ('BATCH', disp, fd, v_bt, 'FACTORY', v_qty, sat, 'Dispatched to ' || (SELECT name FROM factory WHERE id = v_fac));

            -- Back the next day.
            v_rej := v_qty / 75;
            v_short := CASE WHEN sat = DATE '2026-09-26' AND v_bt = 'B20' THEN 6 ELSE 0 END;
            v_filled := v_qty - v_rej - v_short;
            UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'FACTORY_RETURN' RETURNING next_value - 1 INTO n;
            fr := 'FR-' || lpad(n::text, 4, '0');
            INSERT INTO factory_return (return_no, batch_id, return_date, filled_qty, rejected_qty, reject_reason, cost, note,
                                        created_at, created_by)
            VALUES (fr, batch, sat + 1, v_filled, v_rej, CASE WHEN v_rej > 0 THEN 'Cracked neck / failed seal test' END, v_filled * v_chg,
                    CASE WHEN v_short > 0 THEN 'Factory v_short-shipped 6 – to come with next batch' END,
                    (sat + 1) + TIME '16:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin')
            RETURNING id INTO ret;
            INSERT INTO seed_fx VALUES
                ('RETURN', ret, fr, v_bt, 'FACTORY', -(v_filled + v_rej), sat + 1,
                 'Returned from factory – ' || fb || CASE WHEN v_rej > 0 THEN ' (' || v_rej || ' rejected)' ELSE '' END),
                ('RETURN', ret, fr, v_bt, 'FILLED', v_filled, sat + 1,
                 'Returned from factory – ' || fb || CASE WHEN v_rej > 0 THEN ' (' || v_rej || ' rejected)' ELSE '' END);
            IF v_rej > 0 THEN
                INSERT INTO seed_fx VALUES ('RETURN', ret, fr, v_bt, 'WRITTEN_OFF', v_rej, sat + 1,
                                            'Returned from factory – ' || fb || ' (' || v_rej || ' rejected)');
                UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'DAMAGE' RETURNING next_value - 1 INTO n;
                INSERT INTO damage_record (damage_no, damage_date, bottle_type_code, qty, responsibility, location, reason, note,
                                           source_type, source_id, created_at, created_by)
                VALUES ('DMG-' || lpad(n::text, 4, '0'), sat + 1, v_bt, v_rej, 'COMPANY', 'AT_FACTORY', 'Cracked neck / failed seal test',
                        'Rejected in batch ' || fb, 'RETURN', ret, (sat + 1) + TIME '16:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin');
            END IF;
            IF v_fac = f01 AND sat + 1 BETWEEN DATE '2026-08-01' AND DATE '2026-08-31' THEN
                aug := aug + v_filled * v_chg;
            END IF;
        END LOOP;
    END LOOP;

    -- 01/10/2026: both bottle types to AquaSeal in one dispatch – still at the factory.
    UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'FACTORY_DISPATCH' RETURNING next_value - 1 INTO n;
    fd := 'FD-' || lpad(n::text, 4, '0');
    INSERT INTO factory_dispatch (dispatch_no, factory_id, dispatch_date, vehicle_no, note, created_at, created_by)
    VALUES (fd, f01, DATE '2026-10-01', 'LH-4521', 'Mid-week top-up batch (low v_filled stock)',
            TIMESTAMP '2026-10-01 10:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin')
    RETURNING id INTO disp;
    FOREACH v_bt IN ARRAY ARRAY['B20', 'B10'] LOOP
        IF v_bt = 'B20' THEN v_chg := b20_f01; v_qty := 60; ELSE v_chg := b10_f01; v_qty := 8; END IF;
        UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'FACTORY_BATCH' RETURNING next_value - 1 INTO n;
        INSERT INTO factory_batch (batch_no, dispatch_id, factory_id, dispatch_date, bottle_type_code, qty_sent, charge_per_bottle,
                                   vehicle_no, note, created_at, created_by)
        VALUES ('FB-' || lpad(n::text, 4, '0'), disp, f01, DATE '2026-10-01', v_bt, v_qty, v_chg, 'LH-4521',
                'Mid-week top-up batch (low v_filled stock)', TIMESTAMP '2026-10-01 10:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin');
        INSERT INTO seed_fx VALUES
            ('BATCH', disp, fd, v_bt, 'EMPTY', -v_qty, DATE '2026-10-01', 'Dispatched to AquaSeal Bottling (Pvt) Ltd'),
            ('BATCH', disp, fd, v_bt, 'FACTORY', v_qty, DATE '2026-10-01', 'Dispatched to AquaSeal Bottling (Pvt) Ltd');
    END LOOP;

    -- Deliveries and collections of this period are not replayed (see the top of this file).
    INSERT INTO seed_fx
    SELECT 'IMPORT', NULL, 'SEED-FACTORY', bottle, k.bucket, k.sign * sum(delta), DATE '2026-09-30',
           'Seed only – v_filled bottles delivered and empties collected in this history (not replayed)'
    FROM seed_fx CROSS JOIN (VALUES ('FILLED', -1), ('EMPTY', 1)) AS k (bucket, sign)
    WHERE source_type = 'RETURN' AND seed_fx.bucket = 'FILLED'
    GROUP BY bottle, k.bucket, k.sign;

    UPDATE stock_balance s SET qty = s.qty + t.delta
    FROM (SELECT bottle, bucket, sum(delta) AS delta FROM seed_fx GROUP BY bottle, bucket) t
    WHERE s.bottle_type_code = t.bottle AND s.bucket = t.bucket;
    INSERT INTO ledger_effect (source_type, source_id, doc_no, target, bottle_type_code, field, delta, created_at, created_by)
    SELECT source_type, source_id, doc_no, 'STOCK', bottle, bucket, delta, mdate + TIME '12:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin'
    FROM seed_fx WHERE delta <> 0;
    INSERT INTO stock_movement (movement_date, source_type, source_id, doc_no, item_code, bucket, qty_delta, description, created_at, created_by)
    SELECT mdate, source_type, source_id, doc_no, bottle, bucket, delta, descr, mdate + TIME '12:00' AT TIME ZONE 'Asia/Colombo', 'nimal.admin'
    FROM seed_fx WHERE delta <> 0;

    -- AquaSeal's August filling charges, paid on 10/09/2026.
    UPDATE doc_sequence SET next_value = next_value + 1 WHERE name = 'FACTORY_PAYMENT' RETURNING next_value - 1 INTO n;
    INSERT INTO factory_payment (fp_no, factory_id, period, pay_date, amount, method, reference, note, created_at, created_by)
    VALUES ('FP-' || lpad(n::text, 4, '0'), f01, '2026-08', DATE '2026-09-10', aug, 'BANK_TRANSFER', 'COM-TRF-551902',
            'August filling charges', TIMESTAMP '2026-09-10 15:00' AT TIME ZONE 'Asia/Colombo', 'shanika.acc');
END $$;
