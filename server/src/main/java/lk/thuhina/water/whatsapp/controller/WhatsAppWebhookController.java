package lk.thuhina.water.whatsapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lk.thuhina.water.whatsapp.service.WhatsAppWebhookService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Meta's webhook (design 9.2) – public (no login; SecurityConfig permits it), protected by the verify token (GET) and the
 * {@code X-Hub-Signature-256} signature (POST).
 */
@Tag(name = "WhatsApp")
@RestController
@RequestMapping("/whatsapp/webhook")
public class WhatsAppWebhookController {

    private final WhatsAppWebhookService webhook;

    public WhatsAppWebhookController(WhatsAppWebhookService webhook) {
        this.webhook = webhook;
    }

    @Operation(summary = "Meta subscription check: echoes hub.challenge when hub.verify_token matches")
    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        String echo = webhook.verify(mode, token, challenge);
        return echo == null ? ResponseEntity.status(HttpStatus.FORBIDDEN).build() : ResponseEntity.ok(echo);
    }

    @Operation(summary = "Message status updates from Meta (signature required)")
    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> receive(@RequestBody byte[] body,
                                        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        if (!webhook.signatureValid(body, signature)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        webhook.receive(body);
        return ResponseEntity.ok().build();
    }
}
