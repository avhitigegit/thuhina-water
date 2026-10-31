package lk.thuhina.water.whatsapp.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.whatsapp.service.WhatsAppProperties;
import lk.thuhina.water.whatsapp.service.WhatsAppSettingsService.Config;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * WhatsApp Cloud API (Meta Graph API, design 9.2): template messages ({@code POST /{phone-number-id}/messages}), a PDF for
 * the invoice header ({@code POST /{phone-number-id}/media}) and the connection check ({@code GET /{phone-number-id}}).
 */
@Component
public class MetaWhatsAppClient implements WhatsAppClient {

    private final RestClient http;
    private final ObjectMapper json;

    public MetaWhatsAppClient(WhatsAppProperties properties, ObjectMapper json) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(10));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl(properties.apiUrl()).requestFactory(factory).build();
        this.json = json;
    }

    @Override
    public String send(Config config, String to, String template, String language, List<String> params, Document document) {
        List<Object> components = new ArrayList<>();
        if (document != null) {
            String mediaId = upload(config, document);
            components.add(Map.of("type", "header", "parameters", List.of(Map.of("type", "document",
                    "document", Map.of("id", mediaId, "filename", document.fileName())))));
        }
        if (params != null && !params.isEmpty()) {
            components.add(Map.of("type", "body", "parameters", params.stream().map(p -> Map.of("type", "text", "text", p)).toList()));
        }
        Map<String, Object> tpl = new LinkedHashMap<>();
        tpl.put("name", template);
        tpl.put("language", Map.of("code", language));
        if (!components.isEmpty()) {
            tpl.put("components", components);
        }
        Map<String, Object> body = Map.of("messaging_product", "whatsapp", "recipient_type", "individual", "to", to,
                "type", "template", "template", tpl);
        JsonNode r = call(() -> http.post().uri("/{id}/messages", config.phoneNumberId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.accessToken())
                .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
        String id = r.path("messages").path(0).path("id").asString("");
        if (id.isBlank()) {
            throw new WhatsAppException("Meta did not return a message id.");
        }
        return id;
    }

    @Override
    public String verify(Config config) {
        JsonNode r = call(() -> http.get().uri("/{id}?fields=display_phone_number,verified_name,quality_rating", config.phoneNumberId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.accessToken()).retrieve().body(String.class));
        return "Connected to " + r.path("display_phone_number").asString("?") + " (" + r.path("verified_name").asString("no name")
                + "), quality " + r.path("quality_rating").asString("unknown") + ".";
    }

    @Override
    public boolean testMode() {
        return false;
    }

    private String upload(Config config, Document document) {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("messaging_product", "whatsapp");
        form.add("type", "application/pdf");
        form.add("file", new ByteArrayResource(document.content()) {
            @Override
            public String getFilename() {
                return document.fileName();
            }
        });
        JsonNode r = call(() -> http.post().uri("/{id}/media", config.phoneNumberId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.accessToken())
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(String.class));
        String id = r.path("id").asString("");
        if (id.isBlank()) {
            throw new WhatsAppException("Meta did not accept the PDF.");
        }
        return id;
    }

    private interface Call {
        String run();
    }

    /** Runs a request; Meta's error text (e.g. "(#132001) Template name does not exist") becomes the exception message. */
    private JsonNode call(Call call) {
        try {
            String body = call.run();
            return json.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException e) {
            throw new WhatsAppException(metaError(e.getResponseBodyAsString(), e.getStatusCode().value()), e);
        } catch (RestClientException e) {
            throw new WhatsAppException("Could not reach WhatsApp (Meta): " + e.getMessage(), e);
        }
    }

    private String metaError(String body, int status) {
        try {
            JsonNode err = json.readTree(body).path("error");
            String message = err.path("error_user_msg").asString(err.path("message").asString(""));
            if (!message.isBlank()) {
                return "Meta refused (" + err.path("code").asString(String.valueOf(status)) + "): " + message;
            }
        } catch (RuntimeException ignored) {
            // not JSON – fall through
        }
        return "Meta refused the request (HTTP " + status + ").";
    }
}
