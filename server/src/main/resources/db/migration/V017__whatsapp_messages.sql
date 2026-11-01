-- M13 WhatsApp messages redesign (FR-61): eight customer messages, each with its own switch.
--   WELCOME (registration), QUOTATION (office presses "Send on WhatsApp"), DELIVERY_BILL (every sales bill – replaces
--   LEFT_AT_DOOR), BILL_CANCELLED (reversal), PAYMENT_RECEIPT (payment apart from a bill), MONTHLY_INVOICE,
--   PAYMENT_DUE_SOON (N days before the due date), PAYMENT_OVERDUE (N days after – replaces the weekly PAYMENT_REMINDER).
-- A quotation can go to an organisation that is not a customer yet, so it may have no customer_id.

ALTER TABLE whatsapp_message DROP CONSTRAINT ck_whatsapp_type;
ALTER TABLE whatsapp_message DROP CONSTRAINT ck_whatsapp_customer;

UPDATE whatsapp_message SET type = 'DELIVERY_BILL' WHERE type = 'LEFT_AT_DOOR';
UPDATE whatsapp_message SET type = 'PAYMENT_OVERDUE' WHERE type = 'PAYMENT_REMINDER';

ALTER TABLE whatsapp_message ADD CONSTRAINT ck_whatsapp_type CHECK (type IN ('WELCOME', 'QUOTATION', 'DELIVERY_BILL',
    'BILL_CANCELLED', 'PAYMENT_RECEIPT', 'MONTHLY_INVOICE', 'PAYMENT_DUE_SOON', 'PAYMENT_OVERDUE', 'TEST'));
ALTER TABLE whatsapp_message ADD CONSTRAINT ck_whatsapp_customer CHECK (type IN ('TEST', 'QUOTATION') OR customer_id IS NOT NULL);

-- "Was a bill sent for this?" (bill cancelled, My Deliveries status, quotation).
CREATE INDEX ix_whatsapp_message_source ON whatsapp_message (source_type, source_id);

-- Settings: Left at door → Delivery bill, Payment reminder → Due soon + Overdue; reminders daily (3 days before / after).
UPDATE app_setting
SET value = (value - 'leftAtDoor' - 'paymentReminder' - 'reminderDay')
            || jsonb_build_object(
                   'welcome', true,
                   'quotation', true,
                   'deliveryBill', coalesce(value -> 'leftAtDoor', 'true'::jsonb),
                   'billCancelled', true,
                   'paymentReceipt', true,
                   'monthlyInvoice', coalesce(value -> 'monthlyInvoice', 'true'::jsonb),
                   'dueSoon', coalesce(value -> 'paymentReminder', 'true'::jsonb),
                   'overdue', coalesce(value -> 'paymentReminder', 'true'::jsonb),
                   'daysBefore', 3,
                   'daysAfter', 3)
WHERE key = 'whatsapp';
