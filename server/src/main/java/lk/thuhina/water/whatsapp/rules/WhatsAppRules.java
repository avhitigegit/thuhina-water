package lk.thuhina.water.whatsapp.rules;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import lk.thuhina.water.common.Money;
import lk.thuhina.water.whatsapp.model.MessageStatus;

/** WhatsApp rules without the database (design 9.2) – numbers, retries, status order, webhook signature, texts, reminders. */
public final class WhatsAppRules {

    /** After the 3rd failed attempt the message is FAILED. */
    public static final int MAX_ATTEMPTS = 3;
    /** Wait before the 2nd and the 3rd attempt. */
    private static final List<Duration> BACK_OFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5));
    /** At most one "due soon" and one "overdue" message per customer in this many days. */
    public static final int REMINDER_GAP_DAYS = 7;
    /** Bill, welcome and cancel messages only for things dated today or yesterday – a late entry of an old bill sends nothing. */
    public static final int RECENT_DAYS = 1;
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

    /** {@code 94771234567} → {@code 077 123 4567} for the screens. */
    public static String local(String international) {
        if (international == null || international.length() != 11 || !international.startsWith("94")) {
            return international;
        }
        return "0" + international.substring(2, 4) + " " + international.substring(4, 7) + " " + international.substring(7);
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

    /** One bill for {@link #dueBy}: its date and net (amount − paid on the bill; negative = a credit, e.g. a reversal). */
    public record Charge(LocalDate date, BigDecimal net) {
    }

    /**
     * What is still unpaid of the charges dated on or before {@code cutoff}: credits (payments and negative bills) pay the oldest
     * charges first (the FIFO of the aging, design 5.6). Credit customer: cutoff = due date − terms; monthly: the invoice's
     * period end.
     */
    public static BigDecimal dueBy(List<Charge> charges, BigDecimal payments, LocalDate cutoff) {
        BigDecimal charged = BigDecimal.ZERO;
        BigDecimal credit = payments == null ? BigDecimal.ZERO : payments;
        for (Charge c : charges) {
            if (c.net().signum() < 0) {
                credit = credit.add(c.net().negate());
            } else if (!c.date().isAfter(cutoff)) {
                charged = charged.add(c.net());
            }
        }
        return charged.subtract(credit).max(BigDecimal.ZERO);
    }

    /** The date of the oldest charge not fully paid (same FIFO as {@link #dueBy}); null when nothing is unpaid. */
    public static LocalDate oldestUnpaid(List<Charge> charges, BigDecimal payments) {
        BigDecimal credit = payments == null ? BigDecimal.ZERO : payments;
        for (Charge c : charges) {
            if (c.net().signum() < 0) {
                credit = credit.add(c.net().negate());
            }
        }
        BigDecimal charged = BigDecimal.ZERO;
        for (Charge c : charges.stream().filter(x -> x.net().signum() > 0).sorted(Comparator.comparing(Charge::date)).toList()) {
            charged = charged.add(c.net());
            if (charged.compareTo(credit) > 0) {
                return c.date();
            }
        }
        return null;
    }

    /** True when the daily reminder run belongs to this hour. */
    public static boolean isReminderHour(int hour, LocalDateTime now) {
        return now.getHour() == hour;
    }

    /** True for a bill / registration recent enough to tell the customer about (today or yesterday). */
    public static boolean recent(LocalDate date, LocalDate today) {
        return !date.isBefore(today.minusDays(RECENT_DAYS)) && !date.isAfter(today);
    }

    /** One bottle type and how many. */
    public record Left(int qty, BigDecimal litres) {
    }

    /** {@code 2 × 20L, 1 × 10L}; {@code none} when there are none. */
    public static String bottlesText(List<Left> left) {
        String text = left.stream().filter(l -> l.qty() > 0)
                .map(l -> l.qty() + " × " + l.litres().stripTrailingZeros().toPlainString() + "L")
                .collect(Collectors.joining(", "));
        return text.isEmpty() ? "none" : text;
    }

    /** The balance line of the bill / receipt / cancel messages. */
    public static String balanceText(BigDecimal balance) {
        if (balance == null || balance.signum() == 0) {
            return "Balance now: nothing to pay";
        }
        return balance.signum() > 0 ? "Balance now: " + Money.format(balance) + " to pay"
                : "Balance now: " + Money.format(balance.negate()) + " paid in advance";
    }

    /** Monthly bill customers pay on the invoice, so their delivery bill says so instead of the balance. */
    public static final String MONTHLY_PAYMENT_TEXT = "Payment: on your monthly invoice";

    /** The opening of the delivery bill message. */
    public static String billOpening(boolean leftAtDoor) {
        return leftAtDoor ? "nobody was home, so we left your water at the door. Here is the bill" : "here is the bill for your delivery";
    }

    /** {@code Monday} or {@code Monday (every 2 weeks)}. */
    public static String deliveryDay(DayOfWeek day, int cycleWeeks) {
        String name = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return cycleWeeks > 1 ? name + " (every " + cycleWeeks + " weeks)" : name;
    }

    /** {@code 2026-10} → {@code October 2026}. */
    public static String monthLabel(String period) {
        return YearMonth.parse(period).format(MONTH);
    }
}
