package lk.thuhina.water.partners.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.PaymentTerms;
import lk.thuhina.water.common.Texts;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.rules.MasterDataRules;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import lk.thuhina.water.partners.dto.FactoryDtos.FactoryChargeHistoryRow;
import lk.thuhina.water.partners.dto.FactoryDtos.FactoryChargeView;
import lk.thuhina.water.partners.dto.FactoryDtos.FactoryRequest;
import lk.thuhina.water.partners.dto.FactoryDtos.FactoryResponse;
import lk.thuhina.water.partners.model.Factory;
import lk.thuhina.water.partners.model.FactoryCharge;
import lk.thuhina.water.partners.repository.FactoryChargeRepository;
import lk.thuhina.water.partners.repository.FactoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Filling factories (FR-22, BR-11): code {@code F01…} on save; name required; payment terms from the fixed list;
 * a charge per bottle &gt; 0 for every active bottle type. A changed charge is saved from today (history kept);
 * batches copy the charge when they are sent (M10), so it applies only to batches sent after saving.
 */
@Service
public class FactoryService {

    static final String ENTITY = "Filling factory";

    private final FactoryRepository repository;
    private final FactoryChargeRepository charges;
    private final BottleTypeRepository bottleTypes;
    private final NumberingService numbering;
    private final AuditService audit;
    private final Clock clock;

    public FactoryService(FactoryRepository repository, FactoryChargeRepository charges, BottleTypeRepository bottleTypes,
                          NumberingService numbering, AuditService audit, Clock clock) {
        this.repository = repository;
        this.charges = charges;
        this.bottleTypes = bottleTypes;
        this.numbering = numbering;
        this.audit = audit;
        this.clock = clock;
    }

    /** The factory's charge per bottle of this type on {@code date} – the newest row on or before the date. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> currentCharge(long factoryId, String bottleTypeCode, LocalDate date) {
        return inForce(charges.findByFactoryIdOrderByEffectiveFromDescIdDesc(factoryId), bottleTypeCode, date)
                .map(FactoryCharge::getCharge);
    }

    @Transactional(readOnly = true)
    public List<FactoryResponse> list() {
        List<BottleType> active = bottleTypes.findByActiveTrueOrderByLitresDesc();
        Map<Long, List<FactoryCharge>> all = charges.findAllByOrderByEffectiveFromDescIdDesc().stream()
                .collect(Collectors.groupingBy(FactoryCharge::getFactoryId));
        LocalDate today = today();
        return repository.findAllByOrderByCodeAsc().stream()
                .map(f -> toResponse(f, all.getOrDefault(f.getId(), List.of()), active, today))
                .toList();
    }

    /** All charges of a factory, newest first; {@code current} marks the one in force today per bottle type. */
    @Transactional(readOnly = true)
    public List<FactoryChargeHistoryRow> chargeHistory(long factoryId) {
        repository.findById(factoryId).orElseThrow(() -> new NotFoundException(ENTITY, factoryId));
        List<FactoryCharge> list = charges.findByFactoryIdOrderByEffectiveFromDescIdDesc(factoryId);
        LocalDate today = today();
        Set<Long> current = new HashSet<>();
        list.stream().map(FactoryCharge::getBottleTypeCode).distinct()
                .forEach(bt -> inForce(list, bt, today).ifPresent(c -> current.add(c.getId())));
        return list.stream().map(c -> new FactoryChargeHistoryRow(c.getId(), c.getBottleTypeCode(), c.getCharge(),
                c.getEffectiveFrom(), c.getCreatedBy(), c.getCreatedAt(), current.contains(c.getId()))).toList();
    }

    @Transactional
    public FactoryResponse create(FactoryRequest request) {
        int terms = SupplierService.checkTerms(request.termsDays());
        SupplierService.checkEmail(request.email());
        List<BottleType> active = bottleTypes.findByActiveTrueOrderByLitresDesc();
        Map<String, BigDecimal> newCharges = checkCharges(request.charges(), active);

        Factory f = new Factory(numbering.next(SequenceNames.FACTORY));
        apply(f, request, terms, request.active() == null || request.active());
        f = repository.saveAndFlush(f);
        LocalDate today = today();
        List<String> text = new ArrayList<>();
        for (BottleType b : active) {
            BigDecimal charge = newCharges.get(b.getCode());
            charges.save(new FactoryCharge(f.getId(), b.getCode(), charge, today));
            text.add(label(b) + " " + Money.format(charge));
        }
        audit.log(AuditAction.CREATE, ENTITY, f.getCode(), f.getName() + " – charges per bottle " + String.join(" · ", text)
                + ", " + PaymentTerms.label(terms));
        return toResponse(f, charges.findByFactoryIdOrderByEffectiveFromDescIdDesc(f.getId()), active, today);
    }

