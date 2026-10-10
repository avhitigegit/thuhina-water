package lk.thuhina.water.settings.service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Settings groups that can be read and changed through {@code /settings/{group}}, with the keys each one
 * accepts. Later modules add groups (e.g. {@code whatsapp} in M13) or keys here.
 */
public enum SettingsGroup {

    /**
     * Company details printed on bills, invoices, receipts and other documents. {@code logo} is the stored file's key,
     * set only by the logo upload ({@code POST /settings/company/logo}).
     */
    COMPANY("company", fields(
            "name", Type.REQUIRED_TEXT, "address", Type.TEXT, "phone", Type.TEXT,
            "email", Type.EMAIL, "regNo", Type.TEXT, "logo", Type.READ_ONLY)),

    /** General business settings. */
    BUSINESS("business", fields(
            "currency", Type.TEXT, "timeZone", Type.TEXT, "dateFormat", Type.TEXT)),

    /** Feature switches, e.g. {@code dataMigration} shows or hides the Data Migration menu. */
    FEATURES("features", fields(
            "dataMigration", Type.BOOLEAN));

    /** {@code READ_ONLY} keys are shown but cannot be changed through {@code PUT /settings/{group}}. */
    public enum Type { TEXT, REQUIRED_TEXT, EMAIL, BOOLEAN, READ_ONLY }

    private final String key;
    private final Map<String, Type> fields;

    SettingsGroup(String key, Map<String, Type> fields) {
        this.key = key;
        this.fields = fields;
    }

    public String key() {
        return key;
    }

    public Map<String, Type> fields() {
        return fields;
    }

    public static Optional<SettingsGroup> byKey(String key) {
        return Arrays.stream(values()).filter(g -> g.key.equals(key)).findFirst();
    }

    private static Map<String, Type> fields(Object... pairs) {
        Map<String, Type> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Type) pairs[i + 1]);
        }
        return Map.copyOf(map);
    }
}
