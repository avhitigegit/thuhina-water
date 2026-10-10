package lk.thuhina.water.masterdata.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lk.thuhina.water.admin.model.AppUser;
import lk.thuhina.water.admin.repository.AppUserRepository;
import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.BusinessDates;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceBottle;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceCell;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceCustomerType;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceHistoryRow;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceMatrixResponse;
import lk.thuhina.water.masterdata.dto.PriceDtos.PriceValue;
import lk.thuhina.water.masterdata.dto.PriceDtos.SetPriceRequest;
import lk.thuhina.water.masterdata.model.BottleType;
import lk.thuhina.water.masterdata.model.CustomerType;
import lk.thuhina.water.masterdata.model.PriceEntry;
import lk.thuhina.water.masterdata.model.PriceEntry.Kind;
import lk.thuhina.water.masterdata.repository.BottleTypeRepository;
import lk.thuhina.water.masterdata.repository.CustomerTypeRepository;
import lk.thuhina.water.masterdata.repository.PriceEntryRepository;
import lk.thuhina.water.masterdata.rules.MasterDataRules;
import lk.thuhina.water.settings.service.SettingsGroup;
import lk.thuhina.water.settings.service.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

/**
 * Pricing Service (design 5.5): <b>every</b> price on a bill comes from here. Standard water prices per bottle type and
 * customer type, and deposits per bottle type, are dated entries; the price for a date is the latest entry on or
 * before it, so future entries are "scheduled" and apply on their date without any job (FR-07, BR-08).
 * M05 adds the customer's agreed price as step 1 of {@link #waterPrice}.
 */
@Service
public class PricingService {

    public static final String ENTITY = "Price";

    /** Water price for a bill line: the price, whether it is an agreed price, and the standard price. */
    public record WaterPrice(BigDecimal price, boolean agreed, BigDecimal standard) {
    }

    private final PriceEntryRepository entries;
    private final BottleTypeRepository bottleTypes;
    private final CustomerTypeRepository customerTypes;
    private final AppUserRepository users;
    private final SettingsService settings;
    private final AuditService audit;
    private final Clock clock;

