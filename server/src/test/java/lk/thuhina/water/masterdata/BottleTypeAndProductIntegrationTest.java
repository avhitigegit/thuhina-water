package lk.thuhina.water.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.Cookie;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

/** Bottle types (code from size, one per size, size fixed) and products (opening stock only on create). */
class BottleTypeAndProductIntegrationTest extends MasterDataTestSupport {

    @Test
    void a19LitreBottleGetsCodeB19AndASecond19LitreIsRejected() throws Exception {
        mvc.perform(json(post("/bottle-types"), admin, Map.of("name", "19L Bottle", "litres", 19)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("B19"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.deposit").value(nullValue()))
                .andExpect(jsonPath("$.inCirculation").value(nullValue()));
        assertThat(lastAudit("Bottle type", "B19").getDetails()).isEqualTo("19L Bottle");

        mvc.perform(json(post("/bottle-types"), admin, Map.of("name", "Another 19", "litres", "19.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.litres").value("A 19L bottle type already exists (19L Bottle)."));
    }

    @Test
    void sizeAndNameAreRequiredAndTheSizeCannotChange() throws Exception {
        mvc.perform(json(post("/bottle-types"), admin, Map.of("name", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Name and size in litres are required."))
                .andExpect(jsonPath("$.details.fields.litres").value("Name and size in litres are required."));
        mvc.perform(json(post("/bottle-types"), admin, Map.of("name", "Bad", "litres", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.litres").value("Size: more than 0 and at most 1000 litres, with up to 2 decimals."));

        String code = newBottle();
        // The edit has no size: sending one changes nothing.
        Map<String, Object> edit = new HashMap<>(Map.of("name", "Renamed", "active", false, "litres", 999));
        mvc.perform(json(put("/bottle-types/" + code), admin, edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.code").value(code));
        assertThat(lastAudit("Bottle type", code).getDetails()).endsWith("L, Active → Renamed, " + code.substring(1).replace('_', '.') + "L, Inactive");

        // Inactive types are left out of the pick lists.
        MvcResult active = mvc.perform(get("/bottle-types?activeOnly=true").cookie(admin)).andReturn();
        assertThat(readList(active)).noneMatch(b -> code.equals(b.get("code")));
    }

    @Test
    void productCodeOnSaveAndOpeningStockOnlyWhenCreating() throws Exception {
        Map<String, Object> create = new HashMap<>();
        create.put("name", "Tap Filter");
        create.put("sellingPrice", 1450);
        create.put("costPrice", 900);
        create.put("openingStock", 12);
        MvcResult r = mvc.perform(json(post("/products"), admin, create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("P\\d{2,}")))
                .andExpect(jsonPath("$.stockQty").value(12))
                .andExpect(jsonPath("$.sellingPrice").value(1450.0))
                .andReturn();
        Map<String, Object> p = read(r);
        String code = (String) p.get("code");
        assertThat(lastAudit("Product", code).getDetails()).isEqualTo("Tap Filter – Rs. 1,450.00, opening stock 12");

        // Stock cannot be changed by an edit.
        Map<String, Object> edit = new HashMap<>();
        edit.put("name", "Tap Filter");
        edit.put("sellingPrice", 1500);
        edit.put("costPrice", null);
        edit.put("active", true);
        edit.put("stockQty", 99);
        edit.put("openingStock", 99);
        mvc.perform(json(put("/products/" + p.get("id")), admin, edit))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQty").value(12))
                .andExpect(jsonPath("$.costPrice").value(nullValue()));
        assertThat(lastAudit("Product", code).getDetails())
                .isEqualTo("Tap Filter: price Rs. 1,450.00 → Rs. 1,500.00; cost Rs. 900.00 → –");
        assertThat(lastAudit("Product", code).getAction()).isEqualTo(AuditAction.UPDATE);
    }

    @Test
    void productValidation() throws Exception {
        mvc.perform(json(post("/products"), admin, Map.of("name", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.fields.name").value("Name and selling price are required."))
                .andExpect(jsonPath("$.details.fields.sellingPrice").value("Name and selling price are required."));
        mvc.perform(json(post("/products"), admin, Map.of("name", "X", "sellingPrice", 0)))
                .andExpect(jsonPath("$.details.fields.sellingPrice").value("Enter a selling price greater than zero."));
        mvc.perform(json(post("/products"), admin, Map.of("name", "X", "sellingPrice", 10, "costPrice", -1)))
                .andExpect(jsonPath("$.details.fields.costPrice").value("Cost price cannot be negative."));
        mvc.perform(json(post("/products"), admin, Map.of("name", "X", "sellingPrice", 10, "openingStock", -1)))
                .andExpect(jsonPath("$.details.fields.openingStock").value("Opening stock cannot be negative."));
    }

    @Test
    void staleEditGives409() throws Exception {
        String code = newBottle();
        mvc.perform(json(put("/bottle-types/" + code), admin, Map.of("name", "A", "active", true, "version", 0)))
                .andExpect(status().isOk());
        mvc.perform(json(put("/bottle-types/" + code), admin, Map.of("name", "B", "active", true, "version", 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void otherRolesCanReadBottleTypesButNotChangeMasterData() throws Exception {
        String code = newBottle();
        for (Role role : new Role[] {Role.ACCOUNTANT, Role.DELIVERY_STAFF}) {
            Cookie cookie = login(createUser(role));
            mvc.perform(get("/bottle-types").cookie(cookie)).andExpect(status().isOk());
            mvc.perform(json(post("/bottle-types"), cookie, Map.of("name", "X", "litres", uniqueLitres())))
                    .andExpect(status().isForbidden());
            mvc.perform(json(put("/bottle-types/" + code), cookie, Map.of("name", "X", "active", true)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/products").cookie(cookie)).andExpect(status().isForbidden());
            mvc.perform(json(post("/products"), cookie, Map.of("name", "X", "sellingPrice", 10)))
                    .andExpect(status().isForbidden());
        }
    }
}
