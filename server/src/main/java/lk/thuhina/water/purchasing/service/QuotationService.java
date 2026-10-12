package lk.thuhina.water.purchasing.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.BusinessException;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.storage.FileStorage;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.partners.model.Supplier;
import lk.thuhina.water.partners.repository.SupplierRepository;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.AskedSupplier;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.AttachmentResponse;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ItemQty;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PoLink;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.PriceInput;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QrDetail;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QrItemInput;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QrRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QrRow;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QuotationRequestBody;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QuoteColumn;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.QuotePrice;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.ReasonRequest;
import lk.thuhina.water.purchasing.dto.PurchasingDtos.SupplierLink;
import lk.thuhina.water.purchasing.model.PoStatus;
import lk.thuhina.water.purchasing.model.PurchaseOrder;
import lk.thuhina.water.purchasing.model.QrItem;
import lk.thuhina.water.purchasing.model.QrStatus;
import lk.thuhina.water.purchasing.model.Quotation;
import lk.thuhina.water.purchasing.model.QuotationRequest;
import lk.thuhina.water.purchasing.repository.PurchaseOrderRepository;
import lk.thuhina.water.purchasing.repository.QuotationRepository;
import lk.thuhina.water.purchasing.repository.QuotationRequestRepository;
import lk.thuhina.water.purchasing.rules.PurchasingRules;
import lk.thuhina.water.purchasing.service.PurchasingLookups.Item;
import lk.thuhina.water.security.CurrentUser;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Quotation requests and supplier quotations (FR-15 – FR-17): ask suppliers for prices, record what they quote (with an
 * optional scanned file), compare side by side and let the owner choose one with a reason. The chosen quotation becomes a
 * purchase order in {@link PurchaseOrderService}.
 */
@Service
public class QuotationService {

    public static final String QUOTATION_CHOSEN = "QUOTATION_CHOSEN";
    public static final String DUPLICATE_QUOTATION = "DUPLICATE_QUOTATION";
    public static final String QUOTATION_EXPIRED = "QUOTATION_EXPIRED";

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    public static final String MSG_FILE_TYPE = "Attach a PDF, JPG or PNG file.";
    public static final String MSG_FILE_SIZE = "File must be 5 MB or smaller.";

    static final String ENTITY_REQUEST = "Quotation request";
    static final String ENTITY_QUOTATION = "Quotation";

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    /** A stored scanned quotation. */
    public record Attachment(byte[] content, MediaType mediaType, String name) {
    }

    private final QuotationRequestRepository requests;
    private final QuotationRepository quotations;
    private final PurchaseOrderRepository orders;
    private final SupplierRepository supplierRepository;
    private final PurchasingLookups lookups;
    private final NumberingService numbering;
    private final AuditService audit;
    private final FileStorage storage;
    private final Clock clock;

    public QuotationService(QuotationRequestRepository requests, QuotationRepository quotations, PurchaseOrderRepository orders,
                            SupplierRepository supplierRepository, PurchasingLookups lookups, NumberingService numbering,
                            AuditService audit, FileStorage storage, Clock clock) {
        this.requests = requests;
        this.quotations = quotations;
        this.orders = orders;
        this.supplierRepository = supplierRepository;
        this.lookups = lookups;
        this.numbering = numbering;
        this.audit = audit;
        this.storage = storage;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ requests (FR-15)

    /** Requests newest first, optionally of one status, with "n of m suppliers", the chosen supplier and the PO. */
    @Transactional(readOnly = true)
    public List<QrRow> list(String status) {
        List<QuotationRequest> list = status == null || status.isBlank() ? requests.findAllByOrderByRequestDateDescIdDesc()
                : requests.findByStatusOrderByRequestDateDescIdDesc(qrStatus(status));
        if (list.isEmpty()) {
            return List.of();
        }
        List<Long> ids = list.stream().map(QuotationRequest::getId).toList();
        Map<Long, List<Quotation>> quotes = quotations.findByQrIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(Quotation::getQrId));
        Map<Long, PurchaseOrder> pos = latestPos(orders.findByQrIdIn(ids));
        Map<Long, Supplier> suppliers = lookups.suppliers();
        Map<String, Item> items = lookups.items();
        return list.stream().map(r -> {
            List<Quotation> qs = quotes.getOrDefault(r.getId(), List.of());
            SupplierLink chosen = qs.stream().filter(Quotation::isSelected).findFirst()
                    .map(q -> PurchasingLookups.link(suppliers.get(q.getSupplierId()))).orElse(null);
            return new QrRow(r.getId(), r.getQrNo(), r.getRequestDate(), r.getRequiredBy(), r.getNote(), r.getStatus().name(),
                    r.getStatus().label(), itemQtys(r, items), r.getSupplierIds().size(), qs.size(), chosen, poLink(pos.get(r.getId())));
        }).toList();
    }

