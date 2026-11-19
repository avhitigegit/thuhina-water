-- Factory payments clear the oldest filling cost first (like supplier and customer payments): no month is chosen.
-- Payments made before keep the month they were recorded for; new ones have none.
ALTER TABLE factory_payment ALTER COLUMN period DROP NOT NULL;
