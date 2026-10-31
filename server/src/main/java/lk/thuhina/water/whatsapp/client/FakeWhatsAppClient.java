package lk.thuhina.water.whatsapp.client;

import java.util.List;
import java.util.UUID;

import lk.thuhina.water.whatsapp.service.WhatsAppSettingsService.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Test mode (local, UAT without real credentials – design 9.2): writes the message to the application log and reports it as
 * sent with a {@code test-…} id. Nothing goes to Meta and nothing is charged.
 */
@Component
public class FakeWhatsAppClient implements WhatsAppClient {

    private static final Logger log = LoggerFactory.getLogger(FakeWhatsAppClient.class);

    @Override
    public String send(Config config, String to, String template, String language, List<String> params, Document document) {
        String id = "test-" + UUID.randomUUID();
        log.info("WhatsApp (test mode) to {}: template {} ({}), params {}{}", to, template, language, params,
                document == null ? "" : ", document " + document.fileName() + " (" + document.content().length + " bytes)");
        return id;
    }

    @Override
    public String verify(Config config) {
        return "Test mode – no phone number ID / access token saved, so messages are only written to the log.";
    }

    @Override
    public boolean testMode() {
        return true;
    }
}
