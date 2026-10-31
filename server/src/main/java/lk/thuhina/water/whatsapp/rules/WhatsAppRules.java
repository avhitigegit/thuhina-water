package lk.thuhina.water.whatsapp.rules;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lk.thuhina.water.whatsapp.model.MessageStatus;

/** WhatsApp rules without the database (design 9.2) – numbers, retries, status order, webhook signature, reminders. */
public final class WhatsAppRules {

    /** After the 3rd failed attempt the message is FAILED. */
    public static final int MAX_ATTEMPTS = 3;
    /** Wait before the 2nd and the 3rd attempt. */
    private static final List<Duration> BACK_OFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
    /** At most one payment reminder per customer in this many days. */
    public static final int REMINDER_GAP_DAYS = 7;
    /** Left at door notices only for bills dated today or yesterday – a late entry of an old bill sends nothing. */
    public static final int LEFT_AT_DOOR_MAX_AGE_DAYS = 1;
    public static final int DEFAULT_TERMS_DAYS = 30;

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private WhatsAppRules() {
    }

    /**
     * The international number Meta needs (digits only, Sri Lanka 94…) for a mobile number as entered
     * ({@code 077 123 4567}, {@code +94 77 123 4567}, {@code 0094771234567}); null when it is not a Sri Lankan mobile number.
     */
    public static String toInternational(String number) {
        if (number == null) {
            return null;
        }
        String d = number.replaceAll("\\D", "");
        if (d.startsWith("0094")) {
            d = d.substring(2);
        }
        if (d.length() == 10 && d.startsWith("07")) {
            d = "94" + d.substring(1);
        }
        return d.length() == 11 && d.startsWith("947") ? d : null;
    }

    /** {@code 94771234567} → {@code +94 77 123 4567} for the log. */
    public static String display(String international) {
        if (international == null || international.length() != 11) {
            return international;
        }
        return "+" + international.substring(0, 2) + " " + international.substring(2, 4) + " " + international.substring(4, 7) + " "
                + international.substring(7);
    }

    /** When to try again after a failed attempt ({@code attempts} = failed attempts so far, this one included); null = FAILED. */
    public static Instant retryAt(int attempts, Instant now) {
        if (attempts >= MAX_ATTEMPTS) {
            return null;
        }
        return now.plus(BACK_OFF.get(Math.max(0, attempts - 1)));
    }

    /**
     * True when a status reported by Meta may replace the current one: forward only (SENT → DELIVERED → READ); FAILED replaces
     * SENT / DELIVERED; nothing replaces READ, FAILED or SKIPPED.
     */
    public static boolean canMoveTo(MessageStatus current, MessageStatus reported) {
        if (current == MessageStatus.READ || current == MessageStatus.FAILED || current == MessageStatus.SKIPPED) {
            return false;
        }
        if (reported == MessageStatus.FAILED) {
            return true;
        }
        return reported.rank() > current.rank() && reported != MessageStatus.SKIPPED;
    }

    /** Meta's status word → our status; null for words we do not track. */
    public static MessageStatus fromMeta(String status) {
        if (status == null) {
            return null;
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            case "failed" -> MessageStatus.FAILED;
            default -> null;
        };
    }

    /** Checks Meta's {@code X-Hub-Signature-256: sha256=<hex>} header: HMAC SHA-256 of the raw body with the app secret. */
    public static boolean signatureValid(String appSecret, byte[] body, String header) {
        if (appSecret == null || appSecret.isBlank() || header == null || !header.startsWith("sha256=") || body == null) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] given = HexFormat.of().parseHex(header.substring("sha256=".length()).toLowerCase(Locale.ROOT));
            return MessageDigest.isEqual(expected, given);
        } catch (IllegalArgumentException e) {
            return false;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** For tests and the webhook check: the header value Meta would send for this body. */
    public static String signature(String appSecret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The day the oldest unpaid charge fell due ({@code oldest} + terms); null when nothing is unpaid. */
    public static LocalDate dueDate(LocalDate oldestUnpaid, Integer termsDays) {
        if (oldestUnpaid == null) {
            return null;
        }
        return oldestUnpaid.plusDays(termsDays == null || termsDays < 0 ? DEFAULT_TERMS_DAYS : termsDays);
    }

    /** Design 9.1: a reminder is due when the balance is positive and the oldest unpaid charge is older than the terms. */
    public static boolean reminderDue(BigDecimal balance, LocalDate oldestUnpaid, Integer termsDays, LocalDate today) {
        LocalDate due = dueDate(oldestUnpaid, termsDays);
        return balance != null && balance.signum() > 0 && due != null && due.isBefore(today);
    }

    /** True when the weekly reminder run belongs to this hour ({@code day} 1 = Monday … 7 = Sunday). */
    public static boolean isReminderHour(int day, int hour, LocalDateTime now) {
        return now.getDayOfWeek().getValue() == day && now.getHour() == hour;
    }

    /** True for a Left at door bill recent enough to tell the customer about. */
    public static boolean leftAtDoorRecent(LocalDate billDate, LocalDate today) {
        return !billDate.isBefore(today.minusDays(LEFT_AT_DOOR_MAX_AGE_DAYS)) && !billDate.isAfter(today);
    }

    /** One bottle type left at the door. */
    public record Left(int qty, BigDecimal litres) {
    }

    /** {@code 2 × 20L, 1 × 10L}. */
    public static String bottlesText(List<Left> left) {
        return left.stream().filter(l -> l.qty() > 0)
                .map(l -> l.qty() + " × " + l.litres().stripTrailingZeros().toPlainString() + "L")
                .collect(Collectors.joining(", "));
    }

    /** {@code 2026-10} → {@code October 2026}. */
    public static String monthLabel(String period) {
        return YearMonth.parse(period).format(MONTH);
    }
}