    public PricingService(PriceEntryRepository entries, BottleTypeRepository bottleTypes, CustomerTypeRepository customerTypes,
                          AppUserRepository users, SettingsService settings, AuditService audit, Clock clock) {
        this.entries = entries;
        this.bottleTypes = bottleTypes;
        this.customerTypes = customerTypes;
        this.users = users;
        this.settings = settings;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ lookups used by other modules

    /** Standard water price (step 2 of design 5.5), or empty when none is set for that date. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> standardPrice(long customerTypeId, String bottleTypeCode, LocalDate date) {
        return MasterDataRules.inForce(entriesFor(Kind.WATER, bottleTypeCode, customerTypeId), date).map(PriceEntry::getPrice);
    }

    /**
     * Water price for a customer of this type. Until M05 there are no agreed prices, so this is the standard price;
     * M05 changes the first parameter to the customer and checks the agreed price first.
     */
    @Transactional(readOnly = true)
    public Optional<WaterPrice> waterPrice(long customerTypeId, String bottleTypeCode, LocalDate date) {
        return standardPrice(customerTypeId, bottleTypeCode, date).map(p -> new WaterPrice(p, false, p));
    }

    /** Deposit per new bottle – the same for every customer (BR-18). */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> deposit(String bottleTypeCode, LocalDate date) {
        return MasterDataRules.inForce(entriesFor(Kind.DEPOSIT, bottleTypeCode, null), date).map(PriceEntry::getPrice);
    }

    // ------------------------------------------------------------------ Prices & deposits tab

    /** Water grid (active bottle types × active customer types) and deposits, as on {@code date} (default today). */
    @Transactional(readOnly = true)
    public PriceMatrixResponse currentMatrix(LocalDate date) {
        LocalDate day = date != null ? date : today();
        List<BottleType> bottles = bottleTypes.findByActiveTrueOrderByLitresDesc();
        List<CustomerType> types = customerTypes.findByActiveTrueOrderByIdAsc();
        Map<String, List<PriceEntry>> byKey = allByKey();
        Set<String> confirmed = confirmedKeys();

        List<PriceCell> water = new ArrayList<>();
        for (BottleType b : bottles) {
            for (CustomerType t : types) {
                String key = MasterDataRules.priceKey(Kind.WATER, b.getCode(), t.getName());
                water.add(cell(key, Kind.WATER, b.getCode(), t.getId(), byKey.get(internalKey(Kind.WATER, b.getCode(), t.getId())), day, confirmed));
            }
        }
        List<PriceCell> deposits = new ArrayList<>();
        for (BottleType b : bottles) {
            String key = MasterDataRules.priceKey(Kind.DEPOSIT, b.getCode(), null);
            deposits.add(cell(key, Kind.DEPOSIT, b.getCode(), null, byKey.get(internalKey(Kind.DEPOSIT, b.getCode(), null)), day, confirmed));
        }
        return new PriceMatrixResponse(day,
                bottles.stream().map(b -> new PriceBottle(b.getCode(), b.getName())).toList(),
                types.stream().map(t -> new PriceCustomerType(t.getId(), t.getName())).toList(),
                water, deposits);
    }

    /** Every entry, newest effective date first, with Current / Scheduled / Old as of today. */
    @Transactional(readOnly = true)
    public List<PriceHistoryRow> history() {
        List<PriceEntry> all = entries.findAllByOrderByEffectiveFromDescIdDesc();
        Map<String, List<PriceEntry>> byKey = group(all);
        Map<String, String> bottleNames = bottleTypes.findAll().stream()
                .collect(Collectors.toMap(BottleType::getCode, b -> MasterDataRules.litresText(b.getLitres()) + "L"));
        Map<Long, String> typeNames = customerTypes.findAll().stream()
                .collect(Collectors.toMap(CustomerType::getId, CustomerType::getName));
        Map<String, String> userNames = users.findByUsernameIn(all.stream().map(PriceEntry::getCreatedBy).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(AppUser::getUsername, AppUser::getFullName));
        LocalDate today = today();
        return all.stream().map(e -> new PriceHistoryRow(
                e.getId(), e.getKind().name(), e.getBottleTypeCode(), e.getCustomerTypeId(),
                label(e, bottleNames, typeNames), e.getPrice(), e.getEffectiveFrom(), e.getReason(),
                e.getCreatedBy(), userNames.get(e.getCreatedBy()), e.getCreatedAt(),
                MasterDataRules.status(e, byKey.get(internalKey(e)), today).name())).toList();
    }

    /**
     * A price change = a new entry (FR-07): price &gt; 0, effective today or later, reason required. Audited as
     * "Rs. 350.00 → Rs. 375.00 from 01/11/2026 – reason".
     */
    @Transactional
    public PriceHistoryRow setPrice(SetPriceRequest request) {
        Kind kind = request.kind();
        BottleType bottle = bottleTypes.findById(request.bottleTypeCode())
                .filter(BottleType::isActive)
                .orElseThrow(() -> new ValidationException("bottleTypeCode", "Choose an active bottle type."));
        CustomerType type = null;
        if (kind == Kind.WATER) {
            if (request.customerTypeId() == null) {
                throw new ValidationException("customerTypeId", "Choose the customer type.");
            }
            type = customerTypes.findById(request.customerTypeId())
                    .filter(CustomerType::isActive)
                    .orElseThrow(() -> new ValidationException("customerTypeId", "Choose an active customer type."));
        } else if (request.customerTypeId() != null) {
            throw new ValidationException("customerTypeId", "A deposit is the same for every customer type.");
        }
        if (!Money.isPositive(request.price())) {
            throw new ValidationException("price", "Enter a price greater than zero.");
        }
        BigDecimal price = Money.of(request.price());
        if (price.compareTo(request.price()) != 0) {
            throw new ValidationException("price", "Enter the price in rupees and cents (at most 2 decimals).");
        }
        LocalDate from = request.effectiveFrom();
        if (from.isBefore(today())) {
            throw new ValidationException("effectiveFrom", "The effective date cannot be before today.");
        }
        Long typeId = type == null ? null : type.getId();
        List<PriceEntry> same = entriesFor(kind, bottle.getCode(), typeId);
        if (same.stream().anyMatch(e -> e.getEffectiveFrom().equals(from))) {
            throw new ValidationException("effectiveFrom",
                    "There is already a price from " + BusinessDates.format(from) + " for this item. Choose another date.");
        }
        String reason = request.reason().trim();
        Optional<PriceEntry> before = MasterDataRules.inForce(same, from);

        PriceEntry saved = entries.save(new PriceEntry(kind, bottle.getCode(), typeId, price, from, reason));
        String what = kind == Kind.DEPOSIT
                ? MasterDataRules.priceKey(kind, bottle.getCode(), null).replace("|", " / ")
                : MasterDataRules.priceKey(kind, bottle.getCode(), type.getName()).replace("|", " / ");
        audit.log(AuditAction.UPDATE, ENTITY, what,
                before.map(e -> Money.format(e.getPrice())).orElse("none") + " → " + Money.format(price)
                        + " from " + BusinessDates.format(from) + " – " + reason);

        List<PriceEntry> withNew = new ArrayList<>(same);
        withNew.add(saved);
        String bottleShort = MasterDataRules.litresText(bottle.getLitres()) + "L";
        String label = kind == Kind.DEPOSIT ? "Deposit " + bottleShort : "Water " + bottleShort + " / " + type.getName();
        return new PriceHistoryRow(saved.getId(), kind.name(), bottle.getCode(), typeId, label, saved.getPrice(),
                saved.getEffectiveFrom(), saved.getReason(), saved.getCreatedBy(),
                users.findByUsername(saved.getCreatedBy()).map(AppUser::getFullName).orElse(null), saved.getCreatedAt(),
                MasterDataRules.status(saved, withNew, today()).name());
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate today() {
        return BusinessDates.today(clock);
    }

    private List<PriceEntry> entriesFor(Kind kind, String bottleTypeCode, Long customerTypeId) {
        return entries.findByKindAndBottleTypeCodeOrderByEffectiveFromDesc(kind, bottleTypeCode).stream()
                .filter(e -> Objects.equals(e.getCustomerTypeId(), customerTypeId))
                .toList();
    }

    private Map<String, List<PriceEntry>> allByKey() {
        return group(entries.findAllByOrderByEffectiveFromDescIdDesc());
    }

    private static Map<String, List<PriceEntry>> group(List<PriceEntry> list) {
        return list.stream().collect(Collectors.groupingBy(PricingService::internalKey));
    }

    private static String internalKey(PriceEntry e) {
        return internalKey(e.getKind(), e.getBottleTypeCode(), e.getCustomerTypeId());
    }

    private static String internalKey(Kind kind, String bottle, Long typeId) {
        return kind + "|" + bottle + "|" + typeId;
    }

    private static PriceCell cell(String key, Kind kind, String bottle, Long typeId, List<PriceEntry> list, LocalDate day,
                                  Set<String> confirmed) {
        List<PriceEntry> l = list == null ? List.of() : list;
        Function<PriceEntry, PriceValue> value = e -> new PriceValue(e.getId(), e.getPrice(), e.getEffectiveFrom());
        return new PriceCell(key, kind.name(), bottle, typeId,
                MasterDataRules.inForce(l, day).map(value).orElse(null),
                MasterDataRules.nextScheduled(l, day).map(value).orElse(null),
                confirmed.contains(key));
    }

    private static String label(PriceEntry e, Map<String, String> bottleNames, Map<Long, String> typeNames) {
        String b = bottleNames.getOrDefault(e.getBottleTypeCode(), e.getBottleTypeCode());
        return e.getKind() == Kind.DEPOSIT ? "Deposit " + b : "Water " + b + " / " + typeNames.getOrDefault(e.getCustomerTypeId(), "?");
    }

    private Set<String> confirmedKeys() {
        Set<String> keys = new HashSet<>();
        JsonNode list = settings.get(SettingsGroup.PRICES.key()).path("confirmed");
        if (list.isArray()) {
            list.forEach(n -> keys.add(n.asString()));
        }
        return keys;
    }
}
