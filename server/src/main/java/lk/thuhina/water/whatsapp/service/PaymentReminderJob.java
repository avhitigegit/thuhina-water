package lk.thuhina.water.whatsapp.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.billing.rules.AgingRules.Aging;
import lk.thuhina.water.billing.service.AgingService;
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Weekly payment reminders (design 9.1; default Monday 09:00, set in the WhatsApp tab): Credit and Monthly bill customers who
 * opted in, have a balance and whose oldest unpaid charge is older than their terms – at most one reminder per customer in
 * 7 days. Parameters: customer name, balance, due date (oldest unpaid charge + terms).
 */
@Service
public class PaymentReminderJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReminderJob.class);

    private final WhatsAppSettingsService settings;
    private final WhatsAppOutbox outbox;
    private final WhatsAppMessageRepository messages;
    private final AgingService aging;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public PaymentReminderJob(WhatsAppSettingsService settings, WhatsAppOutbox outbox, WhatsAppMessageRepository messages, AgingService aging,
                              NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.settings = settings;
        this.outbox = outbox;
        this.messages = messages;
        this.aging = aging;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Every hour on the hour; runs only in the hour chosen in the settings. */
    @Scheduled(cron = "${app.whatsapp.reminder-cron:0 0 * * * *}", zone = "Asia/Colombo")
    public void hourly() {
        WhatsAppSettingsService.Config c = settings.config();
        if (WhatsAppRules.isReminderHour(c.reminderDay(), c.reminderHour(), LocalDateTime.now(clock.withZone(BusinessDates.ZONE)))) {
            ReminderRunResult r = run();
            log.info("Payment reminders: {}", r.message());
        }
    }

    /** Queues today's reminders (also "Send reminders now" in the WhatsApp tab). */
    @Transactional
    public ReminderRunResult run() {
        if (!outbox.active(MessageType.PAYMENT_REMINDER)) {
            return new ReminderRunResult(0, 0, "Payment reminders are switched off (or WhatsApp is off).");
        }
        LocalDate today = BusinessDates.today(clock);
        Instant weekAgo = Instant.now(clock).minus(WhatsAppRules.REMINDER_GAP_DAYS, ChronoUnit.DAYS);
        List<Map<String, Object>> candidates = jdbc.queryForList("""
                select c.id, c.name, c.terms_days, a.balance from customer c join customer_account a on a.customer_id = c.id
                where c.status = 'ACTIVE' and c.pay_type in ('CREDIT', 'MONTHLY_BILL') and c.whatsapp_opt_in and a.balance > 0
                order by c.id""", Map.of());
        int queued = 0;
        int skipped = 0;
        for (Map<String, Object> row : candidates) {
            long id = ((Number) row.get("id")).longValue();
            if (messages.existsByCustomerIdAndTypeAndCreatedAtAfterAndStatusNot(id, MessageType.PAYMENT_REMINDER, weekAgo, MessageStatus.SKIPPED)) {
                continue;
            }
            Integer terms = row.get("terms_days") == null ? null : ((Number) row.get("terms_days")).intValue();
            Aging a = aging.aging(id, today);
            if (!WhatsAppRules.reminderDue(a.total(), a.oldest(), terms, today)) {
                continue;
            }
            BigDecimal balance = a.total();
            WhatsAppMessage m = outbox.enqueue(MessageType.PAYMENT_REMINDER, id, List.of((String) row.get("name"), Money.format(balance),
                    BusinessDates.format(WhatsAppRules.dueDate(a.oldest(), terms))), "CUSTOMER", id).orElse(null);
            if (m == null) {
                continue;
            }
            if (m.getStatus() == MessageStatus.SKIPPED) {
                skipped++;
            } else {
                queued++;
            }
        }
        return new ReminderRunResult(queued, skipped, queued + " payment reminder(s) queued" + (skipped > 0 ? ", " + skipped
                + " skipped (no valid WhatsApp number)" : "") + ".");
    }
}
