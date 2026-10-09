package lk.thuhina.water.settings.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            SettingsGroup.Type type = group.fields().get(e.getKey());
            JsonNode v = e.getValue();
            if (type == null) {
                errors.put(e.getKey(), "Unknown setting.");
            } else if (type == SettingsGroup.Type.BOOLEAN && !v.isBoolean()) {
                errors.put(e.getKey(), "Must be true or false.");
            } else if (type == SettingsGroup.Type.TEXT && !(v.isNull() || v.isString())) {
                errors.put(e.getKey(), "Must be text.");
            } else if (type == SettingsGroup.Type.TEXT && v.isString() && v.asString().length() > MAX_TEXT) {
                errors.put(e.getKey(), "Must be at most " + MAX_TEXT + " characters.");
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
