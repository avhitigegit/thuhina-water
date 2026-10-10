package lk.thuhina.water.partners.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.PaymentTerms;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.Product;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.ProductRepository;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.partners.dto.SupplierDtos.SuppliedItem;
import lk.thuhina.water.partners.dto.SupplierDtos.SupplierRequest;
import lk.thuhina.water.partners.dto.SupplierDtos.SupplierResponse;
import lk.thuhina.water.partners.model.Supplier;
import lk.thuhina.water.partners.model.SupplierItem;
import lk.thuhina.water.partners.model.SupplierItem.ItemType;
import lk.thuhina.water.partners.repository.SupplierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suppliers (FR-14): code {@code S01…} on save; name and phone required; at least one supplied item (active empty
 * bottles and products); payment terms from the fixed list. Changes are audited.
 */
@Service
public class SupplierService {

    static final String ENTITY = "Supplier";
    public static final String MSG_ITEMS = "Select at least one item the supplier supplies.";

    private final SupplierRepository repository;
    private final BottleTypeRepository bottleTypes;
    private final ProductRepository products;
    private final NumberingService numbering;
    private final AuditService audit;

    public SupplierService(SupplierRepository repository, BottleTypeRepository bottleTypes, ProductRepository products,
                           NumberingService numbering, AuditService audit) {
        this.repository = repository;
        this.bottleTypes = bottleTypes;
        this.products = products;
        this.numbering = numbering;
        this.audit = audit;
    }

    /** All suppliers by code, or – with {@code item} (B20, P04) – the active suppliers of that item. */
    @Transactional(readOnly = true)
    public List<SupplierResponse> list(String item) {
        ItemNames names = names();
        if (item == null || item.isBlank()) {
            return repository.findAllByOrderByCodeAsc().stream().map(s -> toResponse(s, names)).toList();
        }
        String code = item.trim();
        ItemType type = names.bottles.containsKey(code) ? ItemType.BOTTLE : ItemType.PRODUCT;
        return repository.findActiveSupplying(type, code).stream().map(s -> toResponse(s, names)).toList();
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        ItemNames names = names();
        int terms = checkTerms(request.termsDays());
        checkEmail(request.email());
        Set<SupplierItem.Ref> items = checkItems(request.items(), Set.of(), names);
        Supplier s = new Supplier(numbering.next(SequenceNames.SUPPLIER));
        s.update(request.name().trim(), Texts.blankToNull(request.contact()), request.phone().trim(),
                Texts.blankToNull(request.email()), Texts.blankToNull(request.address()), terms,
                request.active() == null || request.active());
        s.replaceItems(items);
        s = repository.saveAndFlush(s);
        audit.log(AuditAction.CREATE, ENTITY, s.getCode(), s.getName() + " – " + itemText(items, names) + ", "
                + PaymentTerms.label(terms));
        return toResponse(s, names);
    }

    @Transactional
    public SupplierResponse update(long id, SupplierRequest request) {
        Supplier s = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(request.version(), s.getVersion(), ENTITY + " " + s.getCode());
        ItemNames names = names();
        int terms = checkTerms(request.termsDays());
        checkEmail(request.email());
        Set<SupplierItem.Ref> before = new LinkedHashSet<>(s.getItems().stream().map(SupplierItem::ref).toList());
        Set<SupplierItem.Ref> items = checkItems(request.items(), before, names);
        boolean active = request.active() == null ? s.isActive() : request.active();

        List<String> changes = new ArrayList<>();
        String name = request.name().trim();
        if (!name.equals(s.getName())) {
            changes.add("name " + s.getName() + " → " + name);
        }
        if (!items.equals(before)) {
            changes.add("supplies " + itemText(items, names));
        }
        if (terms != s.getTermsDays()) {
            changes.add("terms " + PaymentTerms.label(s.getTermsDays()) + " → " + PaymentTerms.label(terms));
        }
        if (active != s.isActive()) {
            changes.add(active ? "Active" : "Inactive");
        }
        boolean details = !Objects.equals(Texts.blankToNull(request.contact()), s.getContact())
                || !request.phone().trim().equals(s.getPhone())
                || !Objects.equals(Texts.blankToNull(request.email()), s.getEmail())
                || !Objects.equals(Texts.blankToNull(request.address()), s.getAddress());
        if (details) {
            changes.add("contact details updated");
        }
        if (changes.isEmpty()) {
            return toResponse(s, names);
        }
        s.update(name, Texts.blankToNull(request.contact()), request.phone().trim(), Texts.blankToNull(request.email()),
                Texts.blankToNull(request.address()), terms, active);
        s.replaceItems(items);
        repository.saveAndFlush(s);
        audit.log(AuditAction.UPDATE, ENTITY, s.getCode(), s.getName() + ": " + String.join("; ", changes));
        return toResponse(s, names);
    }

