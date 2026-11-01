package lk.thuhina.water.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import lk.thuhina.water.common.SecretCipher;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
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
    void dueByAndOldestUnpaidUseTheAgingFifo() {
        List<WhatsAppRules.Charge> charges = List.of(new WhatsAppRules.Charge(TODAY.minusDays(40), new BigDecimal("1000")),
                new WhatsAppRules.Charge(TODAY.minusDays(20), new BigDecimal("700")),
                new WhatsAppRules.Charge(TODAY.minusDays(10), new BigDecimal("-200")),   // a reversal: a credit
                new WhatsAppRules.Charge(TODAY.minusDays(5), new BigDecimal("500")));
        // Credits 600 + 200 pay the oldest bill first: 200 of it left.
        assertThat(WhatsAppRules.dueBy(charges, new BigDecimal("600"), TODAY.minusDays(30))).isEqualByComparingTo("200");
        assertThat(WhatsAppRules.dueBy(charges, new BigDecimal("600"), TODAY.minusDays(20))).isEqualByComparingTo("900");
        assertThat(WhatsAppRules.dueBy(charges, new BigDecimal("600"), TODAY)).isEqualByComparingTo("1400");
        assertThat(WhatsAppRules.dueBy(charges, new BigDecimal("600"), TODAY.minusDays(50))).isEqualByComparingTo("0");
        assertThat(WhatsAppRules.dueBy(charges, new BigDecimal("5000"), TODAY)).as("paid in advance").isEqualByComparingTo("0");
        assertThat(WhatsAppRules.oldestUnpaid(charges, new BigDecimal("600"))).isEqualTo(TODAY.minusDays(40));
        assertThat(WhatsAppRules.oldestUnpaid(charges, new BigDecimal("800"))).isEqualTo(TODAY.minusDays(20));
        assertThat(WhatsAppRules.oldestUnpaid(charges, new BigDecimal("2000"))).isNull();
        assertThat(WhatsAppRules.dueDate(LocalDate.of(2026, 9, 15), 30)).isEqualTo(LocalDate.of(2026, 10, 15));
    }

    @Test
    void reminderHourAndRecentBills() {
        assertThat(WhatsAppRules.isReminderHour(9, LocalDateTime.of(2026, 11, 2, 9, 0))).isTrue();
        assertThat(WhatsAppRules.isReminderHour(9, LocalDateTime.of(2026, 11, 3, 9, 59))).as("every day").isTrue();
        assertThat(WhatsAppRules.isReminderHour(9, LocalDateTime.of(2026, 11, 2, 10, 0))).isFalse();
        assertThat(WhatsAppRules.recent(TODAY, TODAY)).isTrue();
        assertThat(WhatsAppRules.recent(TODAY.minusDays(1), TODAY)).isTrue();
        assertThat(WhatsAppRules.recent(TODAY.minusDays(2), TODAY)).as("late entry").isFalse();
    }

    @Test
    void messageTexts() {
        assertThat(WhatsAppRules.bottlesText(List.of(new WhatsAppRules.Left(2, new BigDecimal("20.00")),
                new WhatsAppRules.Left(1, new BigDecimal("0.50")), new WhatsAppRules.Left(0, new BigDecimal("10.00")))))
                .isEqualTo("2 × 20L, 1 × 0.5L");
        assertThat(WhatsAppRules.bottlesText(List.of(new WhatsAppRules.Left(0, new BigDecimal("20.00"))))).isEqualTo("none");
        assertThat(WhatsAppRules.monthLabel("2026-10")).isEqualTo("October 2026");
        assertThat(WhatsAppRules.balanceText(new BigDecimal("1200"))).isEqualTo("Balance now: Rs. 1,200.00 to pay");
        assertThat(WhatsAppRules.balanceText(new BigDecimal("-500"))).isEqualTo("Balance now: Rs. 500.00 paid in advance");
        assertThat(WhatsAppRules.balanceText(BigDecimal.ZERO)).isEqualTo("Balance now: nothing to pay");
        assertThat(WhatsAppRules.billOpening(true)).startsWith("nobody was home");
        assertThat(WhatsAppRules.billOpening(false)).isEqualTo("here is the bill for your delivery");
        assertThat(WhatsAppRules.deliveryDay(DayOfWeek.MONDAY, 1)).isEqualTo("Monday");
        assertThat(WhatsAppRules.deliveryDay(DayOfWeek.FRIDAY, 2)).isEqualTo("Friday (every 2 weeks)");
        assertThat(WhatsAppRules.local("94771234567")).isEqualTo("077 123 4567");
    }

    /** Meta refuses a template whose text starts or ends with a parameter, or has two parameters side by side. */
    @Test
    void templateBodiesFollowMetaRules() {
        for (MessageType t : MessageType.values()) {
            String body = t.body();
            assertThat(body).as(t.name()).doesNotStartWith("{{").doesNotEndWith("}}").doesNotContain("}}{{");
            for (int i = 1; i <= t.params().size(); i++) {
                assertThat(body).as(t.name() + " {{" + i + "}}").contains("{{" + i + "}}");
            }
            assertThat(body).as(t.name()).doesNotContain("{{" + (t.params().size() + 1) + "}}");
            assertThat(t.template()).matches("[a-z0-9_]+").hasSizeLessThanOrEqualTo(60);
        }
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
