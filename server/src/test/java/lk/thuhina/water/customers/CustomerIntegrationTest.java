package lk.thuhina.water.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.customers.service.CustomerService;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.ledger.model.CustomerField;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.ledger.model.Posting;
import lk.thuhina.water.ledger.model.SourceType;
import lk.thuhina.water.ledger.service.LedgerService;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

/** Customers (M05 S3, S5, S6, S7, S9): register / edit rules, duplicate phone, lists, lookup, status, access. */
class CustomerIntegrationTest extends CustomerTestSupport {

    @Autowired
    LedgerService ledger;
    @Autowired
    TransactionTemplate tx;

    @Test
    @SuppressWarnings("unchecked")
    void registersACustomerWithCodeBalancesAndAccount() throws Exception {
        Map<String, Object> req = customer("Saman Perera");
        MvcResult r = mvc.perform(json(post("/customers/register"), req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.code").value(matchesPattern("C\\d{4,}")))
                .andExpect(jsonPath("$.customer.status").value("ACTIVE"))
                .andExpect(jsonPath("$.customer.registeredOn").value(today.toString()))
                .andExpect(jsonPath("$.customer.outstanding").value(0.0))
                .andReturn();
        Map<String, Object> c = (Map<String, Object>) read(r).get("customer");
        assertThat((List<Map<String, Object>>) c.get("bottles")).anySatisfy(b ->
                assertThat(b).containsEntry("bottleTypeCode", bottle).containsEntry("held", 0));
        assertThat((List<Map<String, Object>>) c.get("prices")).anySatisfy(p ->
                assertThat(p).containsEntry("bottleTypeCode", bottle).containsEntry("price", 300.0).containsEntry("agreed", false));
        assertThat(lastAudit("Customer", (String) c.get("code")).getDetails()).startsWith("Saman Perera – Type ");
    }

    @Test
    void requiredFieldsPhoneAndDeliveryDayMessages() throws Exception {
        Map<String, Object> req = customer(" ");
        req.remove("areaId");
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Name, address, phone, customer type and area are required."))
                .andExpect(jsonPath("$.details.fields.areaId").value("Name, address, phone, customer type and area are required."));

        req = customer("Bad Phone");
        req.put("phone", "77 123 4567");
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(jsonPath("$.details.fields.phone").value("Phone must be a Sri Lankan number, e.g. 077 123 4567 or 011 285 4471."));

        // Next delivery not on the delivery day.
        LocalDate monday = ScheduleRules.firstDelivery(today, DayOfWeek.MONDAY);
        req = customer("Wrong Day");
        req.put("nextDeliveryDate", monday.plusDays(1).toString());
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(jsonPath("$.details.fields.nextDeliveryDate").value("Next delivery date "
                        + BusinessDates.format(monday.plusDays(1)) + " is a Tuesday, but the delivery day is Monday."));

        req = customer("Future");
        req.put("registeredOn", today.plusDays(1).toString());
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(jsonPath("$.details.fields.registeredOn").value("Registration date cannot be in the future."));

        // At least one bottle type needs a usual quantity (N-09).
        req = customer("No usual");
        req.put("usualQty", Map.of(bottle, 0));
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.usualQty").value(CustomerService.MSG_USUAL));
    }

    @Test
    void creditAndMonthlyBillNeedALimitAndTermsFromTheList() throws Exception {
        Map<String, Object> req = customer("Credit Shop");
        req.put("payType", "CREDIT");
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(jsonPath("$.details.fields.creditLimit").value("Credit and Monthly bill customers need a credit limit."));
        req.put("creditLimit", 25000);
        req.put("termsDays", 21);
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(jsonPath("$.details.fields.termsDays").value("Choose the payment terms from the list."));
        req.put("termsDays", 30);
        req.put("payType", "MONTHLY_BILL");
        mvc.perform(json(post("/customers/register"), req))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.creditLimit").value(25000.0))
                .andExpect(jsonPath("$.customer.termsDays").value(30));

        // Cash keeps no limit or terms even if sent.
        Map<String, Object> cash = customer("Cash Home");
        cash.put("creditLimit", 5000);
        cash.put("termsDays", 30);
        mvc.perform(json(post("/customers/register"), cash))
                .andExpect(jsonPath("$.customer.creditLimit").value(0.0))
                .andExpect(jsonPath("$.customer.termsDays").value(0));
    }

    @Test
    void duplicatePhoneWarnsAndCanBeSavedAnyway() throws Exception {
        Map<String, Object> first = customer("First Phone");
        register(first);
        Map<String, Object> second = customer("Second Phone");
        second.put("phone", ((String) first.get("phone")).replace(" ", ""));
        mvc.perform(json(post("/customers/register"), second))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("DUP_PHONE"))
                .andExpect(jsonPath("$.message").value(matchesPattern("Phone number already used by C\\d{4,} – First Phone\\.")));
        second.put("allowDuplicatePhone", true);
        mvc.perform(json(post("/customers/register"), second)).andExpect(status().isCreated());
    }

    @Test
    @SuppressWarnings("unchecked")
    void editListsTheChangedFieldsAndChecksTheVersion() throws Exception {
        long id = register(customer("Edit Me"));
        Map<String, Object> detail = read(mvc.perform(get("/customers/" + id).cookie(admin)).andReturn());
        Map<String, Object> edit = customer("Edit Me");
        edit.put("phone", detail.get("phone"));
        edit.put("cycleWeeks", 2);
        edit.put("payType", "CREDIT");
        edit.put("creditLimit", 20000);
        edit.put("termsDays", 30);
        edit.put("version", detail.get("version"));
        mvc.perform(json(put("/customers/" + id), edit)).andExpect(status().isOk());
        assertThat(lastAudit("Customer", (String) detail.get("code")).getDetails())
                .isEqualTo("cycle: 1 → 2 wk; payment: Cash → Credit; credit limit: Rs. 0.00 → Rs. 20,000.00; terms: 0 → 30 days");
        mvc.perform(json(put("/customers/" + id), edit)).andExpect(status().isConflict());
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchFiltersAndLookup() throws Exception {
        String unique = "Zeta" + System.nanoTime();
        Map<String, Object> a = customer(unique + " Stores");
        long idA = register(a);
        Map<String, Object> b = customer(unique + " Home");
        b.put("deliveryDay", 3);
        b.put("nextDeliveryDate", ScheduleRules.firstDelivery(today, DayOfWeek.WEDNESDAY).toString());
        register(b);
        String code = (String) read(mvc.perform(get("/customers/" + idA).cookie(admin)).andReturn()).get("code");

        Map<String, Object> list = read(mvc.perform(get("/customers?q=" + unique.toLowerCase()).cookie(admin)).andReturn());
        assertThat(list.get("total")).isEqualTo(2);
        list = read(mvc.perform(get("/customers?q=" + unique + "&day=3").cookie(admin)).andReturn());
        assertThat((List<Map<String, Object>>) list.get("items")).extracting(x -> x.get("name")).containsExactly(unique + " Home");
        list = read(mvc.perform(get("/customers?area=" + areaId + "&size=1&page=1").cookie(admin)).andReturn());
        assertThat(list.get("total")).isEqualTo(2);
        assertThat((List<?>) list.get("items")).hasSize(1);
        // Phone digits (3 or more) find the customer.
        String digits = ((String) a.get("phone")).replace(" ", "").substring(4);
        list = read(mvc.perform(get("/customers?q=" + digits).cookie(admin)).andReturn());
        assertThat((List<Map<String, Object>>) list.get("items")).extracting(x -> x.get("code")).contains(code);

        // Lookup: code, number, phone digits, name.
        String number = String.valueOf(Integer.parseInt(code.substring(1)));
        assertThat(readList(mvc.perform(get("/customers/lookup?q=" + code.toLowerCase()).cookie(admin)).andReturn()))
                .extracting(x -> x.get("code")).containsExactly(code);
        assertThat(readList(mvc.perform(get("/customers/lookup?q=" + number).cookie(admin)).andReturn()))
                .extracting(x -> x.get("code")).containsExactly(code);
        assertThat(readList(mvc.perform(get("/customers/lookup").param("q", (String) a.get("phone")).cookie(admin)).andReturn()))
                .extracting(x -> x.get("code")).containsExactly(code);
        assertThat(readList(mvc.perform(get("/customers/lookup?q=" + unique.substring(2)).cookie(admin)).andReturn())).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void deactivatedCustomerKeepsHistoryAndLeavesTheActiveList() throws Exception {
        String unique = "Gone" + System.nanoTime();
        long id = register(customer(unique));
        mvc.perform(json(post("/customers/" + id + "/deactivate"), Map.of("date", today.plusDays(1).toString(), "reason", "x")))
                .andExpect(jsonPath("$.details.fields.date").value("The status date cannot be in the future."));
        mvc.perform(json(post("/customers/" + id + "/deactivate"), Map.of("date", today.toString(), "reason", "Moved away – Kandy")))
                .andExpect(status().isNoContent());
        assertThat(read(mvc.perform(get("/customers?q=" + unique).cookie(admin)).andReturn()).get("total")).isEqualTo(0);
        Map<String, Object> inactive = read(mvc.perform(get("/customers?q=" + unique + "&status=INACTIVE").cookie(admin)).andReturn());
        assertThat((List<Map<String, Object>>) inactive.get("items")).singleElement()
                .satisfies(x -> assertThat(x).containsEntry("statusReason", "Moved away – Kandy"));

        // Make the next delivery date lie in the past, then reactivate: it moves to the next Monday from today.
        jdbc.update("update customer set next_delivery_date = DATE '2026-01-05' where id = ?", id);
        mvc.perform(post("/customers/" + id + "/reactivate").cookie(admin)).andExpect(status().isNoContent());
        Map<String, Object> d = read(mvc.perform(get("/customers/" + id).cookie(admin)).andReturn());
        assertThat(d).containsEntry("status", "ACTIVE")
                .containsEntry("nextDeliveryDate", ScheduleRules.onOrAfter(today, DayOfWeek.MONDAY).toString());
        assertThat(readList(mvc.perform(get("/customers/" + id + "/history").cookie(admin)).andReturn()))
                .extracting(h -> h.get("kind")).contains("REGISTERED", "CHANGED");
    }

    @Test
    void aCustomerTypeInUseCannotBeDeactivated() throws Exception {
        register(customer("Type User"));
        mvc.perform(json(put("/customer-types/" + typeId), Map.of("description", "x", "active", false)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("TYPE_IN_USE"))
                .andExpect(jsonPath("$.message").value("Active customers use this type. Move them first."));
        mvc.perform(get("/customer-types").cookie(admin))
                .andExpect(jsonPath("$[?(@.id == " + typeId + ")].activeCustomers").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void accountantSeesCustomersButCannotChangeThem() throws Exception {
        long id = register(customer("Read Only"));
        Cookie acc = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/customers").cookie(acc)).andExpect(status().isOk());
        mvc.perform(get("/customers/" + id).cookie(acc)).andExpect(status().isOk());
        mvc.perform(get("/customers/lookup?q=Read").cookie(acc)).andExpect(status().isOk());
        mvc.perform(json(post("/customers/register"), acc, customer("X"))).andExpect(status().isForbidden());
        mvc.perform(json(put("/customers/" + id), acc, customer("X"))).andExpect(status().isForbidden());
        mvc.perform(json(post("/customers/" + id + "/deactivate"), acc, Map.of("date", today.toString(), "reason", "x")))
                .andExpect(status().isForbidden());
        Cookie ds = login(createUser(Role.DELIVERY_STAFF));
        mvc.perform(get("/customers").cookie(ds)).andExpect(status().isForbidden());
    }

    @Test
    void ledgerKeepsCustomerBottleBalancesAboveZero() throws Exception {
        long id = register(customer("Bottle Holder"));
        tx.executeWithoutResult(s -> ledger.post(new Posting(SourceType.IMPORT, 900_000L + id, "T", today, "Opening bottles",
                List.of(Effect.customer(id, bottle, CustomerField.HELD, 2)))));
        assertThat(ledger.customerBalances(id).get(bottle)).containsEntry(CustomerField.HELD, 2);
        String label = bottle.substring(1).replace('_', '.') + "L";
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ledger.post(new Posting(SourceType.SALES_TXN, 900_100L + id, "T2", today,
                "Too many", List.of(Effect.customer(id, bottle, CustomerField.HELD, -3))))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Bottles held (" + label + ") for Bottle Holder cannot go below zero – currently 2.");
        mvc.perform(get("/customers/" + id).cookie(admin))
                .andExpect(jsonPath("$.bottles[?(@.bottleTypeCode == '" + bottle + "')].held").value(org.hamcrest.Matchers.contains(2)));
    }
}
