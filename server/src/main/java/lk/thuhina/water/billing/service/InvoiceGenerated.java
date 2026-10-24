package lk.thuhina.water.billing.service;

/**
 * Published (inside the generating transaction) for every monthly invoice created. M13 queues the WhatsApp invoice
 * message from it (design 9.2 outbox: in the same transaction, only when switched on and the customer has opted in).
 */
public record InvoiceGenerated(long invoiceId, long customerId, String period) {
}
