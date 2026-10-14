package lk.thuhina.water.reports.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lk.thuhina.water.inventory.dto.StockDtos.LowStockAlert;

/**
 * Dashboard (design 6 Dashboard, BRD section 8): KPIs, low-stock banners, today's deliveries, "needs attention" and the sales of
 * the last 14 days – one call. A link is given only when the user may open the page it points to.
 */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    /** A KPI tile: {@code value} as shown ("Rs. 12,500.00", "6 of 8", "148"); {@code href} null when the role cannot open the page. */
    public record DashKpi(String key, String label, String value, String hint, @Schema(nullable = true) String href, boolean alert) {
    }

    /** A customer due today: usual quantities ("2×20L 1×10L") and ENTERED / MISSED / DUE ("Missed 12/10/2026"). */
    public record DashDelivery(long customerId, String code, String name, String area, String usual, String status, String statusLabel) {
    }

    /** One "needs attention" line: the linked words, then the rest ("PO-2026-0042" + " – goods to receive"). */
    public record AttentionItem(String kind, String linkText, String text, @Schema(nullable = true) String href) {
    }

    public record DaySales(LocalDate date, BigDecimal amount) {
    }

    public record DashboardView(LocalDate today, List<LowStockAlert> lowStock, @Schema(nullable = true) String lowStockHref,
                                List<DashKpi> kpis, List<DashDelivery> deliveries, @Schema(nullable = true) String deliveriesHref,
                                List<AttentionItem> attention, List<DaySales> sales14) {
    }
}
