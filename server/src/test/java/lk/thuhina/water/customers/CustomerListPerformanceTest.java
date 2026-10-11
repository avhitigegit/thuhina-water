package lk.thuhina.water.customers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** M05 acceptance: search, filters and paging stay under 1 second with 10,000 customers. */
class CustomerListPerformanceTest extends CustomerTestSupport {

    @Test
    void listAndSearchWith10000CustomersTakeLessThanOneSecond() throws Exception {
        String prefix = "Perf" + System.nanoTime();
        jdbc.update("insert into customer (code, name, address, area_id, phone, customer_type_id, delivery_day, cycle_weeks, "
                + "next_delivery_date, pay_type, registered_on, created_by) "
                + "select 'P' || lpad(g::text, 6, '0') || substr(?, length(?) - 2), ? || ' Customer ' || g, g || ', Long Road', ?, "
                + "'077 ' || lpad((g % 1000)::text, 3, '0') || ' ' || lpad(g::text, 4, '0'), ?, 1 + g % 7, 1, current_date, 'CASH', "
                + "current_date, 'test' from generate_series(1, 10000) g",
                prefix, prefix, prefix, areaId, typeId);
        jdbc.update("insert into customer_bottle_balance (customer_id, bottle_type_code) select c.id, ? from customer c "
                + "where c.name like ? on conflict do nothing", bottle, prefix + "%");
        jdbc.update("analyze customer");

        // Warm up once, then time a few typical screens.
        mvc.perform(get("/customers?area=" + areaId).cookie(admin));
        for (String query : new String[] {"area=" + areaId, "q=" + prefix.toLowerCase() + "%20customer%2099", "q=1234&day=3",
                "area=" + areaId + "&page=150&size=50", "area=" + areaId + "&price=STANDARD&sort=next"}) {
            long start = System.nanoTime();
            Map<String, Object> page = read(mvc.perform(get("/customers?" + query).cookie(admin)).andReturn());
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertThat(ms).as(query + " took " + ms + " ms").isLessThan(1000);
            assertThat(page).containsKey("total");
        }
        assertThat(read(mvc.perform(get("/customers?area=" + areaId).cookie(admin)).andReturn()).get("total")).isEqualTo(10000);
    }
}