    @Transactional(readOnly = true)
    public QrDetail detail(long id) {
        return detail(request(id));
    }

    /** Active suppliers of any of the items (B20, P04 …), by code – they are ticked for the user on a new request. */
    @Transactional(readOnly = true)
    public List<SupplierLink> suggestedSuppliers(List<String> itemCodes) {
        Set<String> wanted = itemCodes == null ? Set.of() : itemCodes.stream().filter(c -> c != null && !c.isBlank())
                .map(String::trim).collect(Collectors.toSet());
        return supplierRepository.findAllByOrderByCodeAsc().stream()
                .filter(Supplier::isActive)
                .filter(s -> s.getItems().stream().anyMatch(i -> wanted.contains(i.getItemCode())))
                .map(PurchasingLookups::link)
                .toList();
    }

    @Transactional
    public QrDetail create(QrRequest r) {
        LocalDate today = BusinessDates.today(clock);
        checkNotFuture("date", r.date(), today);
        if (r.requiredBy() != null && r.requiredBy().isBefore(r.date())) {
            throw new ValidationException("requiredBy", "Needed by cannot be before the request date.");
        }
        Map<String, Item> items = lookups.items();
        Map<String, Integer> wanted = new LinkedHashMap<>();
        for (QrItemInput in : r.items() == null ? List.<QrItemInput>of() : r.items()) {
            if (in == null || in.itemCode() == null || in.itemCode().isBlank() || in.qty() == null || in.qty() <= 0) {
                continue;
            }
            String code = in.itemCode().trim();
            Item item = items.get(code);
            if (item == null) {
                throw new ValidationException("items", "Unknown item " + code + ".");
            }
            if (!item.active()) {
                throw new ValidationException("items", item.name() + " is not active.");
            }
            if (wanted.putIfAbsent(code, in.qty()) != null) {
                throw new ValidationException("items", item.name() + " is listed twice.");
            }
        }
        if (wanted.isEmpty()) {
            throw new ValidationException("items", "Add at least one item with a quantity.");
        }
        Set<Long> supplierIds = new LinkedHashSet<>(r.supplierIds() == null ? List.of() : r.supplierIds().stream()
                .filter(x -> x != null).toList());
        if (supplierIds.isEmpty()) {
            throw new ValidationException("supplierIds", "Select the suppliers asked to quote.");
        }
        Map<Long, Supplier> suppliers = lookups.suppliers();
        for (Long sid : supplierIds) {
            Supplier s = suppliers.get(sid);
            if (s == null || !s.isActive()) {
                throw new ValidationException("supplierIds", "Select active suppliers only.");
            }
        }

        QuotationRequest qr = new QuotationRequest(numbering.next(SequenceNames.QUOTATION_REQUEST, r.date()), r.date(), r.requiredBy(),
                Texts.blankToNull(r.note()), supplierIds);
        wanted.forEach((code, qty) -> qr.addItem(items.get(code).type(), code, qty));
        QuotationRequest saved = requests.saveAndFlush(qr);
        audit.log(AuditAction.CREATE, ENTITY_REQUEST, saved.getQrNo(), itemText(saved, items) + " – " + supplierIds.size()
                + " supplier(s): " + supplierIds.stream().map(id -> suppliers.get(id).getName()).collect(Collectors.joining(", ")));
        return detail(saved);
    }

    // ------------------------------------------------------------------ quotations (FR-16)

