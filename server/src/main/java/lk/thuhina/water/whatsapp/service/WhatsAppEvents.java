package lk.thuhina.water.whatsapp.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.billing.service.InvoiceGenerated;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.sales.service.LeftAtDoorBillSaved;
import lk.thuhina.water.whatsapp.model.MessageType;
import lk.thuhina.water.whatsapp.rules.WhatsAppRules;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Turns business events into queued messages, in the same transaction as the bill / invoice (design 9.2):
 * Left at door → customer name, bottles left, date, next delivery date; monthly invoice → customer name, month, total due,
 * due date (the PDF is attached when it is sent).
 */
@Component
public class WhatsAppEvents {

    public static final String SOURCE_BILL = "SALES_TXN";
    public static final String SOURCE_INVOICE = "INVOICE";

    private final WhatsAppOutbox outbox;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public WhatsAppEvents(WhatsAppOutbox outbox, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener
    public void leftAtDoor(LeftAtDoorBillSaved e) {
        if (!outbox.active(MessageType.LEFT_AT_DOOR) || !WhatsAppRules.leftAtDoorRecent(e.date(), BusinessDates.today(clock))) {
            return;
        }
        Map<String, Object> c = jdbc.queryForMap("select name, next_delivery_date from customer where id = :c", Map.of("c", e.customerId()));
        List<WhatsAppRules.Left> left = jdbc.query("""
                select l.filled, b.litres from sales_txn_line l join bottle_type b on b.code = l.bottle_type_code
                where l.txn_id = :t and l.filled > 0 order by l.line_no""", Map.of("t", e.txnId()),
                (rs, i) -> new WhatsAppRules.Left(rs.getInt("filled"), rs.getBigDecimal("litres")));
        LocalDate next = ((java.sql.Date) c.get("next_delivery_date")).toLocalDate();
        outbox.enqueue(MessageType.LEFT_AT_DOOR, e.customerId(), List.of((String) c.get("name"), WhatsAppRules.bottlesText(left),
                BusinessDates.format(e.date()), BusinessDates.format(next)), SOURCE_BILL, e.txnId());
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
                Money.format((BigDecimal) r.get("total_due")), BusinessDates.format(((java.sql.Date) r.get("due_date")).toLocalDate())),
                SOURCE_INVOICE, e.invoiceId());
    }
}
