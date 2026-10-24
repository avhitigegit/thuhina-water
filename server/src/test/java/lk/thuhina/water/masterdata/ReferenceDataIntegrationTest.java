package lk.thuhina.water.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Customer types, areas and accepted old-bottle brands (M02 S4, S6). */
class ReferenceDataIntegrationTest extends MasterDataTestSupport {

    @Test
    void customerTypeNamesAreUniqueAndTheNameCannotChange() throws Exception {
        String name = "Hotel " + System.nanoTime();
        MvcResult r = mvc.perform(json(post("/customer-types"), admin, Map.of("name", name, "description", "Hotels")))
                .andExpect(status().isCreated()).andReturn();
        long id = ((Number) read(r).get("id")).longValue();

        mvc.perform(json(post("/customer-types"), admin, Map.of("name", name.toUpperCase())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Customer type already exists."));
        mvc.perform(json(post("/customer-types"), admin, Map.of("name", " ")))
                .andExpect(jsonPath("$.details.fields.name").value("Enter the customer type name."));

        mvc.perform(json(put("/customer-types/" + id), admin, Map.of("name", "Changed", "description", "Hotels and guest houses", "active", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.active").value(false));
        assertThat(lastAudit("Customer type", name).getDetails()).isEqualTo("Inactive – Hotels and guest houses");
    }

    @Test
    void areasHaveUniqueNamesAndCanBeRenamed() throws Exception {
        String name = "Area " + System.nanoTime();
        String other = "Other " + System.nanoTime();
        MvcResult r = mvc.perform(json(post("/areas"), admin, Map.of("name", name))).andExpect(status().isCreated()).andReturn();
        long id = ((Number) read(r).get("id")).longValue();
        mvc.perform(json(post("/areas"), admin, Map.of("name", other))).andExpect(status().isCreated());

        mvc.perform(json(post("/areas"), admin, Map.of("name", name.toLowerCase())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Area already exists."));
        mvc.perform(json(put("/areas/" + id), admin, Map.of("name", other)))
                .andExpect(jsonPath("$.details.fields.name").value("Area already exists."));
        mvc.perform(json(put("/areas/" + id), admin, Map.of("name", name + " North", "active", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(lastAudit("Area", name + " North").getDetails()).isEqualTo(name + ", Active → " + name + " North, Inactive");
    }

    @Test
    void oldBottleBrandCanBeStoppedAndAcceptedAgain() throws Exception {
        String bottle = newBottle();
        Map<String, Object> add = new HashMap<>(Map.of("name", "American Water", "bottleTypeCode", bottle, "note", " no cracks "));
        MvcResult r = mvc.perform(json(post("/old-bottle-brands"), admin, add))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.note").value("no cracks"))
                .andExpect(jsonPath("$.addedOn").value(today().toString()))
                .andReturn();
        long id = ((Number) read(r).get("id")).longValue();
        String bottleName = (String) read(r).get("bottleTypeName");

        mvc.perform(json(post("/old-bottle-brands"), admin, add))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("American Water is already in the list for " + bottleName + "."));

        Map<String, Object> stop = new HashMap<>(Map.of("bottleTypeCode", bottle, "note", "no cracks", "active", false));
        mvc.perform(json(put("/old-bottle-brands/" + id), admin, stop))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        assertThat(lastAudit("Accepted old-bottle brand", "American Water").getDetails()).isEqualTo("Stopped accepting");

        stop.put("active", true);
        mvc.perform(json(put("/old-bottle-brands/" + id), admin, stop))
                .andExpect(jsonPath("$.active").value(true));
        assertThat(lastAudit("Accepted old-bottle brand", "American Water").getDetails()).isEqualTo("Accepted again");

        // Only active bottle types can be chosen.
        mvc.perform(json(put("/bottle-types/" + bottle), admin, Map.of("name", "Off", "active", false))).andExpect(status().isOk());
        mvc.perform(json(post("/old-bottle-brands"), admin, Map.of("name", "Other Brand", "bottleTypeCode", bottle)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.bottleTypeCode").value("Choose an active bottle type."));
    }

    @Test
    void everyoneReadsTypesAndAreasButOnlyAdminChangesThem() throws Exception {
        for (Role role : new Role[] {Role.ACCOUNTANT, Role.DELIVERY_STAFF}) {
            Cookie cookie = login(createUser(role));
            mvc.perform(get("/customer-types").cookie(cookie)).andExpect(status().isOk());
            mvc.perform(get("/areas").cookie(cookie)).andExpect(status().isOk());
            mvc.perform(json(post("/customer-types"), cookie, Map.of("name", "X" + System.nanoTime()))).andExpect(status().isForbidden());
            mvc.perform(json(post("/areas"), cookie, Map.of("name", "X" + System.nanoTime()))).andExpect(status().isForbidden());
            mvc.perform(get("/old-bottle-brands").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(json(post("/old-bottle-brands"), cookie, Map.of("name", "X", "bottleTypeCode", "B20")))
                    .andExpect(status().isForbidden());
        }
    }
}
