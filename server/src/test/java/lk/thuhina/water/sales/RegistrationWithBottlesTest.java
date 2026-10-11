package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Registration with bottles (FR-08 + FR-32, M06 S5) and the bill print (S12). Water Rs. 350, deposit Rs. 1,000. */
class RegistrationWithBottlesTest extends SalesTestSupport {

    private Map<String, Object> creditCustomer(String name) {
        Map<String, Object> req = customer(name + " " + System.nanoTime());
        req.put("payType", "CREDIT");
        req.put("creditLimit", 10000);
        req.put("termsDays", 30);
        return req;
    }

    @Test
    @SuppressWarnings("unchecked")
    void referenceScenario3BottlesWith1OldBottleIsRs3050InOneStep() throws Exception {
        Map<String, Object> req = creditCustomer("Nimali");
        req.put("bottlesGiven", Map.of(bottle, 3));
        req.put("oldBottles", 1);
        LocalDate chosenNext = LocalDate.parse((String) req.get("nextDeliveryDate"));
        int filled = stock("FILLED");
        int empty = stock("EMPTY");

        mvc.perform(json(post("/customers/register/preview"), req))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount").value(3050.0))
                .andExpect(jsonPath("$.deposit").value(2000.0))
                .andExpect(jsonPath("$.lines[0].oldBottles").value(1))
                .andExpect(jsonPath("$.problems").isEmpty());

        Map<String, Object> saved = registerResult(req);
        List<Map<String, Object>> bills = (List<Map<String, Object>>) saved.get("bills");
        assertThat(bills).hasSize(1);
        Map<String, Object> bill = bills.getFirst();
        assertThat(bill.get("kind")).isEqualTo("FIRST_PURCHASE");
        assertThat(((Number) bill.get("amount")).doubleValue()).isEqualTo(3050.0);
        assertThat(bill.get("note")).isEqualTo("Bottles given at registration");
        long c = ((Number) ((Map<String, Object>) saved.get("customer")).get("id")).longValue();
        assertThat(bottles(c)).containsExactly(3, 0, 0);
        assertThat(balance(c)).isEqualByComparingTo("3050");
        assertThat(stock("FILLED")).isEqualTo(filled - 3);
        assertThat(stock("EMPTY")).isEqualTo(empty + 1);
        assertThat(jdbc.queryForObject("select next_delivery_date from customer where id = ?", LocalDate.class, c)).isEqualTo(chosenNext);

        // The print shows the right lines.
        MvcResult pdf = mvc.perform(get("/print/bill/" + txnId(bill)).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"bill-" + bill.get("billNo") + ".pdf\""))
                .andReturn();
        String text = new PdfTextExtractor(new PdfReader(pdf.getResponse().getContentAsByteArray())).getTextFromPage(1).replaceAll("\\s+", " ");
        assertThat(text).contains("BILL", (String) bill.get("billNo"), "Water – " + label + " S", "Bottle deposit – " + label + " (not refundable)",
                "Old " + brandName + " bottles handed in – no deposit", "Rs. 3,050.00", "Account balance now", "Bottles held: 3 × " + label,
                "Customer signature", "First purchase – Bottles given at registration");
    }

    @Test
    void registrationFailsCleanlyWhenFilledStockIsTooLow() throws Exception {
        count("FILLED", 2);
        Map<String, Object> req = creditCustomer("Short");
        req.put("bottlesGiven", Map.of(bottle, 3));
        mvc.perform(json(post("/customers/register/preview"), req))
                .andExpect(jsonPath("$.problems[0].message").value("Only 2 filled " + label + " bottles in store."));
        send("/customers/register", req).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("STOCK_NEGATIVE"))
                .andExpect(jsonPath("$.message").value("Only 2 filled " + label + " bottles in store."));
        assertThat(jdbc.queryForObject("select count(*) from customer where name = ?", Integer.class, req.get("name"))).isZero();
        assertThat(stock("FILLED")).isEqualTo(2);
    }

    @Test
    void registrationChecksOldBottlesAndCash() throws Exception {
        Map<String, Object> req = creditCustomer("Old");
        req.put("bottlesGiven", Map.of(bottle, 1));
        req.put("oldBottles", 2);
        send("/customers/register", req).andExpect(jsonPath("$.details.fields.oldBottles")
                .value("Old bottles cannot be more than the " + label + " bottles given."));
        req.put("oldBottles", 0);
        req.put("paid", 5000);
        send("/customers/register", req).andExpect(jsonPath("$.details.fields.paid")
                .value("Cash paid cannot be more than the total of the bills."));
        assertThat(jdbc.queryForObject("select count(*) from customer where name = ?", Integer.class, req.get("name"))).isZero();
        // Part paid: the rest goes to the balance.
        req.put("paid", 1000);
        long c = register(req);
        assertThat(balance(c)).isEqualByComparingTo("350");
    }

    @Test
    @SuppressWarnings("unchecked")
    void registeringWithAnAgreedPriceBillsTheFirstPurchaseAtThatPrice() throws Exception {
        Map<String, Object> req = creditCustomer("Factory");
        req.put("agreedPrices", Map.of(bottle, 300));
        req.put("agreedReason", "Quotation accepted");
        req.put("bottlesGiven", Map.of(bottle, 2));
        mvc.perform(json(post("/customers/register/preview"), req))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(300.0))
                .andExpect(jsonPath("$.lines[0].agreedPrice").value(true))
                .andExpect(jsonPath("$.amount").value(2600.0));
        Map<String, Object> bill = ((List<Map<String, Object>>) registerResult(req).get("bills")).getFirst();
        assertThat(((Number) bill.get("amount")).doubleValue()).isEqualTo(2 * 300 + 2 * 1000.0);
        Map<String, Object> line = ((List<Map<String, Object>>) bill.get("lines")).getFirst();
        assertThat(line).containsEntry("unitPrice", 300.0).containsEntry("agreedPrice", true);
    }

    @Test
    void creditLimitAtRegistrationNeedsApproval() throws Exception {
        Map<String, Object> req = creditCustomer("Limit");
        req.put("creditLimit", 2000);
        req.put("bottlesGiven", Map.of(bottle, 3));
        send("/customers/register", req).andExpect(jsonPath("$.code").value("CREDIT_LIMIT"));
        assertThat(jdbc.queryForObject("select count(*) from customer where name = ?", Integer.class, req.get("name"))).isZero();
        req.put("approveOverLimit", true);
        long c = register(req);
        assertThat(balance(c)).isEqualByComparingTo("4050");
    }
}
