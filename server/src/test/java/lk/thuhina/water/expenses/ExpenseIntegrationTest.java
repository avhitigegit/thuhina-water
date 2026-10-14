package lk.thuhina.water.expenses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.expenses.rules.ExpenseRules;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * M15 Expenses – SERVER (S2, S4): add for an earlier month, month view and summary, validation messages, edit, delete with a
 * reason (soft delete + audit DELETE), expense types, the month statement and the role rules. The test database is shared, so
 * each test uses its own expense type and compares month totals before and after.
 */
class ExpenseIntegrationTest extends IntegrationTest {

    @Autowired
    private Clock clock;

    private Cookie admin;
    private LocalDate today;
    private YearMonth thisMonth;
    private YearMonth lastMonth;
    private long typeId;
    private String typeName;

    @BeforeEach
    void setUp() throws Exception {
        admin = login(createUser(Role.ADMIN));
        today = BusinessDates.today(clock);
        thisMonth = YearMonth.from(today);
        lastMonth = thisMonth.minusMonths(1);
        typeName = "Insurance " + System.nanoTime();
        typeId = id(call(post("/expense-types"), admin, Map.of("name", typeName, "description", "Vehicle insurance"), 201));
    }

    // ------------------------------------------------------------------ helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(MockHttpServletRequestBuilder b, Cookie cookie, Object body, int status) throws Exception {
        b.cookie(cookie);
        if (body != null) {
            b.contentType(MediaType.APPLICATION_JSON).content(body(body));
        }
        MvcResult res = mvc.perform(b).andReturn();
        String text = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(text).isEqualTo(status);
        return text.isEmpty() ? Map.of() : json.readValue(text, Map.class);
    }

    private Map<String, Object> getJson(String url) throws Exception {
        return call(get(url), admin, null, 200);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    private static long id(Map<String, Object> m) {
        return ((Number) m.get("id")).longValue();
    }

    private static BigDecimal money(Object o) {
        return new BigDecimal(o.toString());
    }

    private Map<String, Object> expense(Object... kv) {
        Map<String, Object> m = new HashMap<>(Map.of("paidDate", today.toString(), "forMonth", lastMonth.toString(), "typeId", typeId,
                "amount", 4260, "method", "CASH", "description", "Store – bill", "paidTo", "CEB", "billNo", "A/C 4471230981"));
        for (int i = 0; i < kv.length; i += 2) {
            if (kv[i + 1] == null) {
                m.remove((String) kv[i]);
            } else {
                m.put((String) kv[i], kv[i + 1]);
            }
        }
        return m;
    }

    private String fieldError(Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = (Map<String, Object>) ((Map<String, Object>) body.get("details")).get("fields");
        return fields.entrySet().iterator().next().getKey() + ": " + fields.values().iterator().next();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> types() throws Exception {
        MvcResult res = mvc.perform(get("/expense-types").cookie(admin)).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(200);
        return json.readValue(res.getResponse().getContentAsString(), List.class);
    }

    private BigDecimal monthTotal(YearMonth m) throws Exception {
        return money(getJson("/expenses?month=" + m).get("total"));
    }

    private List<Map<String, Object>> audit(String ref) {
        return jdbc.queryForList("select action, details from audit_log where entity = 'Expense' and ref = ? order by id", ref);
    }

    // ------------------------------------------------------------------ tests

    @Test
    void aBillPaidThisMonthCountsForLastMonthAndShowsInTheMonthViewSummaryAndPrint() throws Exception {
        BigDecimal before = monthTotal(lastMonth);
        Map<String, Object> saved = call(post("/expenses"), admin, expense("method", "CHEQUE", "reference", "Cheque 330145"), 201);
        @SuppressWarnings("unchecked")
        Map<String, Object> e = (Map<String, Object>) saved.get("expense");
        String no = (String) e.get("expenseNo");
        assertThat(no).matches("EX-\\d{4,}");
        assertThat(saved.get("message")).isEqualTo(no + " saved – Rs. 4,260.00 for " + ExpenseRules.monthLabel(lastMonth));
        assertThat(e.get("forMonth")).isEqualTo(lastMonth.toString());
        assertThat(e.get("paidInOtherMonth")).isEqualTo(true);
        assertThat(e.get("methodLabel")).isEqualTo("Cheque");

        Map<String, Object> view = getJson("/expenses?month=" + lastMonth + "&type=" + typeId);
        assertThat(list(view.get("items"))).extracting(x -> x.get("expenseNo")).containsExactly(no);
        assertThat(money(view.get("shownTotal"))).isEqualByComparingTo("4260");
        assertThat(money(view.get("total"))).isEqualByComparingTo(before.add(new BigDecimal("4260")));
        assertThat(list(view.get("byType"))).anySatisfy(t -> {
            assertThat(t.get("name")).isEqualTo(typeName);
            assertThat(money(t.get("total"))).isEqualByComparingTo("4260");
        });
        assertThat(view.get("previousLabel")).isEqualTo(ExpenseRules.monthLabel(lastMonth.minusMonths(1)));
        assertThat(money(view.get("difference"))).isEqualByComparingTo(money(view.get("total")).subtract(money(view.get("previousTotal"))));
        assertThat(list(view.get("months")).getFirst().get("month")).isEqualTo(thisMonth.toString());
        assertThat(list(getJson("/expenses?month=" + lastMonth + "&q=" + no).get("items"))).hasSize(1);
        assertThat(list(getJson("/expenses?month=" + thisMonth + "&type=" + typeId).get("items"))).isEmpty();

        Map<String, Object> summary = getJson("/expenses/summary?from=" + lastMonth + "&to=" + thisMonth);
        assertThat(list(summary.get("months"))).extracting(m -> m.get("month")).containsExactly(lastMonth.toString(), thisMonth.toString());
        Map<String, Object> row = list(summary.get("rows")).stream().filter(r -> typeName.equals(r.get("name"))).findFirst().orElseThrow();
        assertThat(((List<?>) row.get("amounts")).stream().map(v -> new BigDecimal(v.toString())).toList())
                .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("4260"), BigDecimal.ZERO);
        assertThat(money(list(summary.get("months")).getFirst().get("total"))).isEqualByComparingTo(money(view.get("total")));
        assertThat(list(summary.get("rows")).stream().map(r -> r.get("name")).toList()).contains("Electricity", "Other");

        MvcResult pdf = mvc.perform(get("/print/expenses?month=" + lastMonth).cookie(admin)).andReturn();
        assertThat(pdf.getResponse().getStatus()).isEqualTo(200);
        String text = new PdfTextExtractor(new PdfReader(pdf.getResponse().getContentAsByteArray())).getTextFromPage(1);
        assertThat(text).contains("EXPENSES", "Expenses for " + ExpenseRules.monthLabel(lastMonth), "Store – bill (A/C 4471230981)",
                "330145", "Prepared by");
        assertThat(audit(no)).extracting(a -> a.get("action")).containsExactly("CREATE");
    }

    @Test
    void validationUsesThePrototypeMessages() throws Exception {
        assertThat(fieldError(call(post("/expenses"), admin, expense("method", "CHEQUE"), 400)))
                .isEqualTo("reference: Enter the cheque number or bank reference.");
        assertThat(fieldError(call(post("/expenses"), admin, expense("method", "BANK_TRANSFER", "reference", " "), 400)))
                .isEqualTo("reference: Enter the cheque number or bank reference.");
        assertThat(fieldError(call(post("/expenses"), admin, expense("paidDate", today.plusDays(1).toString()), 400)))
                .isEqualTo("paidDate: Date cannot be after today (" + BusinessDates.format(today) + ").");
        assertThat(fieldError(call(post("/expenses"), admin, expense("forMonth", thisMonth.plusMonths(1).toString()), 400)))
                .isEqualTo("forMonth: The month cannot be after this month.");
        assertThat(fieldError(call(post("/expenses"), admin, expense("forMonth", null), 400)))
                .isEqualTo("forMonth: Select the month the expense is for.");
        assertThat(fieldError(call(post("/expenses"), admin, expense("typeId", null), 400))).isEqualTo("typeId: Select the expense type.");
        assertThat(fieldError(call(post("/expenses"), admin, expense("amount", 0), 400))).isEqualTo("amount: Enter the amount paid.");
        assertThat(fieldError(call(post("/expense-types"), admin, Map.of("name", typeName.toUpperCase()), 400)))
                .isEqualTo("name: That expense type already exists.");
        assertThat(fieldError(call(post("/expense-types"), admin, Map.of("name", " "), 400))).isEqualTo("name: Enter the expense type name.");
        assertThat(fieldError(call(get("/expenses/summary?from=" + thisMonth + "&to=" + lastMonth), admin, null, 400)))
                .isEqualTo("from: The From month must be on or before the To month.");
    }

    @Test
    void editAnyFieldDeleteWithAReasonAndInactiveTypes() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> e = (Map<String, Object>) call(post("/expenses"), admin, expense(), 201).get("expense");
        long id = id(e);
        String no = (String) e.get("expenseNo");
        BigDecimal lastBefore = monthTotal(lastMonth);
        BigDecimal thisBefore = monthTotal(thisMonth);

        // Edit: amount, month and how it was paid.
        Map<String, Object> edited = call(put("/expenses/" + id), admin, expense("amount", 4500, "forMonth", thisMonth.toString(),
                "method", "BANK_TRANSFER", "reference", "TRF-1", "version", e.get("version")), 200);
        assertThat(edited.get("message")).isEqualTo("Expense updated");
        assertThat(monthTotal(lastMonth)).isEqualByComparingTo(lastBefore.subtract(new BigDecimal("4260")));
        assertThat(monthTotal(thisMonth)).isEqualByComparingTo(thisBefore.add(new BigDecimal("4500")));
        // A stale version is refused.
        call(put("/expenses/" + id), admin, expense("version", e.get("version")), 409);

        // Deactivating the type: the expense can still be edited, a new one cannot use it.
        assertThat(types()).anySatisfy(t -> assertThat(t.get("name")).isEqualTo(typeName));
        Map<String, Object> deactivated = call(put("/expense-types/" + typeId), admin, Map.of("active", false), 200);
        assertThat(deactivated.get("active")).isEqualTo(false);
        long version = ((Number) getJson("/expenses/" + id).get("version")).longValue();
        call(put("/expenses/" + id), admin, expense("amount", 4600, "forMonth", thisMonth.toString(), "version", version), 200);
        assertThat(fieldError(call(post("/expenses"), admin, expense(), 400))).isEqualTo("typeId: That expense type is not active.");

        // Delete needs a reason; it is kept in the audit log and the month total drops.
        assertThat(fieldError(call(delete("/expenses/" + id), admin, Map.of("reason", " "), 400)))
                .isEqualTo("reason: Enter the reason for deleting.");
        call(delete("/expenses/" + id), admin, Map.of("reason", "Entered twice"), 204);
        call(get("/expenses/" + id), admin, null, 404);
        call(put("/expenses/" + id), admin, expense(), 404);
        assertThat(monthTotal(thisMonth)).isEqualByComparingTo(thisBefore);
        assertThat(jdbc.queryForObject("select deleted and delete_reason = 'Entered twice' and deleted_by is not null from expense "
                + "where id = ?", Boolean.class, id)).isTrue();
        List<Map<String, Object>> log = audit(no);
        assertThat(log).extracting(a -> a.get("action")).containsExactly("CREATE", "UPDATE", "UPDATE", "DELETE");
        assertThat((String) log.get(1).get("details")).contains("amount Rs. 4,260.00 → Rs. 4,500.00", "paid by Bank transfer (TRF-1)");
        assertThat((String) log.getLast().get("details")).isEqualTo(typeName + " Rs. 4,600.00 for " + ExpenseRules.monthLabel(thisMonth)
                + " – Store – bill – deleted: Entered twice");
        assertThat(jdbc.queryForObject("select details from audit_log where entity = 'Expense type' and ref = ? and action = 'UPDATE'",
                String.class, typeName)).isEqualTo("Deactivated");
    }

    @Test
    void adminAndAccountantFullDeliveryStaffNone() throws Exception {
        Cookie accountant = login(createUser(Role.ACCOUNTANT));
        Cookie staff = login(createUser(Role.DELIVERY_STAFF));
        @SuppressWarnings("unchecked")
        Map<String, Object> e = (Map<String, Object>) call(post("/expenses"), accountant, expense(), 201).get("expense");
        call(delete("/expenses/" + id(e)), accountant, Map.of("reason", "Test"), 204);
        call(get("/expenses"), accountant, null, 200);
        call(get("/expenses"), staff, null, 403);
        call(get("/expenses/summary"), staff, null, 403);
        mvc.perform(get("/expense-types").cookie(staff)).andExpect(
                org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        call(post("/expenses"), staff, expense(), 403);
        call(post("/expense-types"), staff, Map.of("name", "X " + System.nanoTime()), 403);
        call(get("/print/expenses"), staff, null, 403);
    }
}
