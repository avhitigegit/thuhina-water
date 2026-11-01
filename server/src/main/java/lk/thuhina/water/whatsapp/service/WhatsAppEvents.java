package lk.thuhina.water.whatsapp.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lk.thuhina.water.billing.service.InvoiceGenerated;
import lk.thuhina.water.billing.service.PaymentReceived;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.customers.service.CustomerRegistered;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.sales.service.BillReversed;
import lk.thuhina.water.sales.service.BillSaved;
import lk.thuhina.water.whatsapp.model.MessageStatus;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.model.WhatsAppMessage;
import lk.thuhina.water.whatsapp.repository.WhatsAppMessageRepository;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns business events into queued messages, in the same transaction as the bill / payment / invoice (FR-61, design 9.2):
 * welcome on registration; the delivery bill (PDF attached when sent) for every customer bill dated today or yesterday;
 * "bill cancelled" when a bill that went on WhatsApp is reversed; the receipt of a payment apart from a bill; the monthly
 * invoice (PDF attached). The parameters follow {@link MessageType#params()}.
 */
@Component
public class WhatsAppEvents {

    public static final String SOURCE_BILL = "SALES_TXN";
    public static final String SOURCE_INVOICE = "INVOICE";
    public static final String SOURCE_PAYMENT = "PAYMENT";
    public static final String SOURCE_CUSTOMER = "CUSTOMER";
    public static final String SOURCE_QUOTATION = "QUOTATION";

    private final WhatsAppOutbox outbox;
    private final WhatsAppMessageRepository messages;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public WhatsAppEvents(WhatsAppOutbox outbox, WhatsAppMessageRepository messages, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.outbox = outbox;
        this.messages = messages;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener
    public void registered(CustomerRegistered e) {
        if (!outbox.active(MessageType.WELCOME) || !WhatsAppRules.recent(e.registeredOn(), BusinessDates.today(clock))) {
            return;
        }
        Map<String, Object> c = jdbc.queryForMap("select code, name, delivery_day, cycle_weeks, next_delivery_date from customer where id = :c",
                Map.of("c", e.customerId()));
        outbox.enqueue(MessageType.WELCOME, e.customerId(), List.of((String) c.get("name"), (String) c.get("code"),
                WhatsAppRules.deliveryDay(DayOfWeek.of(((Number) c.get("delivery_day")).intValue()), ((Number) c.get("cycle_weeks")).intValue()),
                BusinessDates.format(date(c.get("next_delivery_date")))), SOURCE_CUSTOMER, e.customerId());
    }

    @EventListener
    public void billSaved(BillSaved e) {
        if (e.kind() == SalesKind.REVERSAL || e.kind() == SalesKind.OPENING_BALANCE
                || !WhatsAppRules.recent(e.date(), BusinessDates.today(clock))) {
            return;
        }
        queueBill(e.txnId());
    }

    /**
     * Queues the delivery bill message for one bill (also "Send again" on My Deliveries); empty when WhatsApp or the delivery
     * bill message is off or the customer has not opted in.
     */
    @Transactional
    public Optional<WhatsAppMessage> queueBill(long txnId) {
        if (!outbox.active(MessageType.DELIVERY_BILL)) {
            return Optional.empty();
        }
        Map<String, Object> t = jdbc.queryForMap("""
                select t.bill_no, t.kind, t.txn_date, t.amount, t.paid_on_bill, t.customer_id, c.name, c.pay_type, c.next_delivery_date,
                       coalesce(a.balance, 0) as balance
                from sales_txn t join customer c on c.id = t.customer_id left join customer_account a on a.customer_id = c.id
                where t.id = :t""", Map.of("t", txnId));
        List<Map<String, Object>> lines = jdbc.queryForList("""
                select l.filled, l.empties, b.litres from sales_txn_line l join bottle_type b on b.code = l.bottle_type_code
                where l.txn_id = :t order by l.line_no""", Map.of("t", txnId));
        List<WhatsAppRules.Left> left = lines.stream()
                .map(l -> new WhatsAppRules.Left(((Number) l.get("filled")).intValue(), (BigDecimal) l.get("litres"))).toList();
        List<WhatsAppRules.Left> empties = lines.stream()
                .map(l -> new WhatsAppRules.Left(((Number) l.get("empties")).intValue(), (BigDecimal) l.get("litres"))).toList();
        boolean monthly = "MONTHLY_BILL".equals(t.get("pay_type"));
        return outbox.enqueue(MessageType.DELIVERY_BILL, ((Number) t.get("customer_id")).longValue(), List.of(
                (String) t.get("name"),
                WhatsAppRules.billOpening(SalesKind.LEFT_AT_DOOR.name().equals(t.get("kind"))),
                (String) t.get("bill_no"),
                BusinessDates.format(date(t.get("txn_date"))),
                WhatsAppRules.bottlesText(left),
                WhatsAppRules.bottlesText(empties),
                Money.format((BigDecimal) t.get("amount")),
                Money.format((BigDecimal) t.get("paid_on_bill")),
                monthly ? WhatsAppRules.MONTHLY_PAYMENT_TEXT : WhatsAppRules.balanceText((BigDecimal) t.get("balance")),
                BusinessDates.format(date(t.get("next_delivery_date")))), SOURCE_BILL, txnId);
    }

    /** Only when the bill itself went on WhatsApp – otherwise the customer never heard of it. */
    @EventListener
    public void billReversed(BillReversed e) {
        if (!outbox.active(MessageType.BILL_CANCELLED)
                || !messages.existsByTypeAndSourceTypeAndSourceIdAndStatusNot(MessageType.DELIVERY_BILL, SOURCE_BILL, e.txnId(), MessageStatus.SKIPPED)) {
            return;
        }
        Map<String, Object> t = jdbc.queryForMap("""
                select t.bill_no, t.txn_date, t.amount, c.name, coalesce(a.balance, 0) as balance
                from sales_txn t join customer c on c.id = t.customer_id left join customer_account a on a.customer_id = c.id
                where t.id = :t""", Map.of("t", e.txnId()));
        outbox.enqueue(MessageType.BILL_CANCELLED, e.customerId(), List.of((String) t.get("name"), (String) t.get("bill_no"),
                BusinessDates.format(date(t.get("txn_date"))), Money.format((BigDecimal) t.get("amount")),
                WhatsAppRules.balanceText((BigDecimal) t.get("balance"))), SOURCE_BILL, e.reversalId());
    }

    @EventListener
    public void paymentReceived(PaymentReceived e) {
        if (!outbox.active(MessageType.PAYMENT_RECEIPT)) {
            return;
        }
        Map<String, Object> p = jdbc.queryForMap("""
                select p.receipt_no, p.pay_date, p.amount, p.method, c.name, coalesce(a.balance, 0) as balance
                from customer_payment p join customer c on c.id = p.customer_id left join customer_account a on a.customer_id = c.id
                where p.id = :p""", Map.of("p", e.paymentId()));
        outbox.enqueue(MessageType.PAYMENT_RECEIPT, e.customerId(), List.of((String) p.get("name"), Money.format((BigDecimal) p.get("amount")),
                PayMethod.valueOf((String) p.get("method")).label().toLowerCase(java.util.Locale.ROOT),
                BusinessDates.format(date(p.get("pay_date"))), (String) p.get("receipt_no"),
                WhatsAppRules.balanceText((BigDecimal) p.get("balance"))), SOURCE_PAYMENT, e.paymentId());
    }

    @EventListener
    public void invoice(InvoiceGenerated e) {
        if (!outbox.active(MessageType.MONTHLY_INVOICE)) {
            return;
        }
        Map<String, Object> r = jdbc.queryForMap("""
                select c.name, i.total_due, i.due_date from monthly_invoice i join customer c on c.id = i.customer_id
                where i.id = :i""", Map.of("i", e.invoiceId()));
        outbox.enqueue(MessageType.MONTHLY_INVOICE, e.customerId(), List.of((String) r.get("name"), WhatsAppRules.monthLabel(e.period()),
                Money.format((BigDecimal) r.get("total_due")), BusinessDates.format(date(r.get("due_date")))),
                SOURCE_INVOICE, e.invoiceId());
    }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : (LocalDate) value;
    }
}
