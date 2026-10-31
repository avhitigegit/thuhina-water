package lk.thuhina.water.whatsapp.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lk.thuhina.water.common.BaseEntity;
import org.hibernate.annotations.ColumnTransformer;

/**
 * One WhatsApp message in the outbox (design 4.3, 9.2). {@code params} is the JSON array of the template's body
 * parameters in order; {@code sourceType}/{@code sourceId} point at what caused it (the bill, the invoice – whose PDF
 * is attached when it is sent). {@code testMode} = handled by the test sender (no real credentials), nothing went to Meta.
 */
@Entity
@Table(name = "whatsapp_message")
public class WhatsAppMessage extends BaseEntity {

    @Column(name = "customer_id")
    private Long customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageType type;

    @Column(nullable = false, length = 60)
    private String template;

    @Column(name = "to_number", nullable = false, length = 20)
    private String toNumber;

    @Column(nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    private String params;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private MessageStatus status;

    @Column(name = "test_mode", nullable = false)
    private boolean testMode;

    @Column(name = "meta_message_id", length = 100)
    private String metaMessageId;

    @Column(length = 500)
    private String error;

    @Column(name = "source_type", length = 20)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "status_at")
    private Instant statusAt;

    protected WhatsAppMessage() {
    }

    public WhatsAppMessage(Long customerId, MessageType type, String toNumber, String params, String sourceType, Long sourceId,
                           Instant now) {
        this.customerId = customerId;
        this.type = type;
        this.template = type.template();
        this.toNumber = toNumber;
        this.params = params;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.status = MessageStatus.QUEUED;
        this.nextAttemptAt = now;
        this.statusAt = now;
    }

    /** Never sent; {@code reason} is shown in the message log. */
    public WhatsAppMessage skip(String reason, Instant now) {
        this.status = MessageStatus.SKIPPED;
        this.error = reason;
        this.nextAttemptAt = null;
        this.statusAt = now;
        return this;
    }

    public void sent(String metaMessageId, boolean testMode, Instant now) {
        this.status = MessageStatus.SENT;
        this.metaMessageId = metaMessageId;
        this.testMode = testMode;
        this.attempts++;
        this.error = null;
        this.nextAttemptAt = null;
        this.sentAt = now;
        this.statusAt = now;
    }

    /** A failed attempt: tried again at {@code nextAttempt}, or FAILED when {@code nextAttempt} is null. */
    public void attemptFailed(String error, Instant nextAttempt, Instant now) {
        this.attempts++;
        this.error = error;
        this.nextAttemptAt = nextAttempt;
        if (nextAttempt == null) {
            this.status = MessageStatus.FAILED;
            this.statusAt = now;
        }
    }

    /** Status reported by Meta through the webhook. */
    public void reported(MessageStatus status, String error, Instant now) {
        this.status = status;
        if (error != null) {
            this.error = error;
        }
        this.statusAt = now;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public MessageType getType() {
        return type;
    }

    public String getTemplate() {
        return template;
    }

    public String getToNumber() {
        return toNumber;
    }

    public String getParams() {
        return params;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public boolean isTestMode() {
        return testMode;
    }

    public String getMetaMessageId() {
        return metaMessageId;
    }

    public String getError() {
        return error;
    }

    public String getSourceType() {
        return sourceType;
    }

    public Long getSourceId() {
        return sourceId;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public Instant getStatusAt() {
        return statusAt;
    }
}
