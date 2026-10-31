package lk.thuhina.water.whatsapp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.model.WhatsAppMessage;
import lk.thuhina.water.whatsapp.repository.WhatsAppMessageRepository;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Outbox (design 9.2): queues a message in the caller's transaction – only when WhatsApp is on, that message type is on and
 * the customer has opted in. An opted-in customer without a valid mobile number gets a SKIPPED row, so the log shows why.
 */
@Service
public class WhatsAppOutbox {

    public static final String MSG_NO_NUMBER = "No valid WhatsApp number (a Sri Lankan mobile number) on the customer.";

    private final WhatsAppSettingsService settings;
    private final WhatsAppMessageRepository messages;
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public WhatsAppOutbox(WhatsAppSettingsService settings, WhatsAppMessageRepository messages, NamedParameterJdbcTemplate jdbc,
                          ObjectMapper json, Clock clock) {
        this.settings = settings;
        this.messages = messages;
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    /** True when this message type would be queued at all (WhatsApp on and the type switched on). */
    public boolean active(MessageType type) {
        return switchedOn(settings.config(), type);
    }

    /** Queues one message; empty when WhatsApp, the type or the customer's opt-in is off. */
    @Transactional
    public Optional<WhatsAppMessage> enqueue(MessageType type, long customerId, List<String> params, String sourceType, Long sourceId) {
        if (!active(type)) {
            return Optional.empty();
        }
        List<Map<String, Object>> rows = jdbc.queryForList("select whatsapp_opt_in, whatsapp_number from customer where id = :c",
                Map.of("c", customerId));
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("whatsapp_opt_in"))) {
            return Optional.empty();
        }
        String entered = (String) rows.getFirst().get("whatsapp_number");
        String to = WhatsAppRules.toInternational(entered);
        Instant now = Instant.now(clock);
        WhatsAppMessage m = new WhatsAppMessage(customerId, type, to != null ? to : entered == null ? "-" : entered,
                json.writeValueAsString(params), sourceType, sourceId, now);
        if (to == null) {
            m.skip(MSG_NO_NUMBER, now);
        }
        return Optional.of(messages.save(m));
    }

    static boolean switchedOn(WhatsAppSettingsService.Config c, MessageType type) {
        if (!c.enabled()) {
            return false;
        }
        return switch (type) {
            case LEFT_AT_DOOR -> c.leftAtDoor();
            case PAYMENT_REMINDER -> c.paymentReminder();
            case MONTHLY_INVOICE -> c.monthlyInvoice();
            case TEST -> true;
        };
    }
}
