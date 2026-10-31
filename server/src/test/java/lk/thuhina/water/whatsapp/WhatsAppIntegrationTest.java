package lk.thuhina.water.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.security.Role;
import lk.thuhina.water.sales.SalesTestSupport;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import lk.thuhina.water.whatsapp.service.PaymentReminderJob;
import lk.thuhina.water.whatsapp.service.WhatsAppSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * M13 WhatsApp – SERVER (S1 – S9) with the test sender (tests never call Meta): queue conditions (master switch, type switch,
 * opt-in, number), Left at door and monthly invoice messages, sending with the invoice PDF, retries until FAILED, the weekly
 * reminder rule (overdue only, once a week), webhook verify / signature / status order, secrets never returned, Admin only.
 */
class WhatsAppIntegrationTest extends SalesTestSupport {

    private static final String SECRET = "test-app-secret-123";

    @Autowired
    private WhatsAppSender sender;
    @Autowired
    private PaymentReminderJob reminders;

    @AfterEach
    void whatsappOff() {
        // All tests share one database: leave WhatsApp off with the default switches for the other test classes.
        jdbc.update("update app_setting set value = jsonb_build_object('enabled', false, 'leftAtDoor', true, 'paymentReminder', true, "
                + "'monthlyInvoice', true, 'reminderDay', 1, 'reminderHour', 9, 'language', 'en') where key = 'whatsapp'");
    }

