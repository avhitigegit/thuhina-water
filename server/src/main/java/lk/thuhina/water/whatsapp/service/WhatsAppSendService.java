package lk.thuhina.water.whatsapp.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.quotations.service.CustomerQuotationService;
import lk.thuhina.water.security.CurrentUser;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.QuotationSendRequest;
import lk.thuhina.water.whatsapp.dto.WhatsAppDtos.SendStatus;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.model.WhatsAppMessage;
import lk.thuhina.water.whatsapp.repository.WhatsAppMessageRepository;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * WhatsApp from the screens that act (FR-61): the delivery bill status and "Send again" on My Deliveries, and "Send on
 * WhatsApp" for a customer quotation (the quotation PDF; a draft is marked as sent by WhatsApp).
 */
@Service
public class WhatsAppSendService {

    public static final String NOT_SENDABLE = "WHATSAPP_NOT_SENDABLE";
    static final String MSG_BILL_OFF = "WhatsApp bills are switched off – give a paper bill.";
    static final String MSG_NO_WHATSAPP = "Customer has no WhatsApp – give a paper bill.";
    static final String MSG_QUOTATION_OFF = "WhatsApp or the quotation message is switched off (Administration → WhatsApp).";
    static final String MSG_AGREED = "Tick that the organisation agreed to get WhatsApp messages from us.";
    static final String MSG_NUMBER = "Enter a Sri Lankan mobile number, e.g. 077 123 4567.";

