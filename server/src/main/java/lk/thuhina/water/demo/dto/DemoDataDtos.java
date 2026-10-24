package lk.thuhina.water.demo.dto;

import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Demo-data generator (M12 S4, local / UAT only). */
public final class DemoDataDtos {

    private DemoDataDtos() {
    }

    /** The suggested range (two months up to yesterday) and the last delivery bill already in the data. */
    public record DemoDataStatus(LocalDate suggestedFrom, LocalDate suggestedTo, @Schema(nullable = true) LocalDate lastDeliveryBill) {
    }

    /** Days to fill: default two months up to yesterday. */
    public record DemoDataRequest(LocalDate from, LocalDate to) {
    }

    /** What was made, and the first problems (a bill that could not be saved is skipped and listed). */
    public record DemoDataResult(LocalDate from, LocalDate to, int bills, int leftAtDoor, int shortages, int extraOrders, int productSales,
                                 int dispatches, int returns, int payments, int invoices, int skipped, List<String> problems,
                                 String message) {
    }
}
