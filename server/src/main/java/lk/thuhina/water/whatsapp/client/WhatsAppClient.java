package lk.thuhina.water.whatsapp.client;

import java.util.List;

import lk.thuhina.water.whatsapp.service.WhatsAppSettingsService.Config;

/** Sends WhatsApp template messages (design 9.2): {@link MetaWhatsAppClient} for real, {@link FakeWhatsAppClient} in test mode. */
public interface WhatsAppClient {

    /** A PDF sent as the template's document header (the monthly invoice). */
    record Document(byte[] content, String fileName) {
    }

    /**
     * Sends one approved template message.
     *
     * @param to       international number, digits only ({@code 94771234567})
     * @param params   body parameters in order
     * @param document header document, or null
     * @return Meta's message id
     * @throws WhatsAppException when Meta refuses it or cannot be reached
     */
    String send(Config config, String to, String template, String language, List<String> params, Document document);

    /** Checks the phone number id and access token; returns a short description of the connected number. */
    String verify(Config config);

    /** True for the test sender (nothing goes to Meta). */
    boolean testMode();
}
