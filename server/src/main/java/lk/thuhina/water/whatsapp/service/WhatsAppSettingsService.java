package lk.thuhina.water.whatsapp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.SecretCipher;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.settings.model.AppSetting;
import lk.thuhina.water.settings.repository.AppSettingRepository;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.SettingsRequest;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.SettingsView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * The WhatsApp settings (app_setting key {@code whatsapp}, design 9.2). Kept apart from the generic settings groups because
 * the access token, app secret and webhook verify token are write-only: stored AES-GCM encrypted and never returned.
 */
@Service
public class WhatsAppSettingsService {

    public static final String KEY = "whatsapp";
    public static final String WEBHOOK_PATH = "/api/whatsapp/webhook";

    private static final Pattern META_ID = Pattern.compile("^\\d{5,30}$");
    private static final Pattern LANGUAGE = Pattern.compile("^[a-z]{2}(_[A-Z]{2})?$");
    private static final int MAX_SECRET = 1000;
    /** Payment due soon / overdue: how many days before / after the due date (1 – 30). */
    public static final int MAX_REMINDER_DAYS = 30;

    private final AppSettingRepository repository;
    private final SecretCipher cipher;
    private final WhatsAppProperties properties;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public WhatsAppSettingsService(AppSettingRepository repository, SecretCipher cipher, WhatsAppProperties properties, AuditService audit,
                                   ObjectMapper json, Clock clock) {
        this.repository = repository;
        this.cipher = cipher;
        this.properties = properties;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    /** Settings with the secrets decrypted – for the outbox, the sender and the webhook only, never for a response. */
    public record Config(boolean enabled, String phoneNumberId, String businessAccountId, String accessToken, String appSecret,
                         String verifyToken, boolean welcome, boolean quotation, boolean deliveryBill, boolean billCancelled,
                         boolean paymentReceipt, boolean monthlyInvoice, boolean dueSoon, boolean overdue, int daysBefore, int daysAfter,
                         int reminderHour, String language, boolean allowLive) {

        /** Real sending: allowed and the phone number id and access token are saved. Otherwise the test sender is used. */
        public boolean live() {
            return allowLive && phoneNumberId != null && accessToken != null;
        }
    }

    @Transactional(readOnly = true)
    public Config config() {
        ObjectNode s = read();
        return new Config(s.path("enabled").asBoolean(false), text(s, "phoneNumberId"), text(s, "businessAccountId"),
                cipher.decrypt(text(s, "accessToken")), cipher.decrypt(text(s, "appSecret")), cipher.decrypt(text(s, "verifyToken")),
                on(s, "welcome"), on(s, "quotation"), on(s, "deliveryBill"), on(s, "billCancelled"), on(s, "paymentReceipt"),
                on(s, "monthlyInvoice"), on(s, "dueSoon"), on(s, "overdue"), s.path("daysBefore").asInt(3), s.path("daysAfter").asInt(3),
                s.path("reminderHour").asInt(9), s.path("language").asString("en"), properties.allowLive());
    }

    @Transactional(readOnly = true)
    public SettingsView view() {
        return view(read());
    }

    @Transactional
    public SettingsView update(SettingsRequest r) {
        Map<String, Object> errors = new LinkedHashMap<>();
        checkId(errors, "phoneNumberId", r.phoneNumberId());
        checkId(errors, "businessAccountId", r.businessAccountId());
        checkSecret(errors, "accessToken", r.accessToken());
        checkSecret(errors, "appSecret", r.appSecret());
        checkSecret(errors, "verifyToken", r.verifyToken());
        if (r.daysBefore() != null && (r.daysBefore() < 1 || r.daysBefore() > MAX_REMINDER_DAYS)) {
            errors.put("daysBefore", "Enter 1 to " + MAX_REMINDER_DAYS + " days.");
        }
        if (r.daysAfter() != null && (r.daysAfter() < 1 || r.daysAfter() > MAX_REMINDER_DAYS)) {
            errors.put("daysAfter", "Enter 1 to " + MAX_REMINDER_DAYS + " days.");
        }
        if (r.reminderHour() != null && (r.reminderHour() < 0 || r.reminderHour() > 23)) {
            errors.put("reminderHour", "Choose an hour from 0 to 23.");
        }
        if (r.language() != null && !LANGUAGE.matcher(r.language().trim()).matches()) {
            errors.put("language", "Language code like en or en_US.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        AppSetting row = repository.findById(KEY).orElseGet(() -> new AppSetting(KEY, "{}"));
        ObjectNode s = parse(row.getValue());
        List<String> changed = new ArrayList<>();
        bool(s, changed, "enabled", "switched " + (Boolean.TRUE.equals(r.enabled()) ? "on" : "off"), r.enabled());
        plain(s, changed, "phoneNumberId", "phone number ID", r.phoneNumberId());
        plain(s, changed, "businessAccountId", "business account ID", r.businessAccountId());
        secret(s, changed, "accessToken", "access token", r.accessToken());
        secret(s, changed, "appSecret", "app secret", r.appSecret());
        secret(s, changed, "verifyToken", "webhook verify token", r.verifyToken());
        bool(s, changed, "welcome", "Welcome " + onOff(r.welcome()), r.welcome());
        bool(s, changed, "quotation", "Quotation " + onOff(r.quotation()), r.quotation());
        bool(s, changed, "deliveryBill", "Delivery bill " + onOff(r.deliveryBill()), r.deliveryBill());
        bool(s, changed, "billCancelled", "Bill cancelled " + onOff(r.billCancelled()), r.billCancelled());
        bool(s, changed, "paymentReceipt", "Payment receipt " + onOff(r.paymentReceipt()), r.paymentReceipt());
        bool(s, changed, "monthlyInvoice", "Monthly invoice " + onOff(r.monthlyInvoice()), r.monthlyInvoice());
        bool(s, changed, "dueSoon", "Payment due soon " + onOff(r.dueSoon()), r.dueSoon());
        bool(s, changed, "overdue", "Payment overdue " + onOff(r.overdue()), r.overdue());
        number(s, changed, "daysBefore", "days before the due date", r.daysBefore());
        number(s, changed, "daysAfter", "days after the due date", r.daysAfter());
        number(s, changed, "reminderHour", "reminder hour", r.reminderHour());
        plain(s, changed, "language", "language", r.language());
        if (!changed.isEmpty()) {
            row.update(json.writeValueAsString(s), Instant.now(clock), CurrentUser.usernameOrSystem());
            repository.save(row);
            audit.log(AuditAction.UPDATE, "Setting", KEY, "WhatsApp settings: " + String.join(", ", changed));
        }
        return view(s);
    }

    private SettingsView view(ObjectNode s) {
        boolean live = properties.allowLive() && text(s, "phoneNumberId") != null && text(s, "accessToken") != null;
        String modeText = live
                ? "Live – messages are sent through WhatsApp Business (Meta)."
                : "Test mode – messages are only written to the log below (no phone number ID / access token saved).";
        return new SettingsView(s.path("enabled").asBoolean(false), text(s, "phoneNumberId"), text(s, "businessAccountId"),
                text(s, "accessToken") != null, text(s, "appSecret") != null, text(s, "verifyToken") != null, on(s, "welcome"),
                on(s, "quotation"), on(s, "deliveryBill"), on(s, "billCancelled"), on(s, "paymentReceipt"), on(s, "monthlyInvoice"),
                on(s, "dueSoon"), on(s, "overdue"), s.path("daysBefore").asInt(3), s.path("daysAfter").asInt(3),
                s.path("reminderHour").asInt(9), s.path("language").asString("en"), live ? "LIVE" : "TEST", modeText, cipher.available(),
                WEBHOOK_PATH);
    }

    private static void checkId(Map<String, Object> errors, String field, String value) {
        if (value != null && !value.isBlank() && !META_ID.matcher(value.trim()).matches()) {
            errors.put(field, "Digits only, as shown in Meta's WhatsApp Manager.");
        }
    }

    private static void checkSecret(Map<String, Object> errors, String field, String value) {
        if (value != null && value.trim().length() > MAX_SECRET) {
            errors.put(field, "Too long.");
        }
    }

    private static void bool(ObjectNode s, List<String> changed, String key, String label, Boolean value) {
        if (value != null && s.path(key).asBoolean(!value) != value) {
            s.put(key, value);
            changed.add(label);
        }
    }

    private static void number(ObjectNode s, List<String> changed, String key, String label, Integer value) {
        if (value != null && (!s.has(key) || s.get(key).asInt() != value)) {
            s.put(key, value);
            changed.add(label);
        }
    }

    private static void plain(ObjectNode s, List<String> changed, String key, String label, String value) {
        if (value == null) {
            return;
        }
        String v = value.trim();
        String old = text(s, key);
        if (v.isEmpty() ? old != null : !v.equals(old)) {
            if (v.isEmpty()) {
                s.remove(key);
            } else {
                s.put(key, v);
            }
            changed.add(label);
        }
    }

    /** Text = encrypt and save (always counts as a change – the old value cannot be compared); empty text = remove. */
    private void secret(ObjectNode s, List<String> changed, String key, String label, String value) {
        if (value == null) {
            return;
        }
        String v = value.trim();
        if (v.isEmpty()) {
            if (s.has(key)) {
                s.remove(key);
                changed.add(label + " removed");
            }
            return;
        }
        s.put(key, cipher.encrypt(v));
        changed.add(label + " saved");
    }

    /** A message type switch; on unless switched off. */
    private static boolean on(JsonNode s, String key) {
        return s.path(key).asBoolean(true);
    }

    private static String onOff(Boolean value) {
        return Boolean.TRUE.equals(value) ? "on" : "off";
    }

    private static String text(JsonNode s, String key) {
        JsonNode v = s.get(key);
        return v == null || v.isNull() || v.asString().isBlank() ? null : v.asString();
    }

    private ObjectNode read() {
        return repository.findById(KEY).map(row -> parse(row.getValue())).orElseGet(json::createObjectNode);
    }

    private ObjectNode parse(String value) {
        JsonNode node = json.readTree(value);
        return node instanceof ObjectNode o ? o : json.createObjectNode();
    }
}
