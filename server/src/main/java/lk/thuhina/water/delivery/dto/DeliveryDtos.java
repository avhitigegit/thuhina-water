package lk.thuhina.water.delivery.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Due list (FR-42, design 5.4): the customers to deliver on a date – Enter Bills (M07) and the delivery lists (M08). */
public final class DeliveryDtos {

    private DeliveryDtos() {
    }

    /**
     * One bottle type of a due customer: usual quantity, the bottles they have now, the water price for the date (agreed or
     * standard), and the grid's pre-filled quantities – filled = usual; empties = usual + empties left at the door last trip.
     */
    public record DueBottle(
            String bottleTypeCode,
            String label,
            int usual,
            int held,
            int toCollect,
            int owed,
            int filled,
            int empties,
            @Schema(nullable = true) BigDecimal price,
            boolean agreedPrice,
            @Schema(nullable = true) BigDecimal standardPrice) {
    }

    /** A bill already entered for the customer on the date (not reversed). */
    public record EnteredBill(long id, String billNo, @Schema(nullable = true) String paperBillNo, String kind, String kindLabel) {
    }

    /**
     * A customer due on the date. {@code overdue} = missed an earlier delivery (only listed for today); {@code entered} = a
     * delivery bill (Exchange, Left at door, First purchase, Extra order) is already saved for the date; {@code noBottles}
     * = holds no bottles yet (use First purchase); {@code listed} = goes into the Enter Bills grid (not entered, has bottles).
     */
    public record DueCustomer(
            long id,
            String code,
            String name,
            String phone,
            long areaId,
            String areaName,
            String payType,
            String payTypeLabel,
            BigDecimal creditLimit,
            BigDecimal balance,
            int cycleWeeks,
            LocalDate nextDeliveryDate,
            @Schema(description = "Next delivery after a bill on this date (FR-46)") LocalDate nextAfterBill,
            boolean due,
            boolean overdue,
            int daysLate,
            boolean entered,
            boolean noBottles,
            boolean listed,
            List<EnteredBill> enteredBills,
            List<DueBottle> bottles) {
    }

    /** Counts for the info line: "8 customer(s) due – 6 listed below · 2 already entered · 1 new customer(s) without bottles". */
    public record DueCounts(int due, int listed, int entered, int noBottles, int overdue) {
    }

    /** The due list for a date, ordered by area then name. */
    public record DueList(LocalDate date, String dayName, boolean today, DueCounts counts, List<DueCustomer> items) {
    }
}
