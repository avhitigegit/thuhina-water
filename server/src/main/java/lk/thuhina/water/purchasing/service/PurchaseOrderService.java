package lk.thuhina.water.purchasing.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.ErrorCodes;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.PaymentTerms;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.ledger.model.Bucket;
import lk.thuhina.water.ledger.model.Effect;
import lk.thuhina.water.ledger.model.Posting;
import lk.thuhina.water.ledger.model.SourceType;
import lk.thuhina.water.ledger.service.LedgerService;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.partners.model.Supplier;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoHistoryRow;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ItemQty;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoDetail;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoLineInput;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoLineView;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoRow;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoUpdateRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReasonRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReceiptLineInput;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReceiptLineView;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReceiptRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReceiptResult;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReceiptView;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.SupplierInfo;
import lk.thuhina.water.purchasing.model.GoodsReceipt;
import lk.thuhina.water.purchasing.model.GrnLine;
import lk.thuhina.water.purchasing.model.PoLine;
import lk.thuhina.water.purchasing.model.PoStatus;
import lk.thuhina.water.purchasing.model.PurchaseOrder;
import lk.thuhina.water.purchasing.model.QrItem;
import lk.thuhina.water.purchasing.model.QrStatus;
import lk.thuhina.water.purchasing.model.Quotation;
import lk.thuhina.water.purchasing.model.QuotationRequest;
import lk.thuhina.water.purchasing.repository.GoodsReceiptRepository;
import lk.thuhina.water.purchasing.repository.PurchaseOrderRepository;
import lk.thuhina.water.purchasing.repository.QuotationRepository;
import lk.thuhina.water.purchasing.repository.QuotationRequestRepository;
import lk.thuhina.water.purchasing.rules.PurchasingRules;
import lk.thuhina.water.purchasing.service.PurchasingLookups.Item;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Purchase orders (FR-18 – FR-20, BR-10): made only from the chosen quotation, changed while Draft, then Approve → Mark as
 * sent → Receive goods, one step at a time. A goods receipt puts the good items into stock through the Ledger Engine
 * (bottles to Empty in store, products to product stock); damaged items are neither added nor paid for.
 */
@Service
public class PurchaseOrderService {

    public static final String NOT_CHOSEN = "PO_NOT_CHOSEN";
    public static final String PO_EXISTS = "PO_EXISTS";

    static final String ENTITY = "Purchase order";

    private final PurchaseOrderRepository orders;
    private final QuotationRepository quotations;
    private final QuotationRequestRepository requests;
    private final GoodsReceiptRepository receipts;
    private final PurchasingLookups lookups;
    private final LedgerService ledger;
    private final NumberingService numbering;
    private final AuditService audit;
    private final Clock clock;

