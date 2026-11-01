package lk.thuhina.water.customers.service;

import java.time.LocalDate;

/** Published (inside the registration transaction) when a customer is registered. M13 sends the welcome message from it. */
public record CustomerRegistered(long customerId, LocalDate registeredOn) {
}
