package lk.thuhina.water.billing.service;

/** Published (inside the payment transaction) for a customer payment apart from a bill. M13 sends the payment receipt from it. */
public record PaymentReceived(long paymentId, long customerId) {
}
