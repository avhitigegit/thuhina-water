package lk.thuhina.water.sales.service;

/** Published (inside the reversal transaction) when a customer's bill is cancelled. M13 sends "Bill cancelled" from it. */
public record BillReversed(long txnId, long reversalId, long customerId) {
}
