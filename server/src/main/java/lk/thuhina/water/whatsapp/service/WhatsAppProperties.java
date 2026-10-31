package lk.thuhina.water.whatsapp.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * WhatsApp server settings from {@code application*.yml} (the Meta credentials are entered in the WhatsApp tab).
 *
 * @param apiUrl    Meta Graph API base address including the version
 * @param allowLive false = always use the test sender, even with credentials (integration tests)
 * @param batchSize messages the sender job takes per run
 */
@ConfigurationProperties(prefix = "app.whatsapp")
public record WhatsAppProperties(@DefaultValue("https://graph.facebook.com/v23.0") String apiUrl,
                                 @DefaultValue("true") boolean allowLive,
                                 @DefaultValue("20") int batchSize) {
}
