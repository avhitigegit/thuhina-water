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
            String address,
            @Schema(nullable = true) String notes,
            long areaId,
            String areaName,
            String payType,
            String payTypeLabel,
            BigDecimal creditLimit,
            BigDecimal balance,
            @Schema(description = "1 = Monday … 7 = Sunday") int deliveryDay,
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

    /** A quantity of one bottle type, e.g. 10 × 20L. */
    public record BottleQty(String bottleTypeCode, String label, int qty) {
    }

    /**
     * Totals of the listed customers for the Daily Delivery List KPIs (FR-43): filled bottles to load (usual quantities),
     * empties to collect (left at the door), bottles owed and money to collect (positive balances).
     */
    public record DueTotals(List<BottleQty> load, int toCollect, int owed, BigDecimal moneyToCollect) {
    }

    /** The due list for a date, ordered by area then name. */
    public record DueList(LocalDate date, String dayName, boolean today, DueCounts counts, DueTotals totals, List<DueCustomer> items) {
    }

    // ------------------------------------------------------------------ Delivery Planning (FR-58)

    /** The last delivery before the planned date (design 5.4): date, filled bottles given, left at the door or not. */
    public record LastDelivery(LocalDate date, List<BottleQty> filled, boolean leftAtDoor) {
    }

    /**
     * A customer on the planning page. {@code thisDate} = usual quantities when due on the date (empty when not due);
     * {@code next} / {@code weekAfter} = the next two deliveries after the date; {@code status} = ENTERED / MISSED / DUE /
     * FIRST_DELIVERY (due, holds no bottles) / NOT_DUE; {@code overLimit} = Credit / Monthly bill balance over the limit.
     */
    public record PlanRow(
            long id,
            String code,
            String name,
            String address,
            String phone,
            String payType,
            String payTypeLabel,
            int deliveryDay,
            String dayName,
            int cycleWeeks,
            LocalDate nextDeliveryDate,
            @Schema(nullable = true) LastDelivery last,
            boolean due,
            boolean overdue,
            boolean entered,
            List<EnteredBill> enteredBills,
            List<BottleQty> thisDate,
            List<BottleQty> held,
            LocalDate next,
            LocalDate weekAfter,
            int toCollect,
            int owed,
            BigDecimal balance,
            String status,
            String statusLabel,
            boolean overLimit) {
    }

    /** A route (area): "Kottawa – 4 due · load 10 × 20L" – the load counts customers due and not yet entered. */
    public record PlanRoute(long areaId, String areaName, int due, int toLoad, List<BottleQty> load, int toCollect, List<PlanRow> rows) {
    }

    /**
     * Ready check for one bottle type (FR-45): {@code needed} on the date vs filled in store now minus {@code neededBefore}
     * (today … date − 1, not entered). {@code shortBy} > 0 = not enough; {@code factoryCovers} = bottles at the factory
     * would cover it if they come back in time.
     */
    public record ReadyCheck(String bottleTypeCode, String label, String name, int needed, int neededBefore, int filledInStore,
                             int atFactory, int shortBy, boolean ready, boolean factoryCovers) {
    }

    /** KPIs of the customers due on the date (FR-58). {@code needed} counts only customers not yet entered. */
    public record PlanKpis(int due, int entered, int missed, List<BottleQty> needed, int toCollect, int owed, BigDecimal moneyToCollect) {
    }

    /** Delivery Planning for a date: KPIs, ready check per bottle type and the customers grouped by route. */
    public record Planning(LocalDate date, String dayName, boolean today, String show, int customers, PlanKpis kpis,
                           List<ReadyCheck> ready, List<PlanRoute> routes) {
    }

    // ------------------------------------------------------------------ Upcoming bottle requirement (FR-45)

    /** One day: customers due and not entered who take this bottle type, bottles needed, running total, what is left. */
    public record RequirementDay(LocalDate date, String dayName, int customers, int needed, int runningTotal, int filledLeft,
                                 int withFactory) {
    }

    /**
     * Filled bottles needed for the coming days. {@code runsOutOn} = first day when filled in store plus at the factory
     * is not enough (null = enough for the whole period).
     */
    public record Requirement(String bottleTypeCode, String label, String name, int filledInStore, int atFactory, int emptyInStore,
                              int days, List<RequirementDay> rows, @Schema(nullable = true) LocalDate runsOutOn) {
    }
}
