-- UAT finding 4 (25/11/2026): a price entered by mistake for a future date can be cancelled before it starts.
-- price_entry stays insert-only: an entry may be deleted only when the application turns on app.price_cancel for the
-- transaction (PricingService.cancelScheduled checks that the price has not started yet and writes the audit log).
CREATE OR REPLACE FUNCTION price_entry_insert_only() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('app.price_cancel', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'price_entry is insert-only – add a new entry with an effective date instead';
END;
$$ LANGUAGE plpgsql;
