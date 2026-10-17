package lk.thuhina.water.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** Agreed customer prices (M05 S10, S11). Standard price of the test type is Rs. 300. */
class CustomerPriceIntegrationTest extends CustomerTestSupport {

    private static final LocalDate JUL = LocalDate.of(2026, 7, 1);

    @Autowired
    PricingService pricing;

    private long customerId;

    @BeforeEach
    void customer() throws Exception {
        customerId = register(customer("Ceyknit " + System.nanoTime()));
    }

    private Map<String, Object> price(Object price, LocalDate from, LocalDate until, String reason) {
        Map<String, Object> m = new HashMap<>();
        m.put("bottleTypeCode", bottle);
        m.put("price", price);
        m.put("from", from.toString());
        m.put("until", until == null ? null : until.toString());
        m.put("reason", reason);
        return m;
    }

    private BigDecimal priceOn(LocalDate d) {
        return pricing.waterPrice(customerId, bottle, d).orElseThrow().price();
    }

    private List<String> states() throws Exception {
        return readList(mvc.perform(get("/customers/" + customerId + "/prices").cookie(admin)).andReturn()).stream()
                .map(r -> r.get("price") + " " + r.get("state")).toList();
    }

    @Test
    void anAgreedPriceFromJulyIsUsedFromJulyAndAuditedWithTheStandard() throws Exception {
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(270, JUL, null, "Volume price – about 43 bottles a month")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("IN_FORCE"))
                .andExpect(jsonPath("$.standardAtSet").value(300.0))
                .andExpect(jsonPath("$.cpNo").value(org.hamcrest.Matchers.matchesPattern("CP\\d{4,}")));
        assertThat(priceOn(JUL.minusDays(1))).isEqualByComparingTo("300");
        assertThat(priceOn(JUL)).isEqualByComparingTo("270");
        assertThat(pricing.waterPrice(customerId, bottle, today).orElseThrow())
                .satisfies(w -> assertThat(w.agreed()).isTrue()).satisfies(w -> assertThat(w.standard()).isEqualByComparingTo("300"));
        String code = (String) read(mvc.perform(get("/customers/" + customerId).cookie(admin)).andReturn()).get("code");
        String label = bottle.substring(1).replace('_', '.') + "L";
        assertThat(lastAudit("Customer price", code + " " + label).getDetails())
                .isEqualTo("Agreed price Rs. 300.00 → Rs. 270.00 from 01/07/2026 (standard Rs. 300.00) – Volume price – about 43 bottles a month");
        mvc.perform(get("/pricing/water?customerId=" + customerId + "&bottleType=" + bottle + "&date=2026-06-30").cookie(admin))
                .andExpect(jsonPath("$.price").value(300.0)).andExpect(jsonPath("$.agreed").value(false));
    }

    @Test
    void aNewPriceClosesTheOldOneAndAScheduledOneStartsOnItsDate() throws Exception {
        LocalDate next = today.plusDays(10);
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(280, JUL, null, "Opening"))).andExpect(status().isCreated());
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(260, next, null, "From next month")))
                .andExpect(jsonPath("$.state").value("SCHEDULED"));
        assertThat(priceOn(today)).isEqualByComparingTo("280");
        assertThat(priceOn(next.minusDays(1))).isEqualByComparingTo("280");
        assertThat(priceOn(next)).isEqualByComparingTo("260");
        assertThat(states()).containsExactly("260.0 SCHEDULED", "280.0 IN_FORCE");
        mvc.perform(get("/customers/" + customerId).cookie(admin))
                .andExpect(jsonPath("$.prices[?(@.bottleTypeCode == '" + bottle + "')].next.price").value(org.hamcrest.Matchers.contains(260.0)));

        // A new price starting before the scheduled one cancels it.
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(275, today.plusDays(1), null, "Changed mind")))
                .andExpect(status().isCreated());
        assertThat(states()).containsExactly("260.0 CANCELLED", "275.0 SCHEDULED", "280.0 IN_FORCE");
    }

    @Test
    void endedReplacedAndBackToStandard() throws Exception {
        LocalDate aug = LocalDate.of(2026, 8, 1);
        // Two months only → ended.
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(320, JUL, LocalDate.of(2026, 7, 31), "Opening offer")))
                .andExpect(jsonPath("$.state").value("ENDED"));
        // From = until (one day only).
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(250, aug, aug, "One-day promotion"))).andExpect(status().isCreated());
        assertThat(priceOn(aug)).isEqualByComparingTo("250");
        assertThat(priceOn(aug.plusDays(1))).isEqualByComparingTo("300");
        // A running price replaced by a later one.
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(290, LocalDate.of(2026, 9, 1), null, "September"))).andExpect(status().isCreated());
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(285, LocalDate.of(2026, 9, 15), null, "Mid September"))).andExpect(status().isCreated());
        // 320 ended on 31/07 and 250 started on 01/08 – so 320 counts as replaced; 250 just ended.
        assertThat(states()).containsExactly("285.0 IN_FORCE", "290.0 REPLACED", "250.0 ENDED", "320.0 REPLACED");

        // Back to standard from tomorrow.
        mvc.perform(json(post("/customers/" + customerId + "/prices/end"),
                        Map.of("bottleTypeCode", bottle, "from", today.plusDays(1).toString(), "reason", "Contract ended")))
                .andExpect(status().isNoContent());
        assertThat(priceOn(today)).isEqualByComparingTo("285");
        assertThat(priceOn(today.plusDays(1))).isEqualByComparingTo("300");
        // Customer details with ?date= show the water price in force on that date (Enter Bills back-dated rows, B-05).
        String path = "$.prices[?(@.bottleTypeCode == '" + bottle + "')].price";
        mvc.perform(get("/customers/" + customerId).cookie(admin)).andExpect(jsonPath(path).value(org.hamcrest.Matchers.contains(285.0)));
        mvc.perform(get("/customers/" + customerId).param("date", "2026-08-01").cookie(admin))
                .andExpect(jsonPath(path).value(org.hamcrest.Matchers.contains(250.0)));
        mvc.perform(get("/customers/" + customerId).param("date", "2026-09-10").cookie(admin))
                .andExpect(jsonPath(path).value(org.hamcrest.Matchers.contains(290.0)));
        mvc.perform(json(post("/customers/" + customerId + "/prices/end"),
                        Map.of("bottleTypeCode", bottle, "from", today.plusDays(5).toString(), "reason", "again")))
                .andExpect(jsonPath("$.code").value("NO_AGREED_PRICE"));
    }

    @Test
    void validationMessages() throws Exception {
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(0, JUL, null, "x")))
                .andExpect(jsonPath("$.details.fields.price").value("Enter the agreed price per bottle."));
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(270, JUL, JUL.minusDays(1), "x")))
                .andExpect(jsonPath("$.details.fields.until").value("The end date cannot be before the start date."));
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(270, JUL, null, " ")))
                .andExpect(jsonPath("$.details.fields.reason").value("Enter the reason for the agreed price (e.g. about 3,500 bottles a month)."));
    }

    @Test
    void registeringWithAnAgreedPriceSetsItFromTheRegistrationDate() throws Exception {
        Map<String, Object> req = customer("Factory " + System.nanoTime());
        req.put("agreedPrices", Map.of(bottle, 260));
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(jsonPath("$.details.fields.agreedReason").value("Enter the reason for the agreed price (e.g. about 3,500 bottles a month)."));
        req.put("agreedReason", "Quotation accepted");
        long id = register(req);
        assertThat(pricing.waterPrice(id, bottle, today).orElseThrow().price()).isEqualByComparingTo("260");
        // The same as the standard price → no agreement is stored.
        Map<String, Object> same = customer("Standard " + System.nanoTime());
        same.put("agreedPrices", Map.of(bottle, 300));
        long id2 = register(same);
        assertThat(pricing.waterPrice(id2, bottle, today).orElseThrow().agreed()).isFalse();
    }

    @Test
    void accountantSeesAgreedPricesButCannotChangeThem() throws Exception {
        mvc.perform(json(post("/customers/" + customerId + "/prices"), price(270, JUL, null, "Volume"))).andExpect(status().isCreated());
        Cookie acc = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/customers/" + customerId + "/prices").cookie(acc)).andExpect(status().isOk());
        mvc.perform(get("/customer-prices").cookie(acc)).andExpect(status().isOk());
        mvc.perform(json(post("/customers/" + customerId + "/prices"), acc, price(260, JUL, null, "x"))).andExpect(status().isForbidden());
        mvc.perform(json(post("/customers/" + customerId + "/prices/end"), acc,
                Map.of("bottleTypeCode", bottle, "from", JUL.toString(), "reason", "x"))).andExpect(status().isForbidden());

        // The all-customers list shows it with the customer and the % against today's standard.
        List<Map<String, Object>> all = readList(mvc.perform(get("/customer-prices").cookie(admin)).andReturn());
        assertThat(all).anySatisfy(r -> assertThat(r).containsEntry("customerId", (int) customerId)
                .containsEntry("price", 270.0).containsEntry("standardNow", 300.0).containsEntry("state", "IN_FORCE"));
        assertThat(BusinessDates.format(JUL)).isEqualTo("01/07/2026");
    }
}