    private Map<String, Object> settings(Map<String, Object> changes) throws Exception {
        MvcResult r = mvc.perform(json(put("/whatsapp/settings"), changes)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return read(r);
    }

    /** A customer holding 4 bottles who opted in with a WhatsApp number (or not). */
    private long optedIn(String name, String whatsapp) throws Exception {
        long c = customerHolding(name, 4);
        jdbc.update("update customer set whatsapp_opt_in = ?, whatsapp_number = ? where id = ?", whatsapp != null, whatsapp, c);
        return c;
    }

    private void leftAtDoor(long customerId, LocalDate date) throws Exception {
        sale("/sales/bills", map("customerId", customerId, "date", date.toString(), "paperBillNo", "W" + System.nanoTime() % 10_000_000_000L,
                "kind", "LEFT_AT_DOOR", "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 0)), "paid", 0,
                "note", "Left with the guard", "phoneConfirmed", true));
    }

    private List<Map<String, Object>> messages(long customerId) {
        return jdbc.queryForList("select id, type, status, to_number, params::text as params, test_mode, meta_message_id, error, "
                + "attempts, source_type, source_id from whatsapp_message where customer_id = ? order by id", customerId);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> log(String query) throws Exception {
        MvcResult r = mvc.perform(get("/whatsapp/messages" + query).cookie(admin)).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return (List<Map<String, Object>>) read(r).get("items");
    }

    // ------------------------------------------------------------------ queue conditions and Left at door (S3)

    @Test
    void leftAtDoorQueuesOnlyWhenSwitchedOnAndOptedIn() throws Exception {
        long opted = optedIn("WA Opted", "077 123 4567");
        long notOpted = optedIn("WA Not opted", null);
        long badNumber = optedIn("WA Landline", "011 285 4471");

        leftAtDoor(opted, today);
        assertThat(messages(opted)).as("WhatsApp is off").isEmpty();

        settings(map("enabled", true));
        leftAtDoor(opted, today);
        leftAtDoor(notOpted, today);
        leftAtDoor(badNumber, today);
        leftAtDoor(opted, today.minusDays(3));   // late entry of an old bill: nothing

        List<Map<String, Object>> m = messages(opted);
        assertThat(m).hasSize(1);
        assertThat(m.getFirst()).containsEntry("type", "LEFT_AT_DOOR").containsEntry("status", "QUEUED").containsEntry("to_number", "94771234567")
                .containsEntry("source_type", "SALES_TXN");
        List<String> params = json.readValue((String) m.getFirst().get("params"), List.class);
        String next = jdbc.queryForObject("select to_char(next_delivery_date, 'DD/MM/YYYY') from customer where id = ?", String.class, opted);
        assertThat(params).hasSize(4);
        assertThat(params.get(0)).startsWith("WA Opted ");
        assertThat(params.subList(1, 4)).containsExactly("2 × " + label, lk.thuhina.water.common.BusinessDates.format(today), next);
        assertThat(messages(notOpted)).isEmpty();
        assertThat(messages(badNumber)).singleElement().satisfies(x -> assertThat(x).containsEntry("status", "SKIPPED")
                .containsEntry("error", "No valid WhatsApp number (a Sri Lankan mobile number) on the customer."));

        settings(map("leftAtDoor", false));
        leftAtDoor(opted, today);
        assertThat(messages(opted)).as("Left at door switched off").hasSize(1);
    }

    // ------------------------------------------------------------------ sender, invoice PDF, retries (S4, S5)

    @Test
    void senderSendsInTestModeAndRetriesUntilFailed() throws Exception {
        settings(map("enabled", true));
        long c = optedIn("WA Sender", "0771112222");
        leftAtDoor(c, today);
        sender.sendDue();
        Map<String, Object> sent = messages(c).getFirst();
        assertThat(sent).containsEntry("status", "SENT").containsEntry("test_mode", true).containsEntry("attempts", 1);
        assertThat((String) sent.get("meta_message_id")).startsWith("test-");

        // An invoice message whose invoice does not exist: the PDF cannot be made → retried, FAILED after the 3rd attempt.
        jdbc.update("insert into whatsapp_message (customer_id, type, template, to_number, params, status, source_type, source_id, "
                + "next_attempt_at) values (?, 'MONTHLY_INVOICE', 'monthly_invoice_v1', '94771112222', '[\"x\"]', 'QUEUED', 'INVOICE', "
                + "999999999, now())", c);
        for (int i = 1; i <= 3; i++) {
            sender.sendDue();
            Map<String, Object> m = messages(c).getLast();
            assertThat(m.get("attempts")).isEqualTo(i);
            assertThat(m.get("status")).isEqualTo(i < 3 ? "QUEUED" : "FAILED");
            assertThat((String) m.get("error")).isNotBlank();
            jdbc.update("update whatsapp_message set next_attempt_at = now() - interval '1 minute' where id = ?", m.get("id"));
        }

        // Switched off before it went out: SKIPPED, never sent.
        leftAtDoor(c, today);
        settings(map("enabled", false));
        sender.sendDue();
        assertThat(messages(c).getLast()).containsEntry("status", "SKIPPED").containsEntry("attempts", 0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatedInvoiceQueuesOneMessageWithThePdf() throws Exception {
        settings(map("enabled", true));
        YearMonth month = YearMonth.from(today).minusMonths(1);
        Map<String, Object> req = customer("WA Monthly " + System.nanoTime());
        req.put("payType", "MONTHLY_BILL");
        req.put("creditLimit", 100000);
        req.put("termsDays", 30);
        req.put("registeredOn", today.minusDays(200).toString());
        req.put("bottlesGiven", Map.of(bottle, 4));
        req.put("paid", 4 * 1350);
        long c = register(req);
        jdbc.update("update customer set whatsapp_opt_in = true, whatsapp_number = '076 555 1234' where id = ?", c);
        sale("/sales/bills", map("customerId", c, "date", month.atDay(5).toString(), "paperBillNo", "I" + System.nanoTime() % 10_000_000_000L,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 2)), "paid", 0));

        MvcResult generated = mvc.perform(json(post("/invoices/generate"), map("period", month.toString()))).andReturn();
        assertThat(generated.getResponse().getStatus()).as(generated.getResponse().getContentAsString()).isEqualTo(200);
        Map<String, Object> result = read(generated);
        Map<String, Object> inv = ((List<Map<String, Object>>) result.get("created")).stream()
                .filter(r -> ((Number) ((Map<String, Object>) r.get("customer")).get("id")).longValue() == c).findFirst().orElseThrow();
        List<Map<String, Object>> m = messages(c);
        assertThat(m).singleElement().satisfies(x -> assertThat(x).containsEntry("type", "MONTHLY_INVOICE").containsEntry("source_type", "INVOICE")
                .containsEntry("source_id", ((Number) inv.get("id")).longValue()));
        List<String> params = json.readValue((String) m.getFirst().get("params"), List.class);
        assertThat(params.get(1)).isEqualTo(WhatsAppRules.monthLabel(month.toString()));
        assertThat(params.get(2)).isEqualTo("Rs. 700.00");

        sender.sendDue();   // the test sender receives the invoice PDF as the document
        assertThat(messages(c).getFirst()).containsEntry("status", "SENT");
    }

    // ------------------------------------------------------------------ payment reminders (S6)

    @Test
    void remindersOnlyForOverdueCustomersAndOncePerWeek() throws Exception {
        settings(map("enabled", true));
        long overdue = credit("WA Overdue", 7, today.minusDays(20));
        long notYet = credit("WA Not yet", 30, today.minusDays(20));
        long notOpted = credit("WA Quiet", 7, today.minusDays(20));
        jdbc.update("update customer set whatsapp_opt_in = false, whatsapp_number = null where id = ?", notOpted);

        reminders.run();
        assertThat(messages(overdue)).singleElement().satisfies(x -> assertThat(x).containsEntry("type", "PAYMENT_REMINDER"));
        List<String> params = json.readValue((String) messages(overdue).getFirst().get("params"), List.class);
        assertThat(params.get(1)).isEqualTo("Rs. 700.00");
        assertThat(params.get(2)).isEqualTo(lk.thuhina.water.common.BusinessDates.format(today.minusDays(13)));
        assertThat(messages(notYet)).as("within terms").isEmpty();
        assertThat(messages(notOpted)).isEmpty();

        reminders.run();
        assertThat(messages(overdue)).as("once a week").hasSize(1);

        settings(map("paymentReminder", false));
        jdbc.update("update whatsapp_message set created_at = now() - interval '8 days' where customer_id = ?", overdue);
        assertThat(reminders.run().queued()).as("switched off").isZero();
    }

    /** A Credit customer (terms in days) who opted in and owes Rs. 700 for a bill dated {@code billDate}. */
    private long credit(String name, int terms, LocalDate billDate) throws Exception {
        Map<String, Object> req = customer(name + " " + System.nanoTime());
        req.put("payType", "CREDIT");
        req.put("creditLimit", 50000);
        req.put("termsDays", terms);
        req.put("registeredOn", today.minusDays(90).toString());
        req.put("bottlesGiven", Map.of(bottle, 4));
        req.put("paid", 4 * 1350);
        long c = register(req);
        jdbc.update("update customer set whatsapp_opt_in = true, whatsapp_number = '077 999 0000' where id = ?", c);
        sale("/sales/bills", map("customerId", c, "date", billDate.toString(), "paperBillNo", "R" + System.nanoTime() % 10_000_000_000L,
                "lines", List.of(map("bottleTypeCode", bottle, "filled", 2, "empties", 2)), "paid", 0));
        return c;
    }

    // ------------------------------------------------------------------ webhook (S7)

    @Test
    void webhookNeedsTheVerifyTokenAndAValidSignature() throws Exception {
        settings(map("enabled", true, "appSecret", SECRET, "verifyToken", "verify-me"));
        mvc.perform(get("/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "verify-me").param("hub.challenge", "12345"))
                .andExpect(status().isOk()).andExpect(content().string("12345"));
        mvc.perform(get("/whatsapp/webhook").param("hub.mode", "subscribe").param("hub.verify_token", "wrong").param("hub.challenge", "12345"))
                .andExpect(status().isForbidden());

        long c = optedIn("WA Webhook", "077 444 5555");
        leftAtDoor(c, today);
        sender.sendDue();
        String metaId = (String) messages(c).getFirst().get("meta_message_id");

        byte[] read = statusUpdate(metaId, "read");
        mvc.perform(post("/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(read).header("X-Hub-Signature-256", "sha256=00"))
                .andExpect(status().isForbidden());
        assertThat(messages(c).getFirst()).containsEntry("status", "SENT");

        mvc.perform(post("/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(read)
                .header("X-Hub-Signature-256", WhatsAppRules.signature(SECRET, read))).andExpect(status().isOk());
        assertThat(messages(c).getFirst()).containsEntry("status", "READ");

        byte[] late = statusUpdate(metaId, "delivered");
        mvc.perform(post("/whatsapp/webhook").contentType(MediaType.APPLICATION_JSON).content(late)
                .header("X-Hub-Signature-256", WhatsAppRules.signature(SECRET, late))).andExpect(status().isOk());
        assertThat(messages(c).getFirst()).as("a late 'delivered' never replaces 'read'").containsEntry("status", "READ");
    }

    private static byte[] statusUpdate(String metaId, String status) {
        return ("{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"1\",\"changes\":[{\"field\":\"messages\",\"value\":"
                + "{\"statuses\":[{\"id\":\"" + metaId + "\",\"status\":\"" + status + "\",\"timestamp\":\"1730000000\","
                + "\"recipient_id\":\"94774445555\"}]}}]}]}").getBytes(StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ settings, test send, log, roles (S2, S8)

    @Test
    @SuppressWarnings("unchecked")
    void secretsAreNeverReturnedAndOnlyTheAdminManagesWhatsApp() throws Exception {
        Map<String, Object> view = settings(map("phoneNumberId", "123456789012345", "accessToken", "EAAG-very-secret-token",
                "appSecret", SECRET, "reminderDay", 3, "reminderHour", 10));
        String body = json.writeValueAsString(view);
        assertThat(body).doesNotContain("EAAG-very-secret-token").doesNotContain(SECRET);
        assertThat(view).containsEntry("accessTokenSaved", true).containsEntry("appSecretSaved", true).containsEntry("verifyTokenSaved", false)
                .containsEntry("phoneNumberId", "123456789012345").containsEntry("reminderDay", 3).containsEntry("mode", "TEST");
        MvcResult got = mvc.perform(get("/whatsapp/settings").cookie(admin)).andExpect(status().isOk()).andReturn();
        assertThat(got.getResponse().getContentAsString()).doesNotContain("EAAG-very-secret-token").doesNotContain(SECRET);
        String stored = jdbc.queryForObject("select value::text from app_setting where key = 'whatsapp'", String.class);
        assertThat(stored).doesNotContain("EAAG-very-secret-token").doesNotContain(SECRET).contains("v1:");

        mvc.perform(json(put("/whatsapp/settings"), map("reminderDay", 8, "phoneNumberId", "abc"))).andExpect(status().isBadRequest());
        assertThat(settings(map("accessToken", ""))).containsEntry("accessTokenSaved", false);

        Map<String, Object> test = read(mvc.perform(json(post("/whatsapp/test"), map("to", "077 662 0000"))).andExpect(status().isOk()).andReturn());
        assertThat(test).containsEntry("ok", true).containsEntry("mode", "TEST");
        mvc.perform(json(post("/whatsapp/test"), map("to", "011 285 4471"))).andExpect(status().isBadRequest());
        assertThat(log("?type=TEST")).anySatisfy(r -> assertThat(r).containsEntry("toNumber", "+94 77 662 0000").containsEntry("status", "SENT")
                .containsEntry("testMode", true));
        Map<String, Object> verify = read(mvc.perform(post("/whatsapp/verify").cookie(admin)).andExpect(status().isOk()).andReturn());
        assertThat(verify).containsEntry("ok", true).containsEntry("mode", "TEST");
        Map<String, Object> page = read(mvc.perform(get("/whatsapp/messages").cookie(admin)).andReturn());
        assertThat((Map<String, Object>) page.get("thisMonth")).containsKeys("sent", "failed", "skipped", "test");

        var accountant = login(createUser(Role.ACCOUNTANT));
        mvc.perform(get("/whatsapp/settings").cookie(accountant)).andExpect(status().isForbidden());
        mvc.perform(get("/whatsapp/messages").cookie(accountant)).andExpect(status().isForbidden());
        mvc.perform(get("/whatsapp/settings")).andExpect(status().isUnauthorized());
    }
}
