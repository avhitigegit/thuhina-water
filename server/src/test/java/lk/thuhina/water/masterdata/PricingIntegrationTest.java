package lk.thuhina.water.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.masterdata.service.PricingService;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/** Standard prices and deposits: dated entries, scheduled changes, history, validation (M02 S5, S8). */
class PricingIntegrationTest extends MasterDataTestSupport {

    @Autowired
    PricingService pricing;

    private String bottle;
    private long type;

    @BeforeEach
    void data() throws Exception {
        bottle = newBottle();
        type = newCustomerType();
    }

    /** Past entries are written directly (the API only accepts today or later). */
    private void pastEntry(String kind, Long typeId, int price, LocalDate from) {
        jdbc.update("insert into price_entry (kind, bottle_type_code, customer_type_id, price, effective_from, reason, created_by) "
                + "values (?, ?, ?, ?, ?, 'test', 'test')", kind, bottle, typeId, price, from);
    }

    private Map<String, Object> change(String kind, Long typeId, Object price, LocalDate from, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind);
        m.put("bottleTypeCode", bottle);
        m.put("customerTypeId", typeId);
        m.put("price", price);
        m.put("effectiveFrom", from == null ? null : from.toString());
        m.put("reason", reason);
        return m;
    }

    @Test
    void priceLookupAtTheDateBoundaries() {
        LocalDate jan = LocalDate.of(2026, 1, 1);
        LocalDate jul = LocalDate.of(2026, 7, 1);
        pastEntry("WATER", type, 160, jan);
        pastEntry("WATER", type, 175, jul);
        pastEntry("DEPOSIT", null, 600, jan);

        assertThat(pricing.standardPrice(type, bottle, jan.minusDays(1))).isEmpty();
        assertThat(pricing.standardPrice(type, bottle, jul.minusDays(1))).contains(new BigDecimal("160.00"));
        assertThat(pricing.standardPrice(type, bottle, jul)).contains(new BigDecimal("175.00"));
        assertThat(pricing.standardPrice(type, bottle, jul.plusDays(1))).contains(new BigDecimal("175.00"));
        assertThat(pricing.deposit(bottle, jul)).contains(new BigDecimal("600.00"));
        assertThat(pricing.waterPrice(type, bottle, jul)).hasValueSatisfying(w -> {
            assertThat(w.price()).isEqualByComparingTo("175");
            assertThat(w.agreed()).isFalse();
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void aChangeFromTomorrowIsScheduledAndTodaysPriceStays() throws Exception {
        LocalDate today = today();
        pastEntry("WATER", type, 300, today.minusDays(30));
        pastEntry("WATER", type, 280, today.minusDays(60));

        mvc.perform(json(post("/prices"), admin, change("WATER", type, 325, today.plusDays(1), " Factory charge increase ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.reason").value("Factory charge increase"));

        // Today's price is unchanged; the change shows under it.
        assertThat(pricing.standardPrice(type, bottle, today)).contains(new BigDecimal("300.00"));
        assertThat(pricing.standardPrice(type, bottle, today.plusDays(1))).contains(new BigDecimal("325.00"));
        MvcResult r = mvc.perform(get("/prices/current").cookie(admin)).andExpect(status().isOk()).andReturn();
        Map<String, Object> cell = ((List<Map<String, Object>>) read(r).get("water")).stream()
                .filter(c -> bottle.equals(c.get("bottleTypeCode")) && ((Number) c.get("customerTypeId")).longValue() == type)
                .findFirst().orElseThrow();
        assertThat((Map<String, Object>) cell.get("current")).containsEntry("price", 300.0);
        assertThat((Map<String, Object>) cell.get("next")).containsEntry("price", 325.0)
                .containsEntry("effectiveFrom", today.plusDays(1).toString());

        // Audit: old → new from date – reason.
        String ref = "WATER / " + bottle + " / " + cell.get("key").toString().split("\\|")[2];
        assertThat(lastAudit("Price", ref).getDetails()).isEqualTo("Rs. 300.00 → Rs. 325.00 from "
                + BusinessDates.format(today.plusDays(1)) + " – Factory charge increase");

        // History: Scheduled, Current, Old for this price.
        MvcResult h = mvc.perform(get("/prices/history").cookie(admin)).andExpect(status().isOk()).andReturn();
        List<Map<String, Object>> mine = readList(h).stream()
                .filter(e -> bottle.equals(e.get("bottleTypeCode")) && "WATER".equals(e.get("kind"))).toList();
        assertThat(mine).extracting(e -> e.get("price") + " " + e.get("status"))
                .containsExactly("325.0 SCHEDULED", "300.0 CURRENT", "280.0 OLD");
        assertThat(mine.get(0).get("label").toString()).startsWith("Water ").contains(" / Type ");
    }

    @Test
    void aChangeFromTodayIsCurrentAtOnce() throws Exception {
        pastEntry("DEPOSIT", null, 900, today().minusDays(10));
        mvc.perform(json(post("/prices"), admin, change("DEPOSIT", null, 950.5, today(), "Bottle cost up")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CURRENT"))
                .andExpect(jsonPath("$.label").value(org.hamcrest.Matchers.startsWith("Deposit ")));
        assertThat(pricing.deposit(bottle, today())).contains(new BigDecimal("950.50"));
    }

    @Test
    void validationMessages() throws Exception {
        LocalDate tomorrow = today().plusDays(1);
        mvc.perform(json(post("/prices"), admin, change("WATER", type, 0, tomorrow, "x")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.price").value("Enter a price greater than zero."));
        mvc.perform(json(post("/prices"), admin, change("WATER", type, 100, null, "x")))
                .andExpect(jsonPath("$.details.fields.effectiveFrom").value("Enter the effective date."));
        mvc.perform(json(post("/prices"), admin, change("WATER", type, 100, tomorrow, " ")))
                .andExpect(jsonPath("$.details.fields.reason").value("Enter the reason for the change."));
        mvc.perform(json(post("/prices"), admin, change("WATER", type, 100, today().minusDays(1), "x")))
                .andExpect(jsonPath("$.details.fields.effectiveFrom").value("The effective date cannot be before today."));
        mvc.perform(json(post("/prices"), admin, change("WATER", null, 100, tomorrow, "x")))
                .andExpect(jsonPath("$.details.fields.customerTypeId").value("Choose the customer type."));
        mvc.perform(json(post("/prices"), admin, change("DEPOSIT", type, 100, tomorrow, "x")))
                .andExpect(jsonPath("$.details.fields.customerTypeId").value("A deposit is the same for every customer type."));
        mvc.perform(json(post("/prices"), admin, change("WATER", type, 100.555, tomorrow, "x")))
                .andExpect(jsonPath("$.details.fields.price").value("Enter the price in rupees and cents (at most 2 decimals)."));

        mvc.perform(json(post("/prices"), admin, change("WATER", type, 100, tomorrow, "first"))).andExpect(status().isCreated());
        mvc.perform(json(post("/prices"), admin, change("WATER", type, 110, tomorrow, "second")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.effectiveFrom").value("There is already a price from "
                        + BusinessDates.format(tomorrow) + " for this item. Choose another date."));

        mvc.perform(json(put("/bottle-types/" + bottle), admin, Map.of("name", "Off", "active", false))).andExpect(status().isOk());
        mvc.perform(json(post("/prices"), admin, change("DEPOSIT", null, 100, tomorrow, "x")))
                .andExpect(jsonPath("$.details.fields.bottleTypeCode").value("Choose an active bottle type."));
    }

    @Test
    @SuppressWarnings("unchecked")
    void confirmedPricesAreMarkedOthersAreExamples() throws Exception {
        pastEntry("DEPOSIT", null, 500, today().minusDays(1));
        pastEntry("WATER", type, 200, today().minusDays(1));
        String depositKey = "DEPOSIT|" + bottle;
        mvc.perform(json(put("/settings/prices"), admin, Map.of("confirmed", List.of("DEPOSIT|B20", depositKey))))
                .andExpect(status().isOk());
        try {
            MvcResult r = mvc.perform(get("/prices/current").cookie(admin)).andReturn();
            Map<String, Object> body = read(r);
            Map<String, Object> deposit = ((List<Map<String, Object>>) body.get("deposits")).stream()
                    .filter(c -> bottle.equals(c.get("bottleTypeCode"))).findFirst().orElseThrow();
            assertThat(deposit).containsEntry("key", depositKey).containsEntry("confirmed", true);
            assertThat(((List<Map<String, Object>>) body.get("water")).stream()
                    .filter(c -> bottle.equals(c.get("bottleTypeCode")))).allMatch(c -> Boolean.FALSE.equals(c.get("confirmed")));
        } finally {
            mvc.perform(json(put("/settings/prices"), admin, Map.of("confirmed", List.of("DEPOSIT|B20"))));
        }
        mvc.perform(json(put("/settings/prices"), admin, Map.of("confirmed", "DEPOSIT|B20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.confirmed").value("Must be a list of texts."));
    }

    @Test
    void priceEntriesCannotBeChangedOrDeleted() {
        pastEntry("DEPOSIT", null, 700, today().minusDays(5));
        assertThatThrownBy(() -> jdbc.update("update price_entry set price = 1 where bottle_type_code = ?", bottle))
                .hasMessageContaining("insert-only");
        assertThatThrownBy(() -> jdbc.update("delete from price_entry where bottle_type_code = ?", bottle))
                .hasMessageContaining("insert-only");
    }

    @Test
    void accountantAndDeliveryStaffCannotSeeOrChangePrices() throws Exception {
        for (Role role : new Role[] {Role.ACCOUNTANT, Role.DELIVERY_STAFF}) {
            Cookie cookie = login(createUser(role));
            mvc.perform(get("/prices/current").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(get("/prices/history").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(json(post("/prices"), cookie, change("DEPOSIT", null, 100, today().plusDays(1), "x")))
                    .andExpect(status().isForbidden());
        }
    }
}
