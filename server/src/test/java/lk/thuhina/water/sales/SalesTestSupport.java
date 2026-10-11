package lk.thuhina.water.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.delivery.rules.ScheduleRules;
import lk.thuhina.water.security.Role;
import lk.thuhina.water.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Helpers for sales tests. Each test gets its own area, customer type (water Rs. 350), bottle type (sizes 700.xx,
 * deposit Rs. 1,000, 100 filled + 20 empty in store) and old-bottle brand, because all tests share one database.
 * After every test the invariants are checked: With customers = Σ held + to collect + owed for the test bottle type,
 * and every test customer's account balance = Σ (amount − paid) of their bills.
 */
abstract class SalesTestSupport extends IntegrationTest {

    private static final AtomicInteger SIZE = new AtomicInteger();
    private static final AtomicInteger PHONE = new AtomicInteger(5000);

    @Autowired
    protected Clock clock;

    protected Cookie admin;
    protected long areaId;
    protected long typeId;
    protected String bottle;
    protected String label;
    protected long brandId;
    protected String brandName;
    protected LocalDate today;

    @BeforeEach
    void setUpSales() throws Exception {
        admin = login(createUser(Role.ADMIN));
        today = BusinessDates.today(clock);
        long n = System.nanoTime();
        areaId = id(mvc.perform(json(post("/areas"), Map.of("name", "Area " + n))).andReturn());
        typeId = id(mvc.perform(json(post("/customer-types"), Map.of("name", "Type " + n))).andReturn());
        BigDecimal litres = new BigDecimal("700").add(BigDecimal.valueOf(SIZE.incrementAndGet(), 2));
        bottle = (String) read(mvc.perform(json(post("/bottle-types"), Map.of("name", litres + "L S", "litres", litres))).andReturn()).get("code");
        label = litres.stripTrailingZeros().toPlainString() + "L";
        jdbc.update("insert into price_entry (kind, bottle_type_code, customer_type_id, price, effective_from, reason) "
                + "values ('WATER', ?, ?, 350, DATE '2026-01-01', 'test')", bottle, typeId);
        jdbc.update("insert into price_entry (kind, bottle_type_code, price, effective_from, reason) "
                + "values ('DEPOSIT', ?, 1000, DATE '2026-01-01', 'test')", bottle);
        brandName = "Brand " + n;
        brandId = id(mvc.perform(json(post("/old-bottle-brands"), Map.of("name", brandName, "bottleTypeCode", bottle))).andReturn());
        count("FILLED", 100);
        count("EMPTY", 20);
    }

    @AfterEach
    void invariants() {
        Integer withCustomers = jdbc.queryForObject("select qty from stock_balance where bottle_type_code = ? and bucket = 'CUSTOMERS'",
                Integer.class, bottle);
        Integer held = jdbc.queryForObject("select coalesce(sum(held + to_collect + owed), 0) from customer_bottle_balance "
                + "where bottle_type_code = ?", Integer.class, bottle);
        assertThat(withCustomers).as("With customers = Σ held + to collect + owed").isEqualTo(held);
        List<Map<String, Object>> wrong = jdbc.queryForList("select a.customer_id, a.balance, coalesce(sum(t.amount - t.paid_on_bill), 0) as bills "
                + "from customer_account a left join sales_txn t on t.customer_id = a.customer_id "
                + "where a.customer_id in (select customer_id from customer_usual_qty where bottle_type_code = ?) "
                + "group by a.customer_id, a.balance having a.balance <> coalesce(sum(t.amount - t.paid_on_bill), 0)", bottle);
        assertThat(wrong).as("account balance = Σ (amount − paid)").isEmpty();
    }

    /** Sets a store bucket of the test bottle type by a physical count. */
    protected void count(String bucket, int qty) throws Exception {
        MvcResult r = mvc.perform(json(post("/stock/adjustments"), Map.of("date", today.toString(), "itemCode", bottle, "mode", "COUNT",
                "bucket", bucket, "counted", qty, "reason", "test stock"))).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isIn(201, 422);
    }

    protected int stock(String bucket) {
        return jdbc.queryForObject("select qty from stock_balance where bottle_type_code = ? and bucket = ?", Integer.class, bottle, bucket);
    }

    /** held, to collect, owed of the test bottle type. */
    protected int[] bottles(long customerId) {
        return jdbc.queryForObject("select held, to_collect, owed from customer_bottle_balance where customer_id = ? and bottle_type_code = ?",
                (rs, i) -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)}, customerId, bottle);
    }

    protected BigDecimal balance(long customerId) {
        return jdbc.queryForObject("select balance from customer_account where customer_id = ?", BigDecimal.class, customerId);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Object body) {
        return json(b, admin, body);
    }

    protected MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, Cookie cookie, Object body) {
        return b.cookie(cookie).contentType(MediaType.APPLICATION_JSON).content(body(body));
    }

    protected ResultActions send(String url, Object body) throws Exception {
        return mvc.perform(json(post(url), body));
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> read(MvcResult r) throws Exception {
        return json.readValue(r.getResponse().getContentAsString(), Map.class);
    }

    protected long id(MvcResult r) throws Exception {
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isBetween(200, 201);
        return ((Number) read(r).get("id")).longValue();
    }

    /** Saves a sale and returns the bill (txn) as a map. */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> sale(String url, Map<String, Object> body) throws Exception {
        MvcResult r = send(url, body).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return (Map<String, Object>) read(r).get("txn");
    }

    protected static long txnId(Map<String, Object> txn) {
        return ((Number) txn.get("id")).longValue();
    }

    protected static String newPhone() {
        int n = PHONE.incrementAndGet();
        return String.format("071 8%02d %04d", n / 10000 % 100, n % 10000);
    }

    /** A valid new-customer request: Monday delivery, cash, usual 2 of the test bottle type. */
    protected Map<String, Object> customer(String name) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("address", "8, Sales Road");
        m.put("areaId", areaId);
        m.put("phone", newPhone());
        m.put("customerTypeId", typeId);
        m.put("deliveryDay", 1);
        m.put("cycleWeeks", 1);
        m.put("nextDeliveryDate", ScheduleRules.firstDelivery(today, DayOfWeek.MONDAY).toString());
        m.put("usualQty", Map.of(bottle, 2));
        m.put("payType", "CASH");
        return m;
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> registerResult(Map<String, Object> request) throws Exception {
        MvcResult r = send("/customers/register", request).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(201);
        return read(r);
    }

    /** Registers a customer (optionally with bottles) and returns its id. */
    @SuppressWarnings("unchecked")
    protected long register(Map<String, Object> request) throws Exception {
        return ((Number) ((Map<String, Object>) registerResult(request).get("customer")).get("id")).longValue();
    }

    /** A cash customer holding {@code held} bottles of the test type (a first purchase, fully paid). */
    protected long customerHolding(String name, int held) throws Exception {
        Map<String, Object> req = customer(name + " " + System.nanoTime());
        req.put("bottlesGiven", Map.of(bottle, held));
        req.put("paid", held * 1350);
        return register(req);
    }

    protected static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }
}
