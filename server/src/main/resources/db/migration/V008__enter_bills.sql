-- M07 Enter Bills (design 5.4, 11 NFR-04). No new tables: the grid posts delivery bills through M06.
-- Indexes for the due list (active customers by delivery day) and for the suggested next paper bill number
-- (the highest numeric paper bill number, read from the index instead of scanning every bill).

CREATE INDEX ix_customer_active_day ON customer (delivery_day) WHERE status = 'ACTIVE';

CREATE INDEX ix_sales_txn_paper_num ON sales_txn ((paper_bill_no::bigint))
    WHERE paper_bill_no ~ '^[0-9]{1,18}$' AND kind <> 'REVERSAL';
