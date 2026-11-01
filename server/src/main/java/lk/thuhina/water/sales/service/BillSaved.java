package lk.thuhina.water.sales.service;

import java.time.LocalDate;

import lk.thuhina.water.sales.model.SalesKind;

/**
 * Published (inside the posting transaction) when a customer's sales bill is saved – delivery bills from the office or the
 * phone and the other sales (not the registration's first purchase, the opening balance or a reversal). M13 queues the
 * "Delivery bill" WhatsApp message from it (design 9.2 outbox).
 */
public record BillSaved(long txnId, long customerId, LocalDate date, SalesKind kind) {
}