    @Transactional
    public FactoryResponse update(long id, FactoryRequest request) {
        Factory f = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(request.version(), f.getVersion(), ENTITY + " " + f.getCode());
        int terms = SupplierService.checkTerms(request.termsDays());
        SupplierService.checkEmail(request.email());
        List<BottleType> active = bottleTypes.findByActiveTrueOrderByLitresDesc();
        Map<String, BigDecimal> newCharges = checkCharges(request.charges(), active);
        boolean isActive = request.active() == null ? f.isActive() : request.active();
        LocalDate today = today();
        List<FactoryCharge> history = charges.findByFactoryIdOrderByEffectiveFromDescIdDesc(id);

        List<String> changes = new ArrayList<>();
        String name = request.name().trim();
        if (!name.equals(f.getName())) {
            changes.add("name " + f.getName() + " → " + name);
        }
        for (BottleType b : active) {
            BigDecimal now = inForce(history, b.getCode(), today).map(FactoryCharge::getCharge).orElse(null);
            BigDecimal next = newCharges.get(b.getCode());
            if (now == null || now.compareTo(next) != 0) {
                charges.save(new FactoryCharge(id, b.getCode(), next, today));
                changes.add("charge " + label(b) + " " + (now == null ? "none" : Money.format(now)) + " → " + Money.format(next));
            }
        }
        if (terms != f.getTermsDays()) {
            changes.add("terms " + PaymentTerms.label(f.getTermsDays()) + " → " + PaymentTerms.label(terms));
        }
        if (isActive != f.isActive()) {
            changes.add(isActive ? "Active" : "Inactive");
        }
        boolean details = !Objects.equals(Texts.blankToNull(request.address()), f.getAddress())
                || !Objects.equals(Texts.blankToNull(request.contact()), f.getContact())
                || !Objects.equals(Texts.blankToNull(request.phone()), f.getPhone())
                || !Objects.equals(Texts.blankToNull(request.email()), f.getEmail())
                || !Objects.equals(Texts.blankToNull(request.licence()), f.getLicence());
        if (details) {
            changes.add("contact details updated");
        }
        if (!changes.isEmpty()) {
            apply(f, request, terms, isActive);
            repository.saveAndFlush(f);
            audit.log(AuditAction.UPDATE, ENTITY, f.getCode(), f.getName() + ": " + String.join("; ", changes));
        }
        return toResponse(f, charges.findByFactoryIdOrderByEffectiveFromDescIdDesc(id), active, today);
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate today() {
        return BusinessDates.today(clock);
    }

    private static void apply(Factory f, FactoryRequest r, int terms, boolean active) {
        f.update(r.name().trim(), Texts.blankToNull(r.address()), Texts.blankToNull(r.contact()), Texts.blankToNull(r.phone()),
                Texts.blankToNull(r.email()), Texts.blankToNull(r.licence()), terms, active);
    }

    /** A charge &gt; 0 (2 decimals) for every active bottle type; charges for other codes are refused. */
    private static Map<String, BigDecimal> checkCharges(Map<String, BigDecimal> sent, List<BottleType> active) {
        Map<String, BigDecimal> in = sent == null ? Map.of() : sent;
        Set<String> activeCodes = active.stream().map(BottleType::getCode).collect(Collectors.toSet());
        for (String code : in.keySet()) {
            if (!activeCodes.contains(code)) {
                throw new ValidationException("charges." + code, "Charges can only be set for active bottle types.");
            }
        }
        Map<String, Object> errors = new LinkedHashMap<>();
        Map<String, BigDecimal> out = new LinkedHashMap<>();
        for (BottleType b : active) {
            BigDecimal c = in.get(b.getCode());
            if (!Money.isPositive(c) || Money.of(c).compareTo(c) != 0) {
                errors.put("charges." + b.getCode(), "Enter the charge per " + label(b) + " bottle (more than 0).");
            } else {
                out.put(b.getCode(), Money.of(c));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return out;
    }

    private static Optional<FactoryCharge> inForce(List<FactoryCharge> list, String bottleTypeCode, LocalDate date) {
        return list.stream()
                .filter(c -> c.getBottleTypeCode().equals(bottleTypeCode) && !c.getEffectiveFrom().isAfter(date))
                .max(Comparator.comparing(FactoryCharge::getEffectiveFrom).thenComparing(FactoryCharge::getId));
    }

    private static String label(BottleType b) {
        return MasterDataRules.litresText(b.getLitres()) + "L";
    }

    private static FactoryResponse toResponse(Factory f, List<FactoryCharge> history, List<BottleType> active, LocalDate today) {
        List<FactoryChargeView> views = new ArrayList<>();
        for (BottleType b : active) {
            inForce(history, b.getCode(), today).ifPresent(c ->
                    views.add(new FactoryChargeView(b.getCode(), label(b), c.getCharge(), c.getEffectiveFrom())));
        }
        return new FactoryResponse(f.getId(), f.getCode(), f.getName(), f.getAddress(), f.getContact(), f.getPhone(),
                f.getEmail(), f.getLicence(), f.getTermsDays(), PaymentTerms.label(f.getTermsDays()), views, null, null,
                f.isActive(), f.getVersion());
    }
}