    @Transactional
    public QrDetail addQuotation(long qrId, QuotationRequestBody r) {
        QuotationRequest qr = requests.lockById(qrId).orElseThrow(() -> new NotFoundException(ENTITY_REQUEST, qrId));
        checkOpen(qr);
        if (r.supplierId() == null) {
            throw new ValidationException("supplierId", "Select the supplier.");
        }
        if (!qr.getSupplierIds().contains(r.supplierId())) {
            throw new ValidationException("supplierId", "Select one of the suppliers asked to quote.");
        }
        Supplier supplier = supplierRepository.findById(r.supplierId()).orElseThrow(() -> new NotFoundException("Supplier", r.supplierId()));
        List<Quotation> existing = quotations.findByQrIdOrderByIdAsc(qr.getId());
        if (existing.stream().anyMatch(q -> q.getSupplierId().equals(r.supplierId()))) {
            throw new BusinessException(DUPLICATE_QUOTATION, supplier.getName() + " already has a quotation recorded for " + qr.getQrNo() + ".");
        }
        LocalDate today = BusinessDates.today(clock);
        checkNotFuture("date", r.date(), today);
        if (r.date().isBefore(qr.getRequestDate())) {
            throw new ValidationException("date", "Quotation date cannot be before the request date ("
                    + BusinessDates.format(qr.getRequestDate()) + ").");
        }
        if (r.validUntil() == null) {
            throw new ValidationException("validUntil", "Enter the validity date.");
        }
        if (r.validUntil().isBefore(r.date())) {
            throw new ValidationException("validUntil", "Valid until cannot be before the quotation date.");
        }
        if (r.deliveryDays() == null || r.deliveryDays() < 0) {
            throw new ValidationException("deliveryDays", "Enter the delivery time in days.");
        }
        if (r.deliveryDays() > 365) {
            throw new ValidationException("deliveryDays", "Delivery time: at most 365 days.");
        }
        Map<String, Item> items = lookups.items();
        Set<String> requested = qr.getItems().stream().map(QrItem::getItemCode).collect(Collectors.toSet());
        Map<String, BigDecimal> prices = new HashMap<>();
        for (PriceInput p : r.prices() == null ? List.<PriceInput>of() : r.prices()) {
            if (p == null || p.itemCode() == null || p.unitPrice() == null) {
                continue;
            }
            String code = p.itemCode().trim();
            if (!requested.contains(code)) {
                throw new ValidationException("prices", PurchasingLookups.name(items, code) + " is not on " + qr.getQrNo() + ".");
            }
            if (p.unitPrice().signum() <= 0) {
                throw new ValidationException("prices", "Unit price for " + PurchasingLookups.name(items, code) + " must be more than zero.");
            }
            prices.put(code, Money.of(p.unitPrice()));
        }
        if (prices.isEmpty()) {
            throw new ValidationException("prices", "Enter at least one unit price.");
        }

        Quotation q = new Quotation(numbering.next(SequenceNames.SUPPLIER_QUOTATION), qr.getId(), supplier.getId(),
                Texts.blankToNull(r.supplierRef()), r.date(), r.validUntil(), r.deliveryDays(), Texts.blankToNull(r.notes()));
        qr.getItems().forEach(i -> q.addLine(i.getItemCode(), prices.get(i.getItemCode())));
        quotations.saveAndFlush(q);
        qr.setStatus(QrStatus.QUOTES_RECEIVED);
        requests.saveAndFlush(qr);
        audit.log(AuditAction.CREATE, ENTITY_QUOTATION, q.getQuoteNo(), supplier.getName() + " for " + qr.getQrNo() + " – total "
                + Money.format(total(qr, q)) + (q.getSupplierRef() == null ? "" : " (their ref. " + q.getSupplierRef() + ")"));
        return detail(qr);
    }

    /** The owner's choice (FR-17): reason required, not when expired, one per request. */
    @Transactional
    public QrDetail choose(long quotationId, ReasonRequest r) {
        Quotation q = quotations.findById(quotationId).orElseThrow(() -> new NotFoundException(ENTITY_QUOTATION, quotationId));
        QuotationRequest qr = requests.lockById(q.getQrId()).orElseThrow(() -> new NotFoundException(ENTITY_REQUEST, q.getQrId()));
        checkOpen(qr);
        String reason = r == null ? null : Texts.blankToNull(r.reason());
        if (reason == null) {
            throw new ValidationException("reason", "Add the reason for choosing this quotation.");
        }
        LocalDate today = BusinessDates.today(clock);
        if (q.getValidUntil().isBefore(today)) {
            throw new BusinessException(QUOTATION_EXPIRED, "This quotation expired on " + BusinessDates.format(q.getValidUntil())
                    + ". Ask the supplier to re-quote.");
        }
        q.choose(reason, CurrentUser.usernameOrSystem(), today);
        quotations.saveAndFlush(q);
        qr.setStatus(QrStatus.SELECTED);
        requests.saveAndFlush(qr);
        Supplier s = supplierRepository.findById(q.getSupplierId()).orElseThrow();
        audit.log(AuditAction.APPROVE, ENTITY_QUOTATION, q.getQuoteNo(), "Chosen for " + qr.getQrNo() + ": " + s.getName() + " – "
                + Money.format(total(qr, q)) + " – " + reason);
        return detail(qr);
    }

