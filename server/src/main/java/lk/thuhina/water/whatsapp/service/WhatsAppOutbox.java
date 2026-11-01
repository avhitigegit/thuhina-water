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
 * A quotation goes to the number the office chose ({@link #enqueueTo}) – the office acts, so no opt-in is needed.
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
        return Optional.of(save(type, customerId, (String) rows.getFirst().get("whatsapp_number"), params, sourceType, sourceId));
    }

    /** Queues one message to the number given (no opt-in check); empty when WhatsApp or the type is off. */
    @Transactional
    public Optional<WhatsAppMessage> enqueueTo(MessageType type, Long customerId, String number, List<String> params, String sourceType,
                                               Long sourceId) {
        if (!active(type)) {
            return Optional.empty();
        }
        return Optional.of(save(type, customerId, number, params, sourceType, sourceId));
    }

    private WhatsAppMessage save(MessageType type, Long customerId, String entered, List<String> params, String sourceType, Long sourceId) {
        String to = WhatsAppRules.toInternational(entered);
        Instant now = Instant.now(clock);
        WhatsAppMessage m = new WhatsAppMessage(customerId, type, to != null ? to : entered == null || entered.isBlank() ? "-" : entered,
                json.writeValueAsString(params), sourceType, sourceId, now);
        if (to == null) {
            m.skip(MSG_NO_NUMBER, now);
        }
        return messages.save(m);
    }

    public static boolean switchedOn(WhatsAppSettingsService.Config c, MessageType type) {
        if (!c.enabled()) {
            return false;
        }
        return switch (type) {
            case WELCOME -> c.welcome();
            case QUOTATION -> c.quotation();
            case DELIVERY_BILL -> c.deliveryBill();
            case BILL_CANCELLED -> c.billCancelled();
            case PAYMENT_RECEIPT -> c.paymentReceipt();
            case MONTHLY_INVOICE -> c.monthlyInvoice();
            case PAYMENT_DUE_SOON -> c.dueSoon();
            case PAYMENT_OVERDUE -> c.overdue();
            case TEST -> true;
        };
    }
}
