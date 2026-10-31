package lk.thuhina.water.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lk.thuhina.water.common.SecretCipher;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.junit.jupiter.api.Test;

/** M13 rules (design 9.1, 9.2): numbers, retries, status order, webhook signature, reminders, texts, secret encryption. */
class WhatsAppRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 2);

    @Test
    void sriLankanMobileNumbersBecomeInternational() {
        assertThat(WhatsAppRules.toInternational("077 123 4567")).isEqualTo("94771234567");
        assertThat(WhatsAppRules.toInternational("0771234567")).isEqualTo("94771234567");
        assertThat(WhatsAppRules.toInternational("+94 77 123 4567")).isEqualTo("94771234567");
        assertThat(WhatsAppRules.toInternational("0094771234567")).isEqualTo("94771234567");
        assertThat(WhatsAppRules.toInternational("011 285 4471")).as("landline").isNull();
        assertThat(WhatsAppRules.toInternational("077 123 456")).as("too short").isNull();
        assertThat(WhatsAppRules.toInternational(null)).isNull();
        assertThat(WhatsAppRules.display("94771234567")).isEqualTo("+94 77 123 4567");
    }

    @Test
    void retriesAfterOneAndFiveMinutesThenFails() {
        Instant now = Instant.parse("2026-11-02T03:30:00Z");
        assertThat(WhatsAppRules.retryAt(1, now)).isEqualTo(now.plusSeconds(60));
        assertThat(WhatsAppRules.retryAt(2, now)).isEqualTo(now.plusSeconds(300));
        assertThat(WhatsAppRules.retryAt(3, now)).as("3rd failure = FAILED").isNull();
    }

    @Test
    void webhookStatusesOnlyMoveForward() {
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.SENT, MessageStatus.DELIVERED)).isTrue();
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.DELIVERED, MessageStatus.READ)).isTrue();
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.SENT, MessageStatus.READ)).as("read can arrive first").isTrue();
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.READ, MessageStatus.DELIVERED)).as("late delivered").isFalse();
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.DELIVERED, MessageStatus.SENT)).isFalse();
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.SENT, MessageStatus.FAILED)).isTrue();
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.FAILED, MessageStatus.DELIVERED)).isFalse();
        assertThat(WhatsAppRules.canMoveTo(MessageStatus.SKIPPED, MessageStatus.SENT)).isFalse();
        assertThat(WhatsAppRules.fromMeta("delivered")).isEqualTo(MessageStatus.DELIVERED);
        assertThat(WhatsAppRules.fromMeta("deleted")).isNull();
    }

    @Test
    void webhookSignatureIsHmacSha256OfTheBody() {
        byte[] body = "{\"entry\":[]}".getBytes(StandardCharsets.UTF_8);
        String header = WhatsAppRules.signature("app-secret", body);
        assertThat(header).startsWith("sha256=");
        assertThat(WhatsAppRules.signatureValid("app-secret", body, header)).isTrue();
        assertThat(WhatsAppRules.signatureValid("app-secret", body, header.toUpperCase().replace("SHA256=", "sha256="))).isTrue();
        assertThat(WhatsAppRules.signatureValid("other-secret", body, header)).isFalse();
        assertThat(WhatsAppRules.signatureValid("app-secret", "{\"entry\":[1]}".getBytes(StandardCharsets.UTF_8), header)).isFalse();
        assertThat(WhatsAppRules.signatureValid("app-secret", body, null)).isFalse();
        assertThat(WhatsAppRules.signatureValid("app-secret", body, "sha256=zz")).isFalse();
        assertThat(WhatsAppRules.signatureValid(null, body, header)).as("no app secret saved").isFalse();
    }

    @Test
    void reminderOnlyWhenTheOldestUnpaidChargeIsOlderThanTheTerms() {
        BigDecimal owed = new BigDecimal("2500.00");
        assertThat(WhatsAppRules.reminderDue(owed, TODAY.minusDays(31), 30, TODAY)).isTrue();
        assertThat(WhatsAppRules.reminderDue(owed, TODAY.minusDays(30), 30, TODAY)).as("due today – not overdue yet").isFalse();
        assertThat(WhatsAppRules.reminderDue(owed, TODAY.minusDays(8), 7, TODAY)).isTrue();
        assertThat(WhatsAppRules.reminderDue(owed, TODAY.minusDays(31), null, TODAY)).as("no terms = 30 days").isTrue();
        assertThat(WhatsAppRules.reminderDue(BigDecimal.ZERO, TODAY.minusDays(90), 30, TODAY)).isFalse();
        assertThat(WhatsAppRules.reminderDue(owed, null, 30, TODAY)).isFalse();
        assertThat(WhatsAppRules.dueDate(LocalDate.of(2026, 9, 15), 30)).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void reminderHourAndRecentLeftAtDoorBills() {
        assertThat(WhatsAppRules.isReminderHour(1, 9, LocalDateTime.of(2026, 11, 2, 9, 0))).as("Monday 09:00").isTrue();
        assertThat(WhatsAppRules.isReminderHour(1, 9, LocalDateTime.of(2026, 11, 2, 10, 0))).isFalse();
        assertThat(WhatsAppRules.isReminderHour(1, 9, LocalDateTime.of(2026, 11, 3, 9, 0))).as("Tuesday").isFalse();
        assertThat(WhatsAppRules.leftAtDoorRecent(TODAY, TODAY)).isTrue();
        assertThat(WhatsAppRules.leftAtDoorRecent(TODAY.minusDays(1), TODAY)).isTrue();
        assertThat(WhatsAppRules.leftAtDoorRecent(TODAY.minusDays(2), TODAY)).as("late entry").isFalse();
    }

    @Test
    void messageTexts() {
        assertThat(WhatsAppRules.bottlesText(List.of(new WhatsAppRules.Left(2, new BigDecimal("20.00")),
                new WhatsAppRules.Left(1, new BigDecimal("0.50")), new WhatsAppRules.Left(0, new BigDecimal("10.00")))))
                .isEqualTo("2 × 20L, 1 × 0.5L");
        assertThat(WhatsAppRules.monthLabel("2026-10")).isEqualTo("October 2026");
    }

    @Test
    void secretsAreEncryptedAndOnlyReadableWithTheSameKey() {
        SecretCipher cipher = new SecretCipher("key-one-0123456789");
        String stored = cipher.encrypt("EAAG-token");
        assertThat(stored).startsWith("v1:").doesNotContain("EAAG-token");
        assertThat(cipher.encrypt("EAAG-token")).as("random IV").isNotEqualTo(stored);
        assertThat(cipher.decrypt(stored)).isEqualTo("EAAG-token");
        assertThat(new SecretCipher("key-two-0123456789").decrypt(stored)).isNull();
        assertThat(new SecretCipher("").available()).isFalse();
    }
}