    // ------------------------------------------------------------------ scanned quotation

    /** Stores the scanned quotation (PDF, JPG or PNG up to 5 MB; type checked from the content); replaces an earlier file. */
    @Transactional
    public AttachmentResponse attach(long quotationId, MultipartFile file) {
        Quotation q = quotations.findById(quotationId).orElseThrow(() -> new NotFoundException(ENTITY_QUOTATION, quotationId));
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file", "Choose a PDF, JPG or PNG file.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new ValidationException("file", MSG_FILE_SIZE);
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String ext = startsWith(content, PDF) ? "pdf" : startsWith(content, PNG) ? "png" : startsWith(content, JPG) ? "jpg" : null;
        if (ext == null) {
            throw new ValidationException("file", MSG_FILE_TYPE);
        }
        String key = "quotations/" + q.getQuoteNo().toLowerCase() + "-" + UUID.randomUUID() + "." + ext;
        storage.put(key, content);
        String old = q.getAttachmentPath();
        String name = fileName(file.getOriginalFilename(), q.getQuoteNo() + "." + ext);
        q.attach(key, name);
        quotations.saveAndFlush(q);
        if (old != null) {
            storage.delete(old);
        }
        long kb = Math.max(1, Math.round(content.length / 1024.0));
        audit.log(AuditAction.UPDATE, ENTITY_QUOTATION, q.getQuoteNo(), "Scanned quotation attached: " + name + " (" + kb + " KB)");
        return new AttachmentResponse(q.getId(), name);
    }

    @Transactional(readOnly = true)
    public Attachment attachment(long quotationId) {
        Quotation q = quotations.findById(quotationId).orElseThrow(() -> new NotFoundException(ENTITY_QUOTATION, quotationId));
        String key = q.getAttachmentPath();
        byte[] content = key == null ? null : storage.get(key).orElse(null);
        if (content == null) {
            throw new NotFoundException("Scanned quotation", q.getQuoteNo());
        }
        MediaType type = key.endsWith(".pdf") ? MediaType.APPLICATION_PDF : key.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return new Attachment(content, type, q.getAttachmentName());
    }

    // ------------------------------------------------------------------ helpers

    QuotationRequest request(long id) {
        return requests.findById(id).orElseThrow(() -> new NotFoundException(ENTITY_REQUEST, id));
    }

    /** Quotations can be added and chosen only while the request is Open / Quotes received. */
    private static void checkOpen(QuotationRequest qr) {
        if (!qr.getStatus().isOpen()) {
            throw new BusinessException(QUOTATION_CHOSEN, "A quotation has already been chosen for " + qr.getQrNo() + ".");
        }
    }

    static void checkNotFuture(String field, LocalDate date, LocalDate today) {
        if (date.isAfter(today)) {
            throw new ValidationException(field, "Date cannot be after today (" + BusinessDates.format(today) + ").");
        }
    }