    public PurchaseOrderService(PurchaseOrderRepository orders, QuotationRepository quotations, QuotationRequestRepository requests,
                                GoodsReceiptRepository receipts, PurchasingLookups lookups, LedgerService ledger,
                                NumberingService numbering, AuditService audit, Clock clock) {
        this.orders = orders;
        this.quotations = quotations;
        this.requests = requests;
        this.receipts = receipts;
        this.lookups = lookups;
        this.ledger = ledger;
        this.numbering = numbering;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ read

    /** POs newest first, optionally of one status, with received / ordered and the next step. */
    @Transactional(readOnly = true)
    public List<PoRow> list(String status) {
        List<PurchaseOrder> list = status == null || status.isBlank() ? orders.findAllByOrderByPoDateDescIdDesc()
                : orders.findByStatusOrderByPoDateDescIdDesc(poStatus(status));
        Map<Long, Supplier> suppliers = lookups.suppliers();
        Map<String, Item> items = lookups.items();
        Map<Long, String> qrNos = new HashMap<>();
        requests.findAllById(list.stream().map(PurchaseOrder::getQrId).collect(Collectors.toSet()))
                .forEach(r -> qrNos.put(r.getId(), r.getQrNo()));
        return list.stream().map(p -> new PoRow(p.getId(), p.getPoNo(), p.getPoDate(), p.getExpectedDate(),
                PurchasingLookups.link(suppliers.get(p.getSupplierId())), qrNos.get(p.getQrId()),
                p.getLines().stream().map(l -> new ItemQty(l.getItemCode(), l.getItemType().name(),
                        PurchasingLookups.name(items, l.getItemCode()), l.getQty())).toList(),
                p.total(), p.getLines().stream().mapToInt(PoLine::getReceivedQty).sum(), p.getLines().stream().mapToInt(PoLine::getQty).sum(),
                p.getStatus().name(), p.getStatus().label(), PurchasingRules.nextStep(p.getStatus()))).toList();
    }

    @Transactional(readOnly = true)
    public PoDetail detail(long id) {
        return detail(orders.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id)));
    }

    // ------------------------------------------------------------------ create (FR-18, BR-10)

    @Transactional
    public PoDetail createFromQuotation(long quotationId) {
        Quotation q = quotations.findById(quotationId)
                .orElseThrow(() -> new NotFoundException(QuotationService.ENTITY_QUOTATION, quotationId));
        if (!q.isSelected()) {
            throw new BusinessException(NOT_CHOSEN, "A purchase order can be created only from the chosen quotation.");
        }
        QuotationRequest qr = requests.lockById(q.getQrId())
                .orElseThrow(() -> new NotFoundException(QuotationService.ENTITY_REQUEST, q.getQrId()));
        orders.findFirstByQuotationIdAndStatusNot(q.getId(), PoStatus.CANCELLED).ifPresent(p -> {
            throw new BusinessException(PO_EXISTS, "Purchase order " + p.getPoNo() + " already exists for this quotation.",
                    Map.of("poId", p.getId(), "poNo", p.getPoNo()));
        });
        LocalDate today = BusinessDates.today(clock);
        PurchaseOrder po = new PurchaseOrder(numbering.next(SequenceNames.PURCHASE_ORDER), qr.getId(), q.getId(), q.getSupplierId(),
                today, today.plusDays(q.getDeliveryDays()));
        for (QrItem item : qr.getItems()) {
            BigDecimal price = q.priceOf(item.getItemCode());
            if (price != null) {
                po.addLine(item.getItemType(), item.getItemCode(), item.getQty(), price);
            }
        }
        po.moveTo(PoStatus.DRAFT, CurrentUser.usernameOrSystem(), Instant.now(clock), null);
        po = orders.saveAndFlush(po);
        qr.setStatus(QrStatus.CLOSED);
        requests.saveAndFlush(qr);
        Supplier s = lookups.suppliers().get(q.getSupplierId());
        audit.log(AuditAction.CREATE, ENTITY, po.getPoNo(), "From " + q.getQuoteNo() + " (" + s.getName() + ", " + qr.getQrNo() + ") – "
                + Money.format(po.total()) + ", Draft");
        return detail(po);
    }

    // ------------------------------------------------------------------ draft changes and steps (FR-19)

    /** Draft only: quantities and prices (qty 0 removes the line), expected delivery date, notes. */
    @Transactional
    public PoDetail update(long id, PoUpdateRequest r) {
        PurchaseOrder po = lock(id);
        Versions.check(r.version(), po.getVersion(), ENTITY + " " + po.getPoNo());
        if (po.getStatus() != PoStatus.DRAFT) {
            throw new BusinessException(ErrorCodes.INVALID_STATE, "Only Draft purchase orders can be edited.");
        }
        if (r.expectedDate().isBefore(po.getPoDate())) {
            throw new ValidationException("expectedDate", "Expected delivery cannot be before the PO date ("
                    + BusinessDates.format(po.getPoDate()) + ").");
        }
        Map<String, Item> items = lookups.items();
        Map<String, PoLineInput> changes = new LinkedHashMap<>();
        for (PoLineInput in : r.lines() == null ? List.<PoLineInput>of() : r.lines()) {
            if (in == null || in.itemCode() == null || in.itemCode().isBlank()) {
                continue;
            }
            String code = in.itemCode().trim();
            String name = PurchasingLookups.name(items, code);
            if (po.line(code) == null) {
                throw new ValidationException("lines", name + " is not on this purchase order.");
            }
            if (in.qty() == null || in.qty() < 0) {
                throw new ValidationException("lines", "Enter the quantity for " + name + ".");
            }
            if (in.qty() > 0 && (in.unitPrice() == null || in.unitPrice().signum() <= 0)) {
                throw new ValidationException("lines", "Enter the unit price for " + name + ".");
            }
            changes.put(code, in);
        }
        boolean anyLeft = po.getLines().stream().anyMatch(l -> {
            PoLineInput in = changes.get(l.getItemCode());
            return in == null ? l.getQty() > 0 : in.qty() > 0;
        });
        if (!anyLeft) {
            throw new ValidationException("lines", "At least one line needs a quantity.");
        }

        BigDecimal before = po.total();
        List<String> what = new ArrayList<>();
        List<String> keep = new ArrayList<>();
        for (PoLine l : po.getLines()) {
            PoLineInput in = changes.get(l.getItemCode());
            String name = PurchasingLookups.name(items, l.getItemCode());
            if (in == null) {
                keep.add(l.getItemCode());
                continue;
            }
            if (in.qty() == 0) {
                what.add(name + " removed");
                continue;
            }
            keep.add(l.getItemCode());
            BigDecimal price = Money.of(in.unitPrice());
            if (in.qty() != l.getQty() || price.compareTo(l.getUnitPrice()) != 0) {
                what.add(name + " " + l.getQty() + " × " + Money.format(l.getUnitPrice()) + " → " + in.qty() + " × " + Money.format(price));
                l.change(in.qty(), price);
            }
        }
        po.removeLinesNotIn(keep);
        String notes = Texts.blankToNull(r.notes());
        if (!r.expectedDate().equals(po.getExpectedDate())) {
            what.add("expected " + BusinessDates.format(po.getExpectedDate()) + " → " + BusinessDates.format(r.expectedDate()));
        }
        if (!Objects.equals(notes, po.getNotes())) {
            what.add("notes changed");
        }
        if (what.isEmpty()) {
            return detail(po);
        }
        po.edit(r.expectedDate(), notes);
        orders.saveAndFlush(po);
        audit.log(AuditAction.UPDATE, ENTITY, po.getPoNo(), "Edited before approval: " + String.join("; ", what) + " – "
                + Money.format(before) + " → " + Money.format(po.total()));
        return detail(po);
    }

    @Transactional
    public PoDetail approve(long id) {
        PurchaseOrder po = lock(id);
        PurchasingRules.checkMove(po.getStatus(), PoStatus.APPROVED);
        String user = CurrentUser.usernameOrSystem();
        po.approved(user, BusinessDates.today(clock));
        po.moveTo(PoStatus.APPROVED, user, Instant.now(clock), null);
        orders.saveAndFlush(po);
        audit.log(AuditAction.APPROVE, ENTITY, po.getPoNo(), "Status → Approved – " + Money.format(po.total()));
        return detail(po);
    }

    @Transactional
    public PoDetail send(long id) {
        PurchaseOrder po = lock(id);
        PurchasingRules.checkMove(po.getStatus(), PoStatus.SENT);
        po.sent(BusinessDates.today(clock));
        po.moveTo(PoStatus.SENT, CurrentUser.usernameOrSystem(), Instant.now(clock), null);
        orders.saveAndFlush(po);
        audit.log(AuditAction.UPDATE, ENTITY, po.getPoNo(), "Status → Sent to " + lookups.suppliers().get(po.getSupplierId()).getName());
        return detail(po);
    }

    /** Only from Draft or Approved, with a reason. The chosen quotation can then be ordered again. */
    @Transactional
    public PoDetail cancel(long id, ReasonRequest r) {
        PurchaseOrder po = lock(id);
        String reason = r == null ? null : Texts.blankToNull(r.reason());
        if (reason == null) {
            throw new ValidationException("reason", "Enter the reason for cancelling.");
        }
        PurchasingRules.checkMove(po.getStatus(), PoStatus.CANCELLED);
        po.cancelled(reason);
        po.moveTo(PoStatus.CANCELLED, CurrentUser.usernameOrSystem(), Instant.now(clock), reason);
        orders.saveAndFlush(po);
        audit.log(AuditAction.UPDATE, ENTITY, po.getPoNo(), "Status → Cancelled – " + reason);
        return detail(po);
    }

    // ------------------------------------------------------------------ goods receipt (FR-20, BR-10)

    @Transactional
    public ReceiptResult receive(long id, ReceiptRequest r) {
        PurchaseOrder po = lock(id);
        if (!PurchasingRules.canReceive(po.getStatus())) {
            throw new BusinessException(ErrorCodes.INVALID_STATE, "Goods can be received only against an approved and sent PO. "
                    + po.getPoNo() + " is " + po.getStatus().label() + ".");
        }
        LocalDate today = BusinessDates.today(clock);
        QuotationService.checkNotFuture("date", r.date(), today);
        if (r.date().isBefore(po.getPoDate())) {
            throw new ValidationException("date", "Date received cannot be before the PO date (" + BusinessDates.format(po.getPoDate()) + ").");
        }
        Map<Long, ReceiptLineInput> inputs = new HashMap<>();
        Set<Long> lineIds = po.getLines().stream().map(PoLine::getId).collect(Collectors.toSet());
        for (ReceiptLineInput in : r.lines() == null ? List.<ReceiptLineInput>of() : r.lines()) {
            if (in == null || in.poLineId() == null) {
                continue;
            }
            if (!lineIds.contains(in.poLineId())) {
                throw new ValidationException("lines", "The line is not on " + po.getPoNo() + ".");
            }
            inputs.put(in.poLineId(), in);
        }
        Map<String, Item> items = lookups.items();
        boolean any = false;
        for (PoLine l : po.getLines()) {
            ReceiptLineInput in = inputs.get(l.getId());
            int received = in == null || in.received() == null ? 0 : in.received();
            int damaged = in == null || in.damaged() == null ? 0 : in.damaged();
            String problem = PurchasingRules.receiptLineProblem(PurchasingLookups.name(items, l.getItemCode()), l.stillDue(), received, damaged);
            if (problem != null) {
                throw new ValidationException("lines", problem);
            }
            any |= received > 0;
        }
        if (!any) {
            throw new ValidationException("lines", "Enter the quantities received.");
        }

        GoodsReceipt grn = new GoodsReceipt(numbering.next(SequenceNames.GOODS_RECEIPT), po.getId(), po.getSupplierId(), r.date(),
                Texts.blankToNull(r.deliveryNote()), Texts.blankToNull(r.note()));
        List<Effect> effects = new ArrayList<>();
        List<String> what = new ArrayList<>();
        for (PoLine l : po.getLines()) {
            ReceiptLineInput in = inputs.get(l.getId());
            int received = in == null || in.received() == null ? 0 : in.received();
            if (received == 0) {
                continue;
            }
            int damaged = in.damaged() == null ? 0 : in.damaged();
            grn.addLine(l.getId(), l.getItemType(), l.getItemCode(), received, damaged, l.getUnitPrice());
            l.receive(received, damaged);
            int good = received - damaged;
            effects.add(l.getItemType() == ItemType.BOTTLE ? Effect.stock(l.getItemCode(), Bucket.EMPTY, good)
                    : Effect.product(l.getItemCode(), good));
            what.add(received + " × " + PurchasingLookups.name(items, l.getItemCode()) + (damaged > 0 ? " (" + damaged + " damaged)" : ""));
        }
        grn = receipts.saveAndFlush(grn);
        ledger.post(new Posting(SourceType.GRN, grn.getId(), grn.getGrnNo(), r.date(), "Goods received – " + po.getPoNo(), effects));

        PoStatus next = PurchasingRules.afterReceipt(po.getLines().stream().map(l -> new int[] {l.getQty(), l.getReceivedQty()}).toList());
        PurchasingRules.checkMove(po.getStatus(), next);
        po.moveTo(next, CurrentUser.usernameOrSystem(), Instant.now(clock), grn.getGrnNo());
        orders.saveAndFlush(po);
        audit.log(AuditAction.CREATE, "Goods receipt", grn.getGrnNo(), po.getPoNo() + ": " + String.join(", ", what) + " – "
                + Money.format(grn.getValue()) + ", PO now " + next.label());
        Map<String, String> names = lookups.userNames(List.of(grn.getCreatedBy()));
        return new ReceiptResult(receiptView(grn, items, names), detail(po));
    }

    // ------------------------------------------------------------------ helpers

    private PurchaseOrder lock(long id) {
        return orders.lockById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
    }

    private PoDetail detail(PurchaseOrder po) {
        Map<String, Item> items = lookups.items();
        Supplier s = lookups.suppliers().get(po.getSupplierId());
        Quotation q = quotations.findById(po.getQuotationId()).orElseThrow();
        QuotationRequest qr = requests.findById(po.getQrId()).orElseThrow();
        List<GoodsReceipt> grns = receipts.findByPoIdOrderByIdAsc(po.getId());
        Set<String> users = new HashSet<>();
        users.add(po.getCreatedBy());
        users.add(po.getApprovedBy());
        po.getHistory().forEach(h -> users.add(h.getUsername()));
        grns.forEach(g -> users.add(g.getCreatedBy()));
        Map<String, String> names = lookups.userNames(users);

        List<PoLineView> lines = po.getLines().stream().map(l -> new PoLineView(l.getId(), l.getItemType().name(), l.getItemCode(),
                PurchasingLookups.name(items, l.getItemCode()), l.getQty(), l.getUnitPrice(), Money.times(l.getUnitPrice(), l.getQty()),
                l.getReceivedQty(), l.getDamagedQty(), l.stillDue())).toList();
        List<PoHistoryRow> history = po.getHistory().stream().map(h -> new PoHistoryRow(h.getStatus().name(), h.getStatus().label(), h.getTs(),
                h.getUsername(), names.get(h.getUsername()), h.getNote())).toList();
        List<ReceiptView> receiptViews = grns.stream().map(g -> receiptView(g, items, names)).toList();
        BigDecimal receivedValue = Money.of(grns.stream().map(GoodsReceipt::getValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        PoStatus st = po.getStatus();
        return new PoDetail(po.getId(), po.getPoNo(), po.getPoDate(), po.getExpectedDate(), st.name(), st.label(),
                PurchasingRules.nextStep(st), supplierInfo(s), qr.getId(), qr.getQrNo(), q.getId(), q.getQuoteNo(), q.getSupplierRef(),
                po.getNotes(), lines, po.total(), receivedValue, names.get(po.getCreatedBy()), names.get(po.getApprovedBy()),
                po.getApprovedOn(), po.getSentOn(), po.getCancelReason(), history, receiptViews, st == PoStatus.DRAFT,
                PurchasingRules.canCancel(st), PurchasingRules.canReceive(st), po.getVersion());
    }

    private static ReceiptView receiptView(GoodsReceipt g, Map<String, Item> items, Map<String, String> names) {
        List<ReceiptLineView> lines = g.getLines().stream().map((GrnLine l) -> new ReceiptLineView(l.getItemCode(),
                PurchasingLookups.name(items, l.getItemCode()), l.getReceived(), l.getDamaged(), l.good(), l.getUnitPrice(), l.value())).toList();
        return new ReceiptView(g.getId(), g.getGrnNo(), g.getReceivedDate(), g.getDeliveryNote(), g.getNote(), Money.of(g.getValue()),
                names.getOrDefault(g.getCreatedBy(), g.getCreatedBy()), lines);
    }

    static SupplierInfo supplierInfo(Supplier s) {
        return new SupplierInfo(s.getId(), s.getCode(), s.getName(), s.getContact(), s.getPhone(), s.getAddress(), s.getTermsDays(),
                PaymentTerms.label(s.getTermsDays()));
    }

    private static PoStatus poStatus(String status) {
        try {
            return PoStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("status", "Unknown status " + status + ".");
        }
    }
}
