package lk.thuhina.water.purchasing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.PaymentTerms;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.partners.model.Supplier;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PaymentRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PaymentRow;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.SupplierBalance;
import lk.thuhina.water.purchasing.model.PurchaseOrder;
import lk.thuhina.water.purchasing.model.SupplierPayment;
import lk.thuhina.water.purchasing.repository.PurchaseOrderRepository;
import lk.thuhina.water.purchasing.repository.SupplierPaymentRepository;
import lk.thuhina.water.purchasing.rules.PurchasingRules;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Amounts owed to suppliers and payments to them (FR-21): owed = value of the good items received (GRN values) − payments.
 * The oldest unpaid receipt is found by paying the receipts oldest first; it is overdue when older than the supplier's terms.
 */
@Service
public class SupplierPaymentService {

    static final String ENTITY = "Supplier payment";

    private final SupplierPaymentRepository payments;
    private final PurchaseOrderRepository orders;
    private final PurchasingLookups lookups;
    private final NamedParameterJdbcTemplate jdbc;
    private final NumberingService numbering;
    private final AuditService audit;
    private final Clock clock;

    public SupplierPaymentService(SupplierPaymentRepository payments, PurchaseOrderRepository orders, PurchasingLookups lookups,
                                  NamedParameterJdbcTemplate jdbc, NumberingService numbering, AuditService audit, Clock clock) {
        this.payments = payments;
        this.orders = orders;
        this.lookups = lookups;
        this.jdbc = jdbc;
        this.numbering = numbering;
        this.audit = audit;
        this.clock = clock;
    }

    /** Suppliers with goods received or payments, by code. */
    @Transactional(readOnly = true)
    public List<SupplierBalance> balances() {
        Map<Long, BigDecimal> paid = new HashMap<>();
        jdbc.query("select supplier_id, sum(amount) as paid from supplier_payment group by supplier_id", rs -> {
            paid.put(rs.getLong("supplier_id"), rs.getBigDecimal("paid"));
        });
        // Receipts oldest first per supplier: the first one the payments do not cover is the oldest unpaid.
        Map<Long, BigDecimal> received = new HashMap<>();
        Map<Long, LocalDate> oldestUnpaid = new HashMap<>();
        jdbc.query("select supplier_id, received_date, value from goods_receipt order by supplier_id, received_date, id", rs -> {
            long sid = rs.getLong("supplier_id");
            BigDecimal cumulative = received.merge(sid, rs.getBigDecimal("value"), BigDecimal::add);
            if (!oldestUnpaid.containsKey(sid) && cumulative.compareTo(paid.getOrDefault(sid, BigDecimal.ZERO)) > 0) {
                oldestUnpaid.put(sid, rs.getDate("received_date").toLocalDate());
            }
        });
        LocalDate today = BusinessDates.today(clock);
        List<SupplierBalance> rows = new ArrayList<>();
        lookups.suppliers().values().stream()
                .filter(s -> received.containsKey(s.getId()) || paid.containsKey(s.getId()))
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .forEach(s -> {
                    BigDecimal in = Money.of(received.get(s.getId()));
                    BigDecimal out = Money.of(paid.get(s.getId()));
                    BigDecimal owed = in.subtract(out);
                    LocalDate oldest = owed.signum() > 0 ? oldestUnpaid.get(s.getId()) : null;
                    Integer days = oldest == null ? null : (int) ChronoUnit.DAYS.between(oldest, today);
                    rows.add(new SupplierBalance(s.getId(), s.getCode(), s.getName(), s.getTermsDays(), PaymentTerms.label(s.getTermsDays()),
                            in, out, owed, oldest, days, days != null && days > s.getTermsDays()));
                });
        return rows;
    }

    /** Owed per supplier id (only suppliers with receipts or payments) – the "Owed" column of the Suppliers tab. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> owedBySupplier() {
        Map<Long, BigDecimal> owed = new HashMap<>();
        jdbc.query("select supplier_id, sum(v) as owed from (select supplier_id, value as v from goods_receipt "
                + "union all select supplier_id, -amount from supplier_payment) x group by supplier_id", rs -> {
            owed.put(rs.getLong("supplier_id"), Money.of(rs.getBigDecimal("owed")));
        });
        return owed;
    }

    /** Payments newest first, optionally of one supplier. */
    @Transactional(readOnly = true)
    public List<PaymentRow> list(Long supplierId) {
        List<SupplierPayment> list = supplierId == null ? payments.findAllByOrderByPayDateDescIdDesc()
                : payments.findBySupplierIdOrderByPayDateDescIdDesc(supplierId);
        Map<Long, Supplier> suppliers = lookups.suppliers();
        Map<Long, String> poNos = new HashMap<>();
        orders.findAllById(list.stream().map(SupplierPayment::getPoId).filter(id -> id != null).distinct().toList())
                .forEach(p -> poNos.put(p.getId(), p.getPoNo()));
        Map<String, String> names = lookups.userNames(list.stream().map(SupplierPayment::getCreatedBy).toList());
        return list.stream().map(p -> row(p, suppliers.get(p.getSupplierId()), poNos.get(p.getPoId()), names)).toList();
    }

    @Transactional
    public PaymentRow create(PaymentRequest r) {
        Supplier s = r.supplierId() == null ? null : lookups.suppliers().get(r.supplierId());
        if (s == null) {
            throw new ValidationException("supplierId", "Select a supplier.");
        }
        QuotationService.checkNotFuture("date", r.date(), BusinessDates.today(clock));
        if (r.amount() == null || Money.of(r.amount()).signum() <= 0) {
            throw new ValidationException("amount", "Enter the amount paid.");
        }
        if (r.method() == null) {
            throw new ValidationException("method", "Select the payment method.");
        }
        String reference = Texts.blankToNull(r.reference());
        if (PurchasingRules.referenceMissing(r.method(), reference)) {
            throw new ValidationException("reference", PurchasingRules.MSG_REFERENCE);
        }
        PurchaseOrder po = null;
        if (r.poId() != null) {
            po = orders.findById(r.poId()).orElseThrow(() -> new ValidationException("poId", "Select a purchase order of this supplier."));
            if (!po.getSupplierId().equals(s.getId())) {
                throw new ValidationException("poId", po.getPoNo() + " is not from " + s.getName() + ".");
            }
        }
        BigDecimal amount = Money.of(r.amount());
        SupplierPayment p = payments.saveAndFlush(new SupplierPayment(numbering.next(SequenceNames.SUPPLIER_PAYMENT), s.getId(),
                po == null ? null : po.getId(), r.date(), amount, r.method(), reference, Texts.blankToNull(r.note())));
        audit.log(AuditAction.CREATE, ENTITY, p.getSpNo(), s.getName() + ": " + Money.format(amount) + " by " + r.method().label()
                + (reference == null ? "" : " (" + reference + ")") + (po == null ? "" : " for " + po.getPoNo()));
        return row(p, s, po == null ? null : po.getPoNo(), lookups.userNames(List.of(p.getCreatedBy())));
    }

    private static PaymentRow row(SupplierPayment p, Supplier s, String poNo, Map<String, String> names) {
        return new PaymentRow(p.getId(), p.getSpNo(), p.getPayDate(), PurchasingLookups.link(s), p.getPoId(), poNo, p.getMethod().name(),
                p.getMethod().label(), p.getReference(), p.getAmount(), p.getNote(), names.getOrDefault(p.getCreatedBy(), p.getCreatedBy()));
    }
}