    // ------------------------------------------------------------------ rules

    static int checkTerms(Integer days) {
        if (!PaymentTerms.isValid(days)) {
            throw new ValidationException("termsDays", PaymentTerms.MSG_CHOOSE);
        }
        return days;
    }

    static void checkEmail(String email) {
        if (!Texts.isEmailOrEmpty(email)) {
            throw new ValidationException("email", Texts.MSG_EMAIL);
        }
    }

    /**
     * At least one item; each must be a bottle type or product. Newly added items must be active; items the supplier
     * already had may stay even if they were made inactive since.
     */
    private static Set<SupplierItem.Ref> checkItems(List<String> codes, Set<SupplierItem.Ref> existing, ItemNames names) {
        if (codes == null || codes.stream().allMatch(c -> c == null || c.isBlank())) {
            throw new ValidationException("items", MSG_ITEMS);
        }
        Set<SupplierItem.Ref> refs = new LinkedHashSet<>();
        for (String raw : codes) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String code = raw.trim();
            Optional<BottleType> bottle = Optional.ofNullable(names.bottles.get(code));
            Optional<Product> product = Optional.ofNullable(names.products.get(code));
            SupplierItem.Ref ref;
            boolean active;
            if (bottle.isPresent()) {
                ref = new SupplierItem.Ref(ItemType.BOTTLE, code);
                active = bottle.get().isActive();
            } else if (product.isPresent()) {
                ref = new SupplierItem.Ref(ItemType.PRODUCT, code);
                active = product.get().isActive();
            } else {
                throw new ValidationException("items", "Unknown item " + code + ".");
            }
            if (!active && !existing.contains(ref)) {
                throw new ValidationException("items", names.name(ref) + " is not active.");
            }
            refs.add(ref);
        }
        return refs;
    }

    // ------------------------------------------------------------------ names

    /** Bottle types and products by code, for item names ("Empty 20L Bottle", "Manual Bottle Pump"). */
    private record ItemNames(Map<String, BottleType> bottles, Map<String, Product> products) {

        String name(SupplierItem.Ref ref) {
            if (ref.type() == ItemType.BOTTLE) {
                BottleType b = bottles.get(ref.code());
                return b == null ? ref.code() : "Empty " + b.getName();
            }
            Product p = products.get(ref.code());
            return p == null ? ref.code() : p.getName();
        }
    }

    private ItemNames names() {
        Map<String, BottleType> b = new LinkedHashMap<>();
        bottleTypes.findAllByOrderByLitresDesc().forEach(x -> b.put(x.getCode(), x));
        Map<String, Product> p = new LinkedHashMap<>();
        products.findAllByOrderByCodeAsc().forEach(x -> p.put(x.getCode(), x));
        return new ItemNames(b, p);
    }

    private static String itemText(Set<SupplierItem.Ref> items, ItemNames names) {
        return String.join(", ", items.stream().map(names::name).toList());
    }

    /** Items in the order of the pick list: bottles largest first, then products by code. */
    private static SupplierResponse toResponse(Supplier s, ItemNames names) {
        List<String> order = new ArrayList<>(names.bottles.keySet());
        order.addAll(names.products.keySet());
        List<SuppliedItem> items = s.getItems().stream()
                .sorted(Comparator.comparingInt((SupplierItem i) -> order.indexOf(i.getItemCode())))
                .map(i -> new SuppliedItem(i.getItemCode(), i.getItemType().name(), names.name(i.ref())))
                .toList();
        return new SupplierResponse(s.getId(), s.getCode(), s.getName(), s.getContact(), s.getPhone(), s.getEmail(),
                s.getAddress(), s.getTermsDays(), PaymentTerms.label(s.getTermsDays()), items, null, s.isActive(), s.getVersion());
    }
}