    private QrDetail detail(QuotationRequest qr) {
        Map<String, Item> items = lookups.items();
        Map<Long, Supplier> suppliers = lookups.suppliers();
        List<Quotation> qs = quotations.findByQrIdOrderByIdAsc(qr.getId());
        List<PurchaseOrder> pos = orders.findByQrIdIn(List.of(qr.getId()));
        PurchaseOrder po = latestPos(pos).get(qr.getId());
        boolean activePo = pos.stream().anyMatch(p -> p.getStatus() != PoStatus.CANCELLED);
        LocalDate today = BusinessDates.today(clock);
        Map<String, String> names = lookups.userNames(qs.stream().map(Quotation::getSelectedBy).toList());

        // Comparison: per requested item the prices of all quotations, lowest flagged when two or more are given.
        Map<Long, List<QuotePrice>> prices = new HashMap<>();
        qs.forEach(q -> prices.put(q.getId(), new ArrayList<>()));
        for (QrItem item : qr.getItems()) {
            List<BigDecimal> row = qs.stream().map(q -> q.priceOf(item.getItemCode())).toList();
            boolean[] lowest = PurchasingRules.lowest(row);
            for (int i = 0; i < qs.size(); i++) {
                BigDecimal p = row.get(i);
                prices.get(qs.get(i).getId()).add(new QuotePrice(item.getItemCode(), p, p == null ? null : Money.times(p, item.getQty()),
                        lowest[i]));
            }
        }
        List<QuoteColumn> columns = qs.stream().map(q -> new QuoteColumn(q.getId(), q.getQuoteNo(),
                PurchasingLookups.link(suppliers.get(q.getSupplierId())), q.getSupplierRef(), q.getQuoteDate(), q.getValidUntil(),
                q.getValidUntil().isBefore(today), q.getDeliveryDays(), q.getNotes(), q.getAttachmentName(), q.getAttachmentPath() != null,
                prices.get(q.getId()), total(qr, q), prices.get(q.getId()).stream().allMatch(p -> p.unitPrice() != null), q.isSelected(),
                q.getSelectionReason(), names.get(q.getSelectedBy()), q.getSelectedOn())).toList();

        Set<Long> quoted = qs.stream().map(Quotation::getSupplierId).collect(Collectors.toSet());
        List<AskedSupplier> asked = qr.getSupplierIds().stream().map(suppliers::get)
                .sorted(Comparator.comparing(Supplier::getCode))
                .map(s -> new AskedSupplier(s.getId(), s.getCode(), s.getName(), quoted.contains(s.getId()))).toList();
        boolean open = qr.getStatus().isOpen();
        boolean chosen = qs.stream().anyMatch(Quotation::isSelected);
        return new QrDetail(qr.getId(), qr.getQrNo(), qr.getRequestDate(), qr.getRequiredBy(), qr.getNote(), qr.getStatus().name(),
                qr.getStatus().label(), lookups.userName(qr.getCreatedBy()), itemQtys(qr, items), asked, columns, poLink(po),
                open && quoted.size() < qr.getSupplierIds().size(), open && !qs.isEmpty(), chosen && !activePo, qr.getVersion());
    }

    /** Σ requested qty × quoted price over the items the supplier quoted. */
    static BigDecimal total(QuotationRequest qr, Quotation q) {
        BigDecimal t = Money.ZERO;
        for (QrItem i : qr.getItems()) {
            BigDecimal p = q.priceOf(i.getItemCode());
            if (p != null) {
                t = t.add(Money.times(p, i.getQty()));
            }
        }
        return t;
    }

    /** Per request: the active PO, else the newest cancelled one. */
    private static Map<Long, PurchaseOrder> latestPos(List<PurchaseOrder> pos) {
        Map<Long, PurchaseOrder> out = new HashMap<>();
        pos.stream().sorted(Comparator.comparing(PurchaseOrder::getId)).forEach(p -> {
            PurchaseOrder current = out.get(p.getQrId());
            if (current == null || current.getStatus() == PoStatus.CANCELLED) {
                out.put(p.getQrId(), p);
            }
        });
        return out;
    }

    static PoLink poLink(PurchaseOrder po) {
        return po == null ? null : new PoLink(po.getId(), po.getPoNo(), po.getStatus().name(), po.getStatus().label());
    }

    private static List<ItemQty> itemQtys(QuotationRequest qr, Map<String, Item> items) {
        return qr.getItems().stream().map(i -> new ItemQty(i.getItemCode(), i.getItemType().name(),
                PurchasingLookups.name(items, i.getItemCode()), i.getQty())).toList();
    }

    private static String itemText(QuotationRequest qr, Map<String, Item> items) {
        return qr.getItems().stream().map(i -> i.getQty() + " × " + PurchasingLookups.name(items, i.getItemCode()))
                .collect(Collectors.joining(", "));
    }

    private static QrStatus qrStatus(String status) {
        try {
            return QrStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationException("status", "Unknown status " + status + ".");
        }
    }

    /** The file name as uploaded, without folders, at most 120 characters. */
    private static String fileName(String original, String fallback) {
        String n = original == null ? "" : original.replaceAll("^.*[\\\\/]", "").replaceAll("[\\p{Cntrl}]", "").trim();
        if (n.isEmpty()) {
            return fallback;
        }
        return n.length() > 120 ? n.substring(n.length() - 120) : n;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        return content.length >= magic.length && Arrays.equals(content, 0, magic.length, magic, 0, magic.length);
    }
}
