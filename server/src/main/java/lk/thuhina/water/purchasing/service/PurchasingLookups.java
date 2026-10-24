package lk.thuhina.water.purchasing.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lk.thuhina.water.partners.model.Supplier;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;
import lk.thuhina.water.partners.repository.SupplierRepository;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.SupplierLink;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Names used on purchasing screens: items ("Empty 20L Bottle", product names), suppliers and users' full names. */
@Component
public class PurchasingLookups {

    /** A purchasable item: empty bottles of a bottle type or a product. */
    public record Item(ItemType type, String code, String name, boolean active) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final SupplierRepository suppliers;

    public PurchasingLookups(NamedParameterJdbcTemplate jdbc, SupplierRepository suppliers) {
        this.jdbc = jdbc;
        this.suppliers = suppliers;
    }

    /** Every bottle type (largest first) and product (by code), keyed by code. */
    public Map<String, Item> items() {
        Map<String, Item> items = new LinkedHashMap<>();
        jdbc.query("select code, name, active from bottle_type order by litres desc", rs -> {
            items.put(rs.getString("code"), new Item(ItemType.BOTTLE, rs.getString("code"), "Empty " + rs.getString("name"),
                    rs.getBoolean("active")));
        });
        jdbc.query("select code, name, active from product order by code", rs -> {
            items.put(rs.getString("code"), new Item(ItemType.PRODUCT, rs.getString("code"), rs.getString("name"), rs.getBoolean("active")));
        });
        return items;
    }

    public static String name(Map<String, Item> items, String code) {
        Item i = items.get(code);
        return i == null ? code : i.name();
    }

    /** Suppliers by id (all, a few dozen at most). */
    public Map<Long, Supplier> suppliers() {
        return suppliers.findAll().stream().collect(Collectors.toMap(Supplier::getId, s -> s));
    }

    public static SupplierLink link(Supplier s) {
        return new SupplierLink(s.getId(), s.getCode(), s.getName());
    }

    /** Full names of users; a username without a user (e.g. "seed", "system") is shown as it is. */
    public Map<String, String> userNames(Collection<String> usernames) {
        Set<String> wanted = usernames.stream().filter(u -> u != null).collect(Collectors.toSet());
        Map<String, String> out = new HashMap<>();
        if (!wanted.isEmpty()) {
            jdbc.query("select username, full_name from app_user where username in (:u)", new MapSqlParameterSource("u", wanted),
                    rs -> {
                        out.put(rs.getString("username"), rs.getString("full_name"));
                    });
        }
        wanted.forEach(u -> out.putIfAbsent(u, u));
        return out;
    }

    public String userName(String username) {
        return username == null ? null : userNames(Set.of(username)).get(username);
    }
}