    private final WhatsAppOutbox outbox;
    private final WhatsAppEvents events;
    private final WhatsAppMessageRepository messages;
    private final CustomerQuotationService quotations;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public WhatsAppSendService(WhatsAppOutbox outbox, WhatsAppEvents events, WhatsAppMessageRepository messages,
                               CustomerQuotationService quotations, AuditService audit, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.outbox = outbox;
        this.events = events;
        this.messages = messages;
        this.quotations = quotations;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ delivery bill (My Deliveries)

    /** The WhatsApp of one of the user's own bills. */
    @Transactional(readOnly = true)
    public SendStatus billStatus(long txnId) {
        Map<String, Object> t = ownBill(txnId);
        String number = WhatsAppRules.toInternational((String) t.get("whatsapp_number"));
        boolean customerOk = Boolean.TRUE.equals(t.get("whatsapp_opt_in")) && number != null;
        boolean canSend = outbox.active(MessageType.DELIVERY_BILL) && customerOk && !Boolean.TRUE.equals(t.get("cancelled"));
        WhatsAppMessage m = messages.findFirstByTypeAndSourceTypeAndSourceIdOrderByIdDesc(MessageType.DELIVERY_BILL,
                WhatsAppEvents.SOURCE_BILL, txnId).orElse(null);
        if (m == null) {
            String text = !outbox.active(MessageType.DELIVERY_BILL) ? MSG_BILL_OFF : !customerOk ? MSG_NO_WHATSAPP
                    : "WhatsApp bill not sent yet.";
            return new SendStatus("NONE", text, number == null ? null : WhatsAppRules.local(number), canSend, null);
        }
        return status(m, "bill", canSend);
    }

    /** "Send again": queues the delivery bill message once more. */
    @Transactional
    public SendStatus resendBill(long txnId) {
        Map<String, Object> t = ownBill(txnId);
        if (Boolean.TRUE.equals(t.get("cancelled"))) {
            throw new BusinessException(NOT_SENDABLE, "This bill was cancelled.");
        }
        if (!outbox.active(MessageType.DELIVERY_BILL)) {
            throw new BusinessException(NOT_SENDABLE, MSG_BILL_OFF);
        }
        WhatsAppMessage m = events.queueBill(txnId).orElse(null);
        if (m == null || m.getStatus() == MessageStatus.SKIPPED) {
            throw new BusinessException(NOT_SENDABLE, MSG_NO_WHATSAPP);
        }
        audit.log(AuditAction.CREATE, "WhatsApp", (String) t.get("bill_no"), "Bill sent again on WhatsApp to "
                + WhatsAppRules.display(m.getToNumber()));
        return status(m, "bill", true);
    }

    /** A bill the current user saved (My Deliveries shows only their own). */
    private Map<String, Object> ownBill(long txnId) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select t.bill_no, t.created_by, t.reversed_by_id is not null as cancelled, c.whatsapp_opt_in, c.whatsapp_number
                from sales_txn t join customer c on c.id = t.customer_id where t.id = :t""", Map.of("t", txnId));
        if (rows.isEmpty() || !CurrentUser.usernameOrSystem().equals(rows.getFirst().get("created_by"))) {
            throw new NotFoundException("Bill", txnId);
        }
        return rows.getFirst();
    }

    // ------------------------------------------------------------------ quotation

    @Transactional(readOnly = true)
    public SendStatus quotationStatus(long id) {
        Map<String, Object> q = quotation(id);
        String number = WhatsAppRules.toInternational(defaultNumber(q));
        WhatsAppMessage m = messages.findFirstByTypeAndSourceTypeAndSourceIdOrderByIdDesc(MessageType.QUOTATION,
                WhatsAppEvents.SOURCE_QUOTATION, id).orElse(null);
        boolean canSend = outbox.active(MessageType.QUOTATION) && sendable(q);
        if (m == null) {
            return new SendStatus("NONE", outbox.active(MessageType.QUOTATION) ? "Not sent on WhatsApp yet." : MSG_QUOTATION_OFF,
                    number == null ? null : WhatsAppRules.local(number), canSend, null);
        }
        return status(m, "quotation", canSend);
    }

    /**
     * Sends the quotation PDF to the number given (default: the customer's WhatsApp number, else the quotation's phone). An
     * organisation that is not a customer yet must have agreed. A draft is marked as sent by WhatsApp; a sent one goes again.
     */
    @Transactional
    public SendStatus sendQuotation(long id, QuotationSendRequest r) {
        if (!outbox.active(MessageType.QUOTATION)) {
            throw new BusinessException(NOT_SENDABLE, MSG_QUOTATION_OFF);
        }
        Map<String, Object> q = quotation(id);
        if (!sendable(q)) {
            throw new BusinessException(NOT_SENDABLE, "Only a draft or sent quotation can be sent.");
        }
        String entered = Texts.blankToNull(r == null ? null : r.to());
        String to = WhatsAppRules.toInternational(entered != null ? entered : defaultNumber(q));
        if (to == null) {
            throw new ValidationException("to", MSG_NUMBER);
        }
        Long customerId = q.get("customer_id") == null ? null : ((Number) q.get("customer_id")).longValue();
        if (customerId == null && (r == null || !r.agreed())) {
            throw new ValidationException("agreed", MSG_AGREED);
        }
        LocalDate today = BusinessDates.today(clock);
        if ("DRAFT".equals(q.get("status"))) {
            quotations.send(id, "WHATSAPP", today);
        }
        String name = Texts.blankToNull((String) q.get("contact")) != null ? (String) q.get("contact") : (String) q.get("org_name");
        WhatsAppMessage m = outbox.enqueueTo(MessageType.QUOTATION, customerId, to, List.of(name, (String) q.get("cq_no"),
                BusinessDates.format(date(q.get("valid_until")))), WhatsAppEvents.SOURCE_QUOTATION, id).orElseThrow();
        audit.log(AuditAction.CREATE, "WhatsApp", (String) q.get("cq_no"), "Quotation sent on WhatsApp to " + WhatsAppRules.display(to));
        return status(m, "quotation", true);
    }

    private Map<String, Object> quotation(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select q.cq_no, q.status, q.customer_id, q.org_name, q.contact, q.phone, q.valid_until, c.whatsapp_number
                from customer_quotation q left join customer c on c.id = q.customer_id where q.id = :id""", Map.of("id", id));
        if (rows.isEmpty()) {
            throw new NotFoundException("Customer quotation", id);
        }
        return rows.getFirst();
    }

    private static boolean sendable(Map<String, Object> q) {
        return "DRAFT".equals(q.get("status")) || "SENT".equals(q.get("status"));
    }

    private static String defaultNumber(Map<String, Object> q) {
        String wa = (String) q.get("whatsapp_number");
        return WhatsAppRules.toInternational(wa) != null ? wa : (String) q.get("phone");
    }

    // ------------------------------------------------------------------

    private static SendStatus status(WhatsAppMessage m, String what, boolean canSend) {
        String to = WhatsAppRules.local(m.getToNumber());
        String text = switch (m.getStatus()) {
            case QUEUED -> "WhatsApp " + what + " is being sent to " + to + ".";
            case SENT -> "WhatsApp " + what + " sent to " + to + (m.isTestMode() ? " (test mode – written to the log only)." : ".");
            case DELIVERED -> "WhatsApp " + what + " delivered to " + to + ".";
            case READ -> "WhatsApp " + what + " read by " + to + ".";
            case FAILED -> "WhatsApp " + what + " to " + to + " failed: " + m.getError() + (what.equals("bill") ? " Give a paper bill." : "");
            case SKIPPED -> what.equals("bill") ? MSG_NO_WHATSAPP : "Not sent: " + m.getError();
        };
        return new SendStatus(m.getStatus().name(), text, m.getStatus() == MessageStatus.SKIPPED ? null : to, canSend, m.getId());
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }
}
