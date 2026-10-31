-- M17 follow-up: bills saved by the delivery staff on the phone (My Deliveries) are marked, so Enter Bills and Sales & Bills
-- can show them apart from the paper bills typed in the office.
ALTER TABLE sales_txn ADD COLUMN entered_on_phone BOOLEAN NOT NULL DEFAULT FALSE;
