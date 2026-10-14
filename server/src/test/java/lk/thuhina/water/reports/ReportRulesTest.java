package lk.thuhina.water.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lk.thuhina.water.reports.service.ReportTestAccess;
import lk.thuhina.water.security.Permissions;
import org.junit.jupiter.api.Test;

/** M12 rules: who runs which report, value formats of the exports, Excel sheet names, sale types and damage places. */
class ReportRulesTest {

    @Test
    void adminRunsAllTenTheAccountantFive() {
        Set<String> admin = Set.of(Permissions.REPORTS_VIEW, Permissions.REPORTS_ALL);
        Set<String> accountant = Set.of(Permissions.REPORTS_VIEW);
        List<String> keys = List.of("sales", "purchasing", "production", "stock", "movement", "damage", "outstanding", "profit", "expenses",
                "delivery");
        assertThat(keys.stream().filter(k -> ReportTestAccess.allowed(admin, k))).hasSize(10);
        assertThat(keys.stream().filter(k -> ReportTestAccess.allowed(accountant, k)))
                .containsExactly("sales", "purchasing", "outstanding", "profit", "expenses");
        assertThat(ReportTestAccess.allowed(Set.of(), "sales")).isFalse();
    }

    @Test
    void exportValuesAreShownAsOnTheScreen() {
        assertThat(ReportTestAccess.shown("1350.5", "MONEY")).isEqualTo("1,350.50");
        assertThat(ReportTestAccess.shown("-700.00", "MONEY")).isEqualTo("-700.00");
        assertThat(ReportTestAccess.shown("12500", "NUMBER")).isEqualTo("12,500");
        assertThat(ReportTestAccess.shown("2026-09-30", "DATE")).isEqualTo("30/09/2026");
        // Footers put words in number and date columns.
        assertThat(ReportTestAccess.shown("Total", "DATE")).isEqualTo("Total");
        assertThat(ReportTestAccess.shown("Total", "MONEY")).isEqualTo("Total");
        assertThat(ReportTestAccess.shown("", "MONEY")).isEmpty();
        assertThat(ReportTestAccess.text(new BigDecimal("1350.5"), "MONEY")).isEqualTo("1350.50");
        assertThat(ReportTestAccess.text(LocalDate.of(2026, 9, 30), "DATE")).isEqualTo("2026-09-30");
        assertThat(ReportTestAccess.text(null, "TEXT")).isEmpty();
    }

    @Test
    void excelSheetNamesAreSafeShortAndUnique() {
        Set<String> used = new HashSet<>(Set.of("Summary"));
        assertThat(ReportTestAccess.sheetName("Customers on agreed prices – water billed vs standard price", used)).hasSizeLessThanOrEqualTo(31);
        assertThat(ReportTestAccess.sheetName("By day", used)).isEqualTo("By day");
        assertThat(ReportTestAccess.sheetName("By day", used)).isEqualTo("By day 2");
        assertThat(ReportTestAccess.sheetName("Summary", used)).isEqualTo("Summary 2");
        assertThat(ReportTestAccess.sheetName("Stock at 30/09/2026", used)).doesNotContain("/");
    }

    @Test
    void saleTypesAndDamagePlacesInThePrototypeWords() {
        assertThat(ReportTestAccess.saleType("LEFT_AT_DOOR")).isEqualTo("Exchange");
        assertThat(ReportTestAccess.saleType("CUSTOMER_DAMAGE")).isEqualTo("Bottle replacement");
        assertThat(ReportTestAccess.saleType("OWED_SETTLEMENT")).isEqualTo("Bottles owed (deposit)");
        assertThat(ReportTestAccess.location("AT_FACTORY")).isEqualTo("At factory");
        assertThat(ReportTestAccess.location("EMPTY")).isEqualTo("Empty in store");
        assertThat(ReportTestAccess.location(null)).isEqualTo("At the customer");
    }
}
