package lk.thuhina.water.sales.service;

import java.time.LocalDate;

/**
 * Published (inside the posting transaction) when a Left at door bill is saved, after the next delivery date has moved.
 * M13 queues the "Left at door" WhatsApp notice from it (design 9.2 outbox).
 */
public record LeftAtDoorBillSaved(long txnId, long customerId, LocalDate date) {
}
