package lk.thuhina.water.delivery.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.sales.model.PayMethod;

/** My Deliveries on the phone (M17): money received without a delivery and the delivery person's day. */
public final class MobileDtos {

    private MobileDtos() {
    }

    /** Money received at the door without a delivery – saved as a payment (receipt) for today. */
    public record MobilePaymentRequest(
            Long customerId,
            BigDecimal amount,
            PayMethod method,
            @Size(max = 100, message = "Reference: at most 100 characters.") String reference,
            @Size(max = 100, message = "Bank: at most 100 characters.") String bank,
            LocalDate chequeDate,
            @Size(max = 200, message = "Note: at most 200 characters.") String note) {
    }

    /** A bill or payment the user saved today, newest first. {@code cancelled} = the office reversed the bill. */
    public record MyEntry(
            @Schema(description = "BILL or PAYMENT") String entry,
            long id,
            String number,
            String kindLabel,
            Instant at,
            @Schema(nullable = true) Long customerId,
            @Schema(nullable = true) String customerCode,
            String customerName,
            int filled,
            int empties,
            @Schema(nullable = true, description = "Bill amount (null for a payment)") BigDecimal amount,
            BigDecimal received,
            @Schema(nullable = true) String method,
            @Schema(nullable = true) String reference,
            boolean cancelled) {
    }

    /** Filled bottles left and empties collected of one bottle type. */
    public record MyBottles(String bottleTypeCode, String label, int filled, int empties) {
    }

    /** Money received today by how it was paid – for handing over the cash and cheques. */
    public record MyMoney(BigDecimal cash, BigDecimal bankTransfer, BigDecimal cheque, BigDecimal total) {
    }

    /** The user's day: counts, bottles, money (cancelled bills left out) and every entry. */
    public record MyDay(LocalDate date, int bills, int payments, List<MyBottles> bottles, MyMoney money, List<MyEntry> entries) {
    }
}
