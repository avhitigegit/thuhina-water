package lk.thuhina.water.whatsapp.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.printing.BillingPrintService;
import lk.thuhina.water.whatsapp.client.FakeWhatsAppClient;
import lk.thuhina.water.whatsapp.client.MetaWhatsAppClient;
import lk.thuhina.water.whatsapp.client.WhatsAppClient;
import lk.thuhina.water.whatsapp.client.WhatsAppException;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.CheckResult;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.model.WhatsAppMessage;
import lk.thuhina.water.whatsapp.repository.WhatsAppMessageRepository;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Sender job (design 9.1, every minute): takes up to 20 due QUEUED messages and sends them – through Meta when real credentials
 * are saved, otherwise through the test sender. Success → SENT with Meta's id; an error → tried again after 1 and 5 minutes,
 * then FAILED with the error text. The Meta call runs outside any database transaction, so billing never waits for Meta.
 */
@Service
public class WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppSender.class);
    /** Meta's {@code hello_world} template exists only in US English. */
    private static final String TEST_LANGUAGE = "en_US";
    static final String MSG_SWITCHED_OFF = "Not sent – WhatsApp or this message type was switched off before it went out.";

    private final WhatsAppSettingsService settings;
    private final WhatsAppMessageRepository messages;
    private final MetaWhatsAppClient meta;
    private final FakeWhatsAppClient fake;
    private final BillingPrintService billingPrint;
    private final WhatsAppProperties properties;
    private final AuditService audit;
    private final ObjectMapper json;
    private final Clock clock;

    public WhatsAppSender(WhatsAppSettingsService settings, WhatsAppMessageRepository messages, MetaWhatsAppClient meta,
                          FakeWhatsAppClient fake, BillingPrintService billingPrint, WhatsAppProperties properties, AuditService audit,
                          ObjectMapper json, Clock clock) {
        this.settings = settings;
        this.messages = messages;
        this.meta = meta;
        this.fake = fake;
        this.billingPrint = billingPrint;
        this.properties = properties;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.whatsapp.sender-cron:30 * * * * *}", zone = "Asia/Colombo")
    public void run() {
        int n = sendDue();
        if (n > 0) {
            log.info("WhatsApp sender handled {} message(s)", n);
        }
    }

    /** Sends the due messages; returns how many were handled. */
    public int sendDue() {
        WhatsAppSettingsService.Config c = settings.config();
        List<WhatsAppMessage> due = messages.findDue(Instant.now(clock), Limit.of(properties.batchSize()));
        for (WhatsAppMessage m : due) {
            send(m, c);
        }
        return due.size();
    }

    /** "Test send" from the WhatsApp tab: Meta's hello_world template to one number, straight away. Works with WhatsApp off. */
    public CheckResult test(String number) {
        String to = WhatsAppRules.toInternational(number);
        if (to == null) {
            throw new ValidationException("to", "Enter a Sri Lankan mobile number, e.g. 077 123 4567.");
        }
        WhatsAppSettingsService.Config c = settings.config();
        Instant now = Instant.now(clock);
        WhatsAppMessage m = messages.save(new WhatsAppMessage(null, MessageType.TEST, to, "[]", null, null, now));
        WhatsAppMessage done = send(m, c);
        audit.log(AuditAction.CREATE, "WhatsApp", "TEST", "Test message to " + WhatsAppRules.display(to) + ": " + done.getStatus().label());
        String mode = client(c).testMode() ? "TEST" : "LIVE";
        if (done.getStatus() == MessageStatus.SENT) {
            return new CheckResult(true, mode, mode.equals("LIVE")
                    ? "Test message sent to " + WhatsAppRules.display(to) + ". Check the phone."
                    : "Test mode: the message was written to the log only (no real credentials saved).");
        }
        return new CheckResult(false, mode, done.getError());
    }

    /** "Verify connection": checks the saved phone number id and access token with Meta. */
    public CheckResult verify() {
        WhatsAppSettingsService.Config c = settings.config();
        WhatsAppClient client = client(c);
        try {
            return new CheckResult(true, client.testMode() ? "TEST" : "LIVE", client.verify(c));
        } catch (WhatsAppException e) {
            return new CheckResult(false, "LIVE", e.getMessage());
        }
    }

    private WhatsAppMessage send(WhatsAppMessage m, WhatsAppSettingsService.Config c) {
        Instant now = Instant.now(clock);
        if (m.getType() != MessageType.TEST && !WhatsAppOutbox.switchedOn(c, m.getType())) {
            m.skip(MSG_SWITCHED_OFF, now);
            return messages.save(m);
        }
        WhatsAppClient client = client(c);
        try {
            List<String> params = json.readValue(m.getParams(), new TypeReference<List<String>>() {
            });
            WhatsAppClient.Document document = null;
            if (m.getType() == MessageType.MONTHLY_INVOICE && WhatsAppEvents.SOURCE_INVOICE.equals(m.getSourceType()) && m.getSourceId() != null) {
                document = new WhatsAppClient.Document(billingPrint.invoice(m.getSourceId()), billingPrint.invoiceNo(m.getSourceId()) + ".pdf");
            }
            String language = m.getType() == MessageType.TEST ? TEST_LANGUAGE : c.language();
            String id = client.send(c, m.getToNumber(), m.getTemplate(), language, params, document);
            m.sent(id, client.testMode(), Instant.now(clock));
        } catch (RuntimeException e) {
            String error = e instanceof WhatsAppException ? e.getMessage() : "Could not send: " + e.getMessage();
            int attempts = m.getAttempts() + 1;
            m.attemptFailed(truncate(error), m.getType() == MessageType.TEST ? null : WhatsAppRules.retryAt(attempts, now), now);
            log.warn("WhatsApp message {} ({}) attempt {} failed: {}", m.getId(), m.getType(), attempts, error);
        }
        return messages.save(m);
    }

    private WhatsAppClient client(WhatsAppSettingsService.Config c) {
        return c.live() ? meta : fake;
    }

    private static String truncate(String text) {
        return text == null ? null : text.length() <= 500 ? text : text.substring(0, 497) + "...";
    }
}
