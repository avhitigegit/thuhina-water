package lk.thuhina.water.production.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lk.thuhina.water.sales.model.PayMethod;

/** Filling Factory (design 6 Production, 7.2): dispatches, batches, returns, factory balances and payments. */
public final class ProductionDtos {

    private ProductionDtos() {
    }

    public record FactoryLink(long id, String code, String name) {
    }

    // ------------------------------------------------------------------ dispatch (FR-23)

    /** Empties of one bottle type sent; qty 0 = not sent. */
    public record DispatchLine(String bottleTypeCode, Integer qty) {
    }

    /** One dispatch for every bottle type going in this load. */
    public record DispatchRequest(
            Long factoryId,
            @NotNull(message = "Enter the date.") LocalDate date,
            @Size(max = 20, message = "Vehicle: at most 20 characters.") String vehicleNo,
            @Size(max = 200, message = "Note: at most 200 characters.") String note,
            List<DispatchLine> lines) {
    }

    public record ReturnView(long id, String returnNo, LocalDate date, int filled, int rejected,
                             @Schema(nullable = true) String rejectReason, BigDecimal cost, @Schema(nullable = true) String note,
                             String createdByName) {
    }

    /**
     * A batch with what came back: {@code atFactory} = sent − filled − rejected; {@code cost} = Σ returns (filled × charge);
     * {@code status} AT_FACTORY / PARTLY_RETURNED / RETURNED.
     */
    public record BatchView(
            long id,
            String batchNo,
            long dispatchId,
            String dispatchNo,
            LocalDate date,
            FactoryLink factory,
            String bottleTypeCode,
            String bottleLabel,
            String bottleName,
            int qtySent,
            int filled,
            int rejected,
            int atFactory,
            BigDecimal charge,
            BigDecimal cost,
            String status,
            String statusLabel,
            @Schema(nullable = true) String vehicleNo,
            @Schema(nullable = true) String note,
            List<ReturnView> returns) {
    }

    /** A dispatch with its batches – shown after saving and printed as the dispatch note. */
    public record DispatchView(
            long id,
            String dispatchNo,
            LocalDate date,
            FactoryLink factory,
            @Schema(nullable = true) String factoryAddress,
            @Schema(nullable = true) String factoryContact,
            @Schema(nullable = true) String factoryPhone,
            @Schema(nullable = true) String vehicleNo,
            @Schema(nullable = true) String note,
            String createdByName,
            int totalBottles,
            List<BatchView> batches) {
    }

    // ------------------------------------------------------------------ return (FR-24, FR-26)

    public record ReturnRequest(
            @NotNull(message = "Enter the date.") LocalDate date,
            Integer filled,
            Integer rejected,
            @Size(max = 200, message = "Reason: at most 200 characters.") String rejectReason,
            @Size(max = 200, message = "Note: at most 200 characters.") String note) {
    }

    /** The saved return, the batch after it and the damage record of the rejects (null when none). */
    public record ReturnResult(ReturnView saved, BatchView batch, @Schema(nullable = true) String damageNo) {
    }

    // ------------------------------------------------------------------ KPIs and balances (FR-25, FR-26)

    public record FactoryBottleStock(String bottleTypeCode, String label, String name, int empty, int atFactory) {
    }

    public record FactoryOwed(long id, String code, String name, int atFactory, BigDecimal owed, boolean active) {
    }

    /** Page KPIs: empty and at factory per active bottle type, bottles at and money owed to each factory. */
    public record ProductionSummary(List<FactoryBottleStock> bottles, List<FactoryOwed> factories) {
    }

    /** Filling cost of one factory in one month (by return date): status PAID / PART_PAID / UNPAID. */
    public record FactoryMonth(long factoryId, String factoryName, String period, String periodLabel, int filled, int rejected,
                               BigDecimal cost, BigDecimal paid, BigDecimal due, String status, String statusLabel) {
    }

    public record FactoryPaymentRequest(
            Long factoryId,
            String period,
            @NotNull(message = "Enter the date.") LocalDate date,
            BigDecimal amount,
            PayMethod method,
            @Size(max = 100, message = "Reference: at most 100 characters.") String reference,
            @Size(max = 200, message = "Note: at most 200 characters.") String note) {
    }

    public record FactoryPaymentRow(long id, String fpNo, LocalDate date, FactoryLink factory, String period, String periodLabel,
                                    String method, String methodLabel, @Schema(nullable = true) String reference, BigDecimal amount,
                                    @Schema(nullable = true) String note, String createdByName) {
    }
}
