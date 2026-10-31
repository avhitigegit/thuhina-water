package lk.thuhina.water.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.billing.rules.PaymentRules;
import lk.thuhina.water.sales.SalesTestSupport;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** My Deliveries on the phone (M17): today's bill with bottles and money, money without a delivery, the day, search, access. */
class MobileDeliveryIntegrationTest extends SalesTestSupport {

    private AppUser staffUser;
    private Cookie staff;

    @BeforeEach
    void staff() throws Exception {
        staffUser = createUser(Role.DELIVERY_STAFF);
        staff = login(staffUser);
    }

    private Map<String, Object> bill(long customerId, int filled, int empties, Object paid, String method, String reference) {
        Map<String, Object> m = map("customerId", customerId, "date", today.minusDays(3).toString(),
                "lines", List.of(map("bottleTypeCode", bottle, "filled", filled, "empties", empties)), "paid", paid);
        if (method != null) {
            m.put("payMethod", method);
        }
        if (reference != null) {
            m.put("payReference", reference);
        }
        return m;
    }

    private Map<String, Object> staffGet(String url) throws Exception {
        MvcResult r = mvc.perform(get(url).cookie(staff)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return read(r);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staffSavesTodaysBillWithBottlesAndMoneyAndSeesTheirDay() throws Exception {
        long c = customerHolding("Mobile Home", 4);

        // Bank transfer without its reference: shown by the preview, refused on save.
        Map<String, Object> preview = read(mvc.perform(json(post("/delivery/mobile/bills/preview"), staff,
                bill(c, 2, 2, 700, "BANK_TRANSFER", null))).andExpect(status().isOk()).andReturn());
        assertThat((List<Map<String, Object>>) preview.get("problems")).extracting(p -> p.get("field")).contains("payReference");
        assertThat(new BigDecimal(preview.get("amount").toString())).isEqualByComparingTo("700");
        mvc.perform(json(post("/delivery/mobile/bills"), staff, bill(c, 2, 2, 700, "BANK_TRANSFER", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.payReference").value(PaymentRules.MSG_BANK_REF));

        // Saved for today (the date sent is ignored), no paper bill number needed.
        MvcResult saved = mvc.perform(json(post("/delivery/mobile/bills"), staff, bill(c, 2, 2, 700, "BANK_TRANSFER", "TRF-77"))).andReturn();
        assertThat(saved.getResponse().getStatus()).as(saved.getResponse().getContentAsString()).isEqualTo(201);
        Map<String, Object> txn = (Map<String, Object>) read(saved).get("txn");
        assertThat(txn).containsEntry("date", today.toString()).containsEntry("paperBillNo", null)
                .containsEntry("payMethod", "Bank transfer").containsEntry("payReference", "TRF-77")
                .containsEntry("createdBy", staffUser.getUsername());
        assertThat(bottles(c)).containsExactly(4, 0, 0);
        assertThat(balance(c)).isEqualByComparingTo("0");

        // Money without a delivery: a payment for today.
        MvcResult paid = mvc.perform(json(post("/delivery/mobile/payments"), staff, map("customerId", c, "amount", 500, "method", "CASH")))
                .andReturn();
        assertThat(paid.getResponse().getStatus()).as(paid.getResponse().getContentAsString()).isEqualTo(201);
        assertThat(balance(c)).isEqualByComparingTo("-500");
        mvc.perform(json(post("/delivery/mobile/payments"), staff, map("customerId", c, "amount", 100, "method", "CHEQUE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.reference").value(PaymentRules.MSG_CHEQUE_NO));

        Map<String, Object> day = staffGet("/delivery/mobile/day");
        assertThat(day).containsEntry("date", today.toString()).containsEntry("bills", 1).containsEntry("payments", 1);
        Map<String, Object> money = (Map<String, Object>) day.get("money");
        assertThat(new BigDecimal(money.get("cash").toString())).isEqualByComparingTo("500");
        assertThat(new BigDecimal(money.get("bankTransfer").toString())).isEqualByComparingTo("700");
        assertThat(new BigDecimal(money.get("total").toString())).isEqualByComparingTo("1200");
        assertThat((List<Map<String, Object>>) day.get("bottles")).singleElement()
                .satisfies(b -> assertThat(b).containsEntry("bottleTypeCode", bottle).containsEntry("filled", 2).containsEntry("empties", 2));
        List<Map<String, Object>> entries = (List<Map<String, Object>>) day.get("entries");
        assertThat(entries).extracting(e -> e.get("entry")).containsExactly("PAYMENT", "BILL");
        assertThat(entries.get(1)).containsEntry("reference", "TRF-77").containsEntry("cancelled", false);

        // Another user's day does not show these.
        Cookie other = login(createUser(Role.DELIVERY_STAFF));
        MvcResult otherDay = mvc.perform(get("/delivery/mobile/day").cookie(other)).andExpect(status().isOk()).andReturn();
        assertThat(read(otherDay)).containsEntry("bills", 0).containsEntry("payments", 0);

        // The office reverses the bill: still listed, marked cancelled and left out of the totals.
        mvc.perform(json(post("/sales/txns/" + txn.get("id") + "/reverse"), map("reason", "Wrong customer"))).andExpect(status().isCreated());
        day = staffGet("/delivery/mobile/day");
        assertThat(day).containsEntry("bills", 0);
        assertThat(new BigDecimal(((Map<String, Object>) day.get("money")).get("total").toString())).isEqualByComparingTo("500");
        assertThat((List<?>) day.get("bottles")).isEmpty();
        assertThat(((List<Map<String, Object>>) day.get("entries")).get(1)).containsEntry("cancelled", true);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findsAnotherCustomerAndTodaysListIsOpen() throws Exception {
        String name = "Findme " + System.nanoTime();
        long c = customerHolding(name, 2);
        List<Map<String, Object>> found = json.readValue(mvc.perform(get("/delivery/mobile/customers").param("q", name.toLowerCase())
                .cookie(staff)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), List.class);
        assertThat(found).singleElement().satisfies(f -> {
            assertThat(((Number) f.get("id")).longValue()).isEqualTo(c);
            assertThat((List<Map<String, Object>>) f.get("bottles")).anySatisfy(b -> assertThat(b)
                    .containsEntry("bottleTypeCode", bottle).containsEntry("held", 2));
        });
        mvc.perform(get("/delivery/mobile/customers").param("q", "a").cookie(staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/delivery/due").param("area", String.valueOf(areaId)).cookie(staff)).andExpect(status().isOk());
    }

    @Test
    void onlyDeliveryEntryMayUseIt() throws Exception {
        long c = customerHolding("Mobile Access", 2);
        // Delivery staff still cannot use the office sales and payment screens.
        mvc.perform(json(post("/sales/bills"), staff, map("customerId", c, "date", today.toString(), "paperBillNo", "M" + System.nanoTime(),
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 1, "empties", 1))))).andExpect(status().isForbidden());
        mvc.perform(get("/sales/txns").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(json(post("/payments"), staff, map("customerId", c, "date", today.toString(), "amount", 10, "method", "CASH")))
                .andExpect(status().isForbidden());

        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/delivery/mobile/day").cookie(accountant)).andExpect(status().isForbidden());
        mvc.perform(json(post("/delivery/mobile/bills"), accountant, bill(c, 1, 1, 0, null, null))).andExpect(status().isForbidden());
        mvc.perform(get("/delivery/mobile/day").cookie(admin)).andExpect(status().isOk());
    }
}
