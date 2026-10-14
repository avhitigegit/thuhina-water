package lk.thuhina.water.billing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentRequest;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentRow;
import lk.thuhina.water.billing.dto.BillingDtos.PaymentSaved;
import lk.thuhina.water.billing.model.CustomerPayment;
import lk.thuhina.water.billing.repository.CustomerPaymentRepository;
import lk.thuhina.water.billing.rules.PaymentRules;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.customers.model.Customer;
import lk.thuhina.water.customers.repository.CustomerRepository;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.sales.model.PayMethod;
import lk.thuhina.water.sales.service.AccountService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Receive a customer payment (FR-50, design 5.6): receipt number {@code RC-00001}, the balance before is stored, and the
 * account balance goes down in the same transaction (the account row is locked, so bills and payments for the same customer
 * wait for each other). Part payments and advances (more than owed) are allowed.
 */
@Service
public class PaymentService {

    private final CustomerPaymentRepository payments;
    private final CustomerRepository customers;
    private final AccountService accounts;
    private final BillingQueryService query;
    private final NumberingService numbering;
    private final AuditService audit;
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public PaymentService(CustomerPaymentRepository payments, CustomerRepository customers, AccountService accounts,
                          BillingQueryService query, NumberingService numbering, AuditService audit, NamedParameterJdbcTemplate jdbc,
                          Clock clock) {
        this.payments = payments;
        this.customers = customers;
        this.accounts = accounts;
        this.query = query;
        this.numbering = numbering;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public PaymentSaved receive(PaymentRequest r) {
        Customer c = r.customerId() == null ? null : customers.findById(r.customerId()).orElse(null);
        if (c == null) {
            throw new ValidationException("customerId", PaymentRules.MSG_CUSTOMER);
        }
        LocalDate today = BusinessDates.today(clock);
        if (r.date().isAfter(today)) {
            throw new ValidationException("date", "Date cannot be after today (" + BusinessDates.format(today) + ").");
        }
        if (r.amount() == null || Money.of(r.amount()).signum() <= 0) {
            throw new ValidationException("amount", PaymentRules.MSG_AMOUNT);
        }
        if (r.method() == null) {
            throw new ValidationException("method", PaymentRules.MSG_METHOD);
        }
        String reference = Texts.blankToNull(r.reference());
        String refProblem = PaymentRules.referenceProblem(r.method(), reference);
        if (refProblem != null) {
            throw new ValidationException("reference", refProblem);
        }
        if (r.invoiceId() != null) {
            Integer mine = jdbc.queryForObject("select count(*) from monthly_invoice where id = :id and customer_id = :c",
                    new MapSqlParameterSource("id", r.invoiceId()).addValue("c", c.getId()), Integer.class);
            if (mine == null || mine == 0) {
                throw new ValidationException("invoiceId", "Choose one of " + c.getName() + "'s invoices.");
            }
        }
        boolean cheque = r.method() == PayMethod.CHEQUE;
        BigDecimal amount = Money.of(r.amount());

        BigDecimal before = accounts.lockedBalance(c.getId());
        CustomerPayment p = payments.saveAndFlush(new CustomerPayment(numbering.next(SequenceNames.RECEIPT), c.getId(), r.date(), amount,
                r.method(), reference, cheque ? Texts.blankToNull(r.bank()) : null, cheque ? r.chequeDate() : null, r.invoiceId(),
                Texts.blankToNull(r.note()), before));
        accounts.add(c.getId(), amount.negate());
        BigDecimal after = before.subtract(amount);
        boolean part = PaymentRules.isPartPayment(amount, before);
        boolean advance = PaymentRules.isAdvance(amount, before);
        audit.log(AuditAction.CREATE, "Payment", p.getReceiptNo(), c.getName() + " (" + c.getCode() + "): " + Money.format(amount) + " by "
                + PaymentRules.how(r.method(), reference) + (part ? " – part payment" : "") + (advance ? " – advance" : ""));
        PaymentRow row = query.payment(p.getId());
        String message = "Payment saved – " + Money.format(amount) + " received from " + c.getName() + ". Balance now "
                + (after.signum() < 0 ? Money.format(after.negate()) + " in advance (credit balance)." : Money.format(after) + ".");
        return new PaymentSaved(row, after, part, advance, message);
    }
}
