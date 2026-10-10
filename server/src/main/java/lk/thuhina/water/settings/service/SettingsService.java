package lk.thuhina.water.settings.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.settings.model.AppSetting;
import lk.thuhina.water.settings.repository.AppSettingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Company details, business settings and feature switches (design 3.2 {@code settings}). */
@Service
public class SettingsService {

    private static final int MAX_TEXT = 300;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final AppSettingRepository repository;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public SettingsService(AppSettingRepository repository, AuditService audit, ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ObjectNode get(String groupKey) {
        SettingsGroup group = group(groupKey);
        return read(group);
    }

    /** True when the feature switch is on (missing = off). */
    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String feature) {
        return read(SettingsGroup.FEATURES).path(feature).asBoolean(false);
    }

    /**
     * Changes some keys of a group (keys not sent stay as they are). Unknown keys or wrong types are refused.
     * Audited with the names of the changed keys.
     */
    @Transactional
    public ObjectNode update(String groupKey, JsonNode changes) {
        SettingsGroup group = group(groupKey);
        if (changes == null || !changes.isObject()) {
            throw new ValidationException("body", "Send the settings as a JSON object.");
        }
        Map<String, Object> errors = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> e : changes.properties()) {
            String error = check(group.fields().get(e.getKey()), e.getValue());
            if (error != null) {
                errors.put(e.getKey(), error);
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        AppSetting row = repository.findById(group.key()).orElseGet(() -> new AppSetting(group.key(), "{}"));
        ObjectNode current = parse(row.getValue());
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : changes.properties()) {
            JsonNode value = e.getValue().isString() ? json.getNodeFactory().stringNode(e.getValue().asString().trim()) : e.getValue();
            if (!value.equals(current.get(e.getKey()))) {
                changed.add(e.getKey());
                current.set(e.getKey(), value);
            }
        }
        if (!changed.isEmpty()) {
            row.update(json.writeValueAsString(current), Instant.now(clock), CurrentUser.usernameOrSystem());
            repository.save(row);
            audit.log(AuditAction.UPDATE, "Setting", group.key(), "Changed " + group.key() + " settings: " + String.join(", ", changed));
        }
        return current;
    }

    /**
     * Sets one key without the {@code PUT} rules – for values the server manages itself, such as the company logo
     * key written by the logo upload. Audited with {@code auditDetails}.
     */
    @Transactional
    public ObjectNode setManaged(SettingsGroup group, String key, JsonNode value, String auditDetails) {
        AppSetting row = repository.findById(group.key()).orElseGet(() -> new AppSetting(group.key(), "{}"));
        ObjectNode current = parse(row.getValue());
        current.set(key, value);
        row.update(json.writeValueAsString(current), Instant.now(clock), CurrentUser.usernameOrSystem());
        repository.save(row);
        audit.log(AuditAction.UPDATE, "Setting", group.key(), auditDetails);
        return current;
    }

    /** The error message for a value of this type, or null when it is fine. */
    private static String check(SettingsGroup.Type type, JsonNode v) {
        if (type == null) {
            return "Unknown setting.";
        }
        return switch (type) {
            case READ_ONLY -> "This setting cannot be changed here.";
            case BOOLEAN -> v.isBoolean() ? null : "Must be true or false.";
            case TEXT_LIST -> {
                if (!v.isArray()) {
                    yield "Must be a list of texts.";
                }
                for (JsonNode item : v) {
                    if (!item.isString() || item.asString().length() > MAX_TEXT) {
                        yield "Must be a list of texts.";
                    }
                }
                yield null;
            }
            case TEXT, REQUIRED_TEXT, EMAIL -> {
                if (!(v.isNull() || v.isString())) {
                    yield "Must be text.";
                }
                String text = v.isNull() ? "" : v.asString().trim();
                if (text.length() > MAX_TEXT) {
                    yield "Must be at most " + MAX_TEXT + " characters.";
                }
                if (type == SettingsGroup.Type.REQUIRED_TEXT && text.isEmpty()) {
                    yield "Required.";
                }
                if (type == SettingsGroup.Type.EMAIL && !text.isEmpty() && !EMAIL.matcher(text).matches()) {
                    yield "Enter a valid email address.";
                }
                yield null;
            }
        };
    }

    private ObjectNode read(SettingsGroup group) {
        return repository.findById(group.key()).map(s -> parse(s.getValue())).orElseGet(json::createObjectNode);
    }

    private ObjectNode parse(String value) {
        JsonNode node = json.readTree(value);
        return node instanceof ObjectNode o ? o : json.createObjectNode();
    }

    private static SettingsGroup group(String key) {
        return SettingsGroup.byKey(key).orElseThrow(() -> new NotFoundException("Settings group", key));
    }

}
