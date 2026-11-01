package lk.thuhina.water.whatsapp.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.ReminderRunResult;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.model.WhatsAppMessage;
import lk.thuhina.water.whatsapp.repository.WhatsAppMessageRepository;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily payment messages (FR-61; default 09:00, set in the WhatsApp tab) for Credit and Monthly bill customers who opted in
 * and owe money:
 * <ul>
 * <li><b>Payment due soon</b> – {@code daysBefore} (default 3) days before a due date: Monthly = an invoice's due date, the
 * amount still unpaid on it; Credit = bills of (due date − terms) not paid yet, the amount to pay by then.</li>
 * <li><b>Payment overdue</b> – {@code daysAfter} (default 3) days after the due date when still unpaid: the amount overdue and
 * the oldest due date.</li>
 * </ul>
 * Credits pay the oldest charges first (the aging FIFO). At most one of each per customer in 7 days, so an unpaid account
 * hears once a week.
 */
@Service
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);

    private final WhatsAppSettingsService settings;
    private final WhatsAppOutbox outbox;
    private final WhatsAppMessageRepository messages;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public PaymentReminderJob(WhatsAppSettingsService settings, WhatsAppOutbox outbox, WhatsAppMessageRepository messages,
                              NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.settings = settings;
        this.outbox = outbox;
        this.messages = messages;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Every hour on the hour; runs only in the hour chosen in the settings. */
    @Scheduled(cron = "${app.whatsapp.reminder-cron:0 0 * * * *}", zone = "Asia/Colombo")
    public void hourly() {
        WhatsAppSettingsService.Config c = settings.config();
        if (WhatsAppRules.isReminderHour(c.reminderHour(), LocalDateTime.now(clock.withZone(BusinessDates.ZONE)))) {
            ReminderRunResult r = run();
            log.info("Payment reminders: {}", r.message());
        }
    }

    /** One message to queue: type, amount, due date. */
    private record Due(MessageType type, BigDecimal amount, LocalDate date) {
    }

    /** Queues today's messages (also "Send reminders now" in the WhatsApp tab). */
    @Transactional
    public ReminderRunResult run() {
        return run(BusinessDates.today(clock));
    }

    /** Queues the messages due on {@code today} (tests use another day; invoices cannot be changed). */
    @Transactional
    public ReminderRunResult run(LocalDate today) {
        WhatsAppSettingsService.Config c = settings.config();
        boolean soonOn = outbox.active(MessageType.PAYMENT_DUE_SOON);
        boolean lateOn = outbox.active(MessageType.PAYMENT_OVERDUE);
        if (!soonOn && !lateOn) {
            return new ReminderRunResult(0, 0, "Payment due soon and overdue messages are switched off (or WhatsApp is off).");
        }
        Instant weekAgo = Instant.now(clock).minus(WhatsAppRules.REMINDER_GAP_DAYS, ChronoUnit.DAYS);
        List<Map<String, Object>> candidates = jdbc.queryForList("""
                select c.id, c.name, c.pay_type, c.terms_days from customer c join customer_account a on a.customer_id = c.id
                where c.status = 'ACTIVE' and c.pay_type in ('CREDIT', 'MONTHLY_BILL') and c.whatsapp_opt_in and a.balance > 0
                order by c.id""", Map.of());
        int soon = 0;
        int late = 0;
        int skipped = 0;
        for (Map<String, Object> row : candidates) {
            long id = ((Number) row.get("id")).longValue();
            MapSqlParameterSource p = new MapSqlParameterSource("c", id).addValue("asOf", today);
            List<WhatsAppRules.Charge> charges = jdbc.query("select txn_date, amount - paid_on_bill as net from sales_txn "
                            + "where customer_id = :c and txn_date <= :asOf order by txn_date, id", p,
                    (rs, i) -> new WhatsAppRules.Charge(rs.getObject("txn_date", LocalDate.class), rs.getBigDecimal("net")));
            BigDecimal paid = jdbc.queryForObject("select coalesce(sum(amount), 0) from customer_payment where customer_id = :c "
                    + "and pay_date <= :asOf", p, BigDecimal.class);
            List<Due> due = "MONTHLY_BILL".equals(row.get("pay_type"))
                    ? monthly(id, charges, paid, today, c.daysBefore(), c.daysAfter())
                    : credit(charges, paid, ((Number) row.get("terms_days")).intValue(), today, c.daysBefore(), c.daysAfter());
            for (Due d : due) {
                if (!(d.type() == MessageType.PAYMENT_DUE_SOON ? soonOn : lateOn)
                        || messages.existsByCustomerIdAndTypeAndCreatedAtAfterAndStatusNot(id, d.type(), weekAgo, MessageStatus.SKIPPED)) {
                    continue;
                }
                WhatsAppMessage m = outbox.enqueue(d.type(), id, List.of((String) row.get("name"), Money.format(d.amount()),
                        BusinessDates.format(d.date())), WhatsAppEvents.SOURCE_CUSTOMER, id).orElse(null);
                if (m == null) {
                    continue;
                }
                if (m.getStatus() == MessageStatus.SKIPPED) {
                    skipped++;
                } else if (d.type() == MessageType.PAYMENT_DUE_SOON) {
                    soon++;
                } else {
                    late++;
                }
            }
        }
        return new ReminderRunResult(soon + late, skipped, soon + " payment due soon and " + late + " payment overdue message(s) queued"
                + (skipped > 0 ? ", " + skipped + " skipped (no valid WhatsApp number)" : "") + ".");
    }

    /** Credit: bills fall due {@code terms} days after their date. */
    private static List<Due> credit(List<WhatsAppRules.Charge> charges, BigDecimal paid, int terms, LocalDate today, int before,
                                    int after) {
        List<Due> out = new ArrayList<>();
        LocalDate soonDate = today.plusDays(before);
        LocalDate soonCutoff = soonDate.minusDays(terms);
        BigDecimal dueThen = WhatsAppRules.dueBy(charges, paid, soonCutoff);
        // Something of the bills of that one day is still unpaid.
        if (dueThen.compareTo(WhatsAppRules.dueBy(charges, paid, soonCutoff.minusDays(1))) > 0) {
            out.add(new Due(MessageType.PAYMENT_DUE_SOON, dueThen, soonDate));
        }
        BigDecimal overdue = WhatsAppRules.dueBy(charges, paid, today.minusDays(after).minusDays(terms));
        LocalDate oldest = WhatsAppRules.oldestUnpaid(charges, paid);
        if (overdue.signum() > 0 && oldest != null) {
            out.add(new Due(MessageType.PAYMENT_OVERDUE, overdue, oldest.plusDays(terms)));
        }
        return out;
    }

    /** Monthly bill: an invoice falls due on its due date; what is unpaid of the charges up to its period end. */
    private List<Due> monthly(long customerId, List<WhatsAppRules.Charge> charges, BigDecimal paid, LocalDate today, int before,
                              int after) {
        List<Due> out = new ArrayList<>();
        List<Map<String, Object>> invoices = jdbc.queryForList("""
                select period_to, due_date from monthly_invoice where customer_id = :c and (due_date = :soon or due_date <= :late)
                order by due_date""", new MapSqlParameterSource("c", customerId).addValue("soon", today.plusDays(before))
                .addValue("late", today.minusDays(after)));
        LocalDate oldestDue = null;
        BigDecimal overdue = BigDecimal.ZERO;
        for (Map<String, Object> inv : invoices) {
            LocalDate periodTo = date(inv.get("period_to"));
            LocalDate dueDate = date(inv.get("due_date"));
            BigDecimal unpaid = WhatsAppRules.dueBy(charges, paid, periodTo);
            if (unpaid.signum() <= 0) {
                continue;
            }
            if (dueDate.equals(today.plusDays(before))) {
                out.add(new Due(MessageType.PAYMENT_DUE_SOON, unpaid, dueDate));
            } else {
                oldestDue = oldestDue == null ? dueDate : oldestDue;
                overdue = unpaid;
            }
        }
        if (oldestDue != null) {
            out.add(new Due(MessageType.PAYMENT_OVERDUE, overdue, oldestDue));
        }
        return out;
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }
}
