package lk.thuhina.water.delivery.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import lk.thuhina.water.billing.dto.BillingDtos.CustomerPaymentRequest;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentSaved;
import lk.thuhina.water.billing.service.PaymentService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.delivery.dto.MobileDtos.MobilePaymentRequest;
import lk.thuhina.water.delivery.dto.MobileDtos.MyBottles;
import lk.thuhina.water.delivery.dto.MobileDtos.MyDay;
import lk.thuhina.water.delivery.dto.MobileDtos.MyEntry;
import lk.thuhina.water.delivery.dto.MobileDtos.MyMoney;
import lk.thuhina.water.ledger.rules.LedgerRules;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.model.SalesKind;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * My Deliveries on the phone (M17): money received without a delivery (a payment for today) and the delivery person's day –
 * what they saved today, the bottles left / collected and the money by Cash, Bank transfer and Cheque for the hand-over.
 * The delivery bills themselves are saved by {@code SalesService.postMobileBill}.
 */
@Service
public class MobileDeliveryService {

    private final PaymentService payments;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public MobileDeliveryService(PaymentService payments, NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.payments = payments;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public PaymentSaved receive(MobilePaymentRequest r) {
        return payments.receive(new CustomerPaymentRequest(r.customerId(), BusinessDates.today(clock), r.amount(), r.method(),
                r.reference(), r.bank(), r.chequeDate(), null, r.note()));
    }

    @Transactional(readOnly = true)
    public MyDay myDay() {
        LocalDate d = BusinessDates.today(clock);
        MapSqlParameterSource p = new MapSqlParameterSource("u", CurrentUser.usernameOrSystem()).addValue("d", d)
                .addValue("reversal", SalesKind.REVERSAL.name());
        List<MyEntry> entries = new ArrayList<>(jdbc.query("""
                select t.id, t.bill_no, t.kind, t.created_at, t.customer_id, c.code, coalesce(c.name, t.walk_in_name) as name,
                       t.amount, t.paid_on_bill, t.pay_method, t.pay_reference, t.reversed_by_id is not null as cancelled,
                       coalesce(sum(l.filled), 0) as filled, coalesce(sum(l.empties), 0) as empties
                from sales_txn t left join customer c on c.id = t.customer_id
                left join sales_txn_line l on l.txn_id = t.id and l.bottle_type_code is not null
                where t.created_by = :u and t.txn_date = :d and t.kind <> :reversal
                group by t.id, c.code, c.name""", p, (rs, i) -> {
            String m = rs.getString("pay_method");
            return new MyEntry("BILL", rs.getLong("id"), rs.getString("bill_no"), SalesKind.valueOf(rs.getString("kind")).label(),
                    rs.getTimestamp("created_at").toInstant(), (Long) rs.getObject("customer_id"), rs.getString("code"), rs.getString("name"),
                    rs.getInt("filled"), rs.getInt("empties"), Money.of(rs.getBigDecimal("amount")), Money.of(rs.getBigDecimal("paid_on_bill")),
                    m == null ? null : PayMethod.valueOf(m).label(), rs.getString("pay_reference"), rs.getBoolean("cancelled"));
        }));
        entries.addAll(jdbc.query("""
                select p.id, p.receipt_no, p.created_at, p.customer_id, c.code, c.name, p.amount, p.method, p.reference
                from customer_payment p join customer c on c.id = p.customer_id
                where p.created_by = :u and p.pay_date = :d""", p, (rs, i) -> new MyEntry("PAYMENT", rs.getLong("id"),
                rs.getString("receipt_no"), "Payment", rs.getTimestamp("created_at").toInstant(), rs.getLong("customer_id"), rs.getString("code"),
                rs.getString("name"), 0, 0, null, Money.of(rs.getBigDecimal("amount")), PayMethod.valueOf(rs.getString("method")).label(),
                rs.getString("reference"), false)));
        entries.sort(Comparator.comparing(MyEntry::at).reversed());

        List<MyBottles> bottles = jdbc.query("""
                select l.bottle_type_code, b.litres, sum(l.filled) as filled, sum(l.empties) as empties
                from sales_txn_line l join sales_txn t on t.id = l.txn_id join bottle_type b on b.code = l.bottle_type_code
                where t.created_by = :u and t.txn_date = :d and t.kind <> :reversal and t.reversed_by_id is null
                group by l.bottle_type_code, b.litres having sum(l.filled) + sum(l.empties) > 0
                order by b.litres, l.bottle_type_code""", p, (rs, i) -> new MyBottles(rs.getString("bottle_type_code"),
                LedgerRules.bottleLabel(rs.getBigDecimal("litres")), rs.getInt("filled"), rs.getInt("empties")));

        BigDecimal cash = Money.ZERO;
        BigDecimal bank = Money.ZERO;
        BigDecimal cheque = Money.ZERO;
        int bills = 0;
        int paid = 0;
        for (MyEntry e : entries) {
            if (e.cancelled()) {
                continue;
            }
            if ("BILL".equals(e.entry())) {
                bills++;
            } else {
                paid++;
            }
            if (e.received().signum() <= 0) {
                continue;
            }
            if (PayMethod.CHEQUE.label().equals(e.method())) {
                cheque = cheque.add(e.received());
            } else if (PayMethod.BANK_TRANSFER.label().equals(e.method())) {
                bank = bank.add(e.received());
            } else {
                cash = cash.add(e.received());
            }
        }
        return new MyDay(d, bills, paid, bottles, new MyMoney(Money.of(cash), Money.of(bank), Money.of(cheque),
                Money.of(cash.add(bank).add(cheque))), entries);
    }
}
