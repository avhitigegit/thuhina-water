package lk.thuhina.water.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.customers.CustomerTestSupport;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;

/** Payments page: Money in (customer payments + money paid on bills) and Money out (suppliers, factories, expenses). */
class MoneyIntegrationTest extends CustomerTestSupport {

    private Map<String, Object> money(String direction, String q, Cookie who) throws Exception {
        return read(mvc.perform(get("/money/" + direction).param("from", today.minusDays(1).toString()).param("to", today.toString())
                .param("q", q).cookie(who)).andExpect(status().isOk()).andReturn());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> list) {
        return (List<Map<String, Object>>) list.get("items");
    }

    @Test
    void listsMoneyInAndOutWithTotals() throws Exception {
        String tag = "Money" + System.nanoTime();
        long c = register(customer(tag + " Customer"));
        jdbc.update("insert into customer_payment (receipt_no, customer_id, pay_date, amount, method, reference, balance_before, created_by) "
                + "values (?, ?, ?, 2500, 'CHEQUE', '551203', 0, 'test')", "RC-" + tag.substring(5, 15), c, today);
        long supplier = jdbc.queryForObject("insert into supplier (code, name, phone, terms_days) values (?, ?, '011 000 0000', 30) returning id",
                Long.class, "S" + tag.substring(tag.length() - 8), tag + " Supplier");
        jdbc.update("insert into supplier_payment (sp_no, supplier_id, pay_date, amount, method, reference) values (?, ?, ?, 10000, 'BANK_TRANSFER', 'TRF-1')",
                "SP-" + tag.substring(5, 15), supplier, today);
        long type = jdbc.queryForObject("insert into expense_type (name) values (?) returning id", Long.class, tag + " Power");
        jdbc.update("insert into expense (expense_no, paid_date, for_month, expense_type_id, paid_to, amount, method) "
                + "values (?, ?, date_trunc('month', ?::date)::date, ?, ?, 1800, 'CASH')", "EX-" + tag.substring(5, 15), today, today, type,
                tag + " CEB");
        jdbc.update("insert into expense (expense_no, paid_date, for_month, expense_type_id, paid_to, amount, method, deleted, delete_reason, deleted_at, deleted_by) "
                + "values (?, ?, date_trunc('month', ?::date)::date, ?, ?, 999, 'CASH', true, 'typo', now(), 'test')", "EY-" + tag.substring(5, 15), today, today,
                type, tag + " CEB");

        // Money in: the payment and the money paid when the customer was registered (a bill).
        Map<String, Object> in = money("in", tag, admin);
        assertThat(items(in)).extracting(r -> r.get("kind")).contains("PAYMENT");
        assertThat(items(in)).filteredOn(r -> "PAYMENT".equals(r.get("kind"))).singleElement().satisfies(r -> assertThat(r)
                .containsEntry("kindLabel", "Payment received").containsEntry("methodLabel", "Cheque").containsEntry("reference", "551203")
                .containsEntry("party", tag + " Customer"));
        BigDecimal sum = items(in).stream().map(r -> new BigDecimal(r.get("amount").toString())).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(new BigDecimal(in.get("total").toString())).isEqualByComparingTo(sum);
        assertThat(((Number) in.get("count")).intValue()).isEqualTo(items(in).size());

        // Money out: the supplier payment and the expense (not the deleted one).
        Map<String, Object> out = money("out", tag, admin);
        assertThat(items(out)).extracting(r -> r.get("kind")).containsExactlyInAnyOrder("SUPPLIER", "EXPENSE");
        assertThat(new BigDecimal(out.get("total").toString())).isEqualByComparingTo("11800");
        assertThat(items(out)).filteredOn(r -> "EXPENSE".equals(r.get("kind"))).singleElement().satisfies(r -> assertThat(r)
                .containsEntry("party", tag + " CEB").containsEntry("note", tag + " Power").containsEntry("methodLabel", "Cash"));

        // Accountant sees both; Delivery Staff none.
        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        assertThat(items(money("out", tag, accountant))).hasSize(2);
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        mvc.perform(get("/money/in").cookie(staff)).andExpect(status().isForbidden());
        mvc.perform(get("/money/out").cookie(staff)).andExpect(status().isForbidden());
        // From after To.
        mvc.perform(get("/money/in").param("from", today.toString()).param("to", today.minusDays(1).toString()).cookie(admin))
                .andExpect(status().is4xxClientError());
    }
}
