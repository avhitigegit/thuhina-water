package lk.thuhina.water.payments.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** The Payments page (one home for money): Money in and Money out lists. */
public final class MoneyDtos {

    private MoneyDtos() {
    }

    /**
     * One payment. Money in: PAYMENT (a customer payment, RC-…) or BILL (money paid on a bill, B…).
     * Money out: SUPPLIER (SP-…), FACTORY (FP-…) or EXPENSE (EX-…).
     */
    public record MoneyRow(
            String kind,
            String kindLabel,
            long id,
            String ref,
            LocalDate date,
            Instant at,
            @Schema(nullable = true, description = "Customer, supplier or factory id; null for a walk-in or an expense") Long partyId,
            String party,
            String methodLabel,
            @Schema(nullable = true) String reference,
            @Schema(nullable = true) String note,
            BigDecimal amount,
            String enteredBy) {
    }

    /** The rows (newest first, at most {@code limit}) and the count and total of every matching row. */
    public record MoneyList(LocalDate from, LocalDate to, List<MoneyRow> items, long count, BigDecimal total, int limit) {
    }
}
