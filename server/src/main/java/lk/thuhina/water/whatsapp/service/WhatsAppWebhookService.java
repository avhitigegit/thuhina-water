package lk.thuhina.water.whatsapp.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;

import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.repository.WhatsAppMessageRepository;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Meta's webhook (design 9.2): {@code GET} subscription check with the verify token; {@code POST} status updates, accepted only
 * with a valid {@code X-Hub-Signature-256} (HMAC SHA-256 of the raw body with the app secret).
 */
@Service
public class WhatsAppWebhookService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookService.class);

    private final WhatsAppSettingsService settings;
    private final WhatsAppMessageRepository messages;
    private final ObjectMapper json;
    private final Clock clock;

    public WhatsAppWebhookService(WhatsAppSettingsService settings, WhatsAppMessageRepository messages, ObjectMapper json, Clock clock) {
        this.settings = settings;
        this.messages = messages;
        this.json = json;
        this.clock = clock;
    }

    /** The challenge to echo when the mode is "subscribe" and the token matches the saved verify token; otherwise null. */
    public String verify(String mode, String token, String challenge) {
        String saved = settings.config().verifyToken();
        if (!"subscribe".equals(mode) || saved == null || token == null || challenge == null) {
            return null;
        }
        return MessageDigest.isEqual(saved.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8)) ? challenge : null;
    }

    public boolean signatureValid(byte[] body, String signature) {
        return WhatsAppRules.signatureValid(settings.config().appSecret(), body, signature);
    }

    /** Applies the status updates in a signed body; returns how many messages changed. */
    @Transactional
    public int receive(byte[] body) {
        JsonNode root = json.readTree(body);
        Instant now = Instant.now(clock);
        int changed = 0;
        for (JsonNode entry : root.path("entry")) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode s : change.path("value").path("statuses")) {
                    MessageStatus reported = WhatsAppRules.fromMeta(s.path("status").asString(null));
                    String id = s.path("id").asString(null);
                    if (reported == null || id == null) {
                        continue;
                    }
                    String error = null;
                    JsonNode err = s.path("errors").path(0);
                    if (!err.isMissingNode()) {
                        String detail = err.path("error_data").path("details").asString(err.path("message").asString(""));
                        error = ("Meta: " + err.path("title").asString("error") + (detail.isBlank() ? "" : " – " + detail));
                        error = error.length() > 500 ? error.substring(0, 500) : error;
                    }
                    String errorText = error;
                    changed += messages.findByMetaMessageId(id).filter(m -> WhatsAppRules.canMoveTo(m.getStatus(), reported)).map(m -> {
                        m.reported(reported, errorText, now);
                        return 1;
                    }).orElse(0);
                }
            }
        }
        if (changed > 0) {
            log.debug("WhatsApp webhook updated {} message(s)", changed);
        }
        return changed;
    }
}
