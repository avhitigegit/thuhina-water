-- M17 My Deliveries (phone): the cheque number / bank transfer reference of money received on a delivery bill.
ALTER TABLE sales_txn ADD COLUMN pay_reference VARCHAR(100);
