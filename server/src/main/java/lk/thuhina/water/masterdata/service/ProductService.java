package lk.thuhina.water.masterdata.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import lk.thuhina.water.audit.model.AuditAction;
import lk.thuhina.water.audit.service.AuditService;
import lk.thuhina.water.common.Money;
import lk.thuhina.water.common.NotFoundException;
import lk.thuhina.water.common.ValidationException;
import lk.thuhina.water.common.Versions;
import lk.thuhina.water.masterdata.dto.ProductDtos.CreateProductRequest;
import lk.thuhina.water.masterdata.dto.ProductDtos.ProductResponse;
import lk.thuhina.water.masterdata.dto.ProductDtos.UpdateProductRequest;
import lk.thuhina.water.masterdata.model.Product;
import lk.thuhina.water.masterdata.repository.ProductRepository;
import lk.thuhina.water.numbering.service.NumberingService;
import lk.thuhina.water.numbering.service.SequenceNames;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Other products (FR-02): code {@code P01…} on save; name and selling price &gt; 0 required; cost optional; the stock is
 * set only on creation (opening stock) – later only goods receipts, sales and stock adjustments change it.
 */
@Service
public class ProductService {

    static final String ENTITY = "Product";

    private final ProductRepository repository;
    private final NumberingService numbering;
    private final AuditService audit;

    public ProductService(ProductRepository repository, NumberingService numbering, AuditService audit) {
        this.repository = repository;
        this.numbering = numbering;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list() {
        return repository.findAllByOrderByCodeAsc().stream().map(ProductService::toResponse).toList();
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        BigDecimal price = checkPrices(request.sellingPrice(), request.costPrice());
        int opening = request.openingStock() == null ? 0 : request.openingStock();
        if (opening < 0) {
            throw new ValidationException("openingStock", "Opening stock cannot be negative.");
        }
        boolean active = request.active() == null || request.active();
        Product p = repository.saveAndFlush(new Product(numbering.next(SequenceNames.PRODUCT), request.name().trim(), price,
                cost(request.costPrice()), opening, active));
        audit.log(AuditAction.CREATE, ENTITY, p.getCode(), p.getName() + " – " + Money.format(p.getSellingPrice())
                + (opening > 0 ? ", opening stock " + opening : ""));
        return toResponse(p);
    }

    /** Name, prices and active – not the code and not the stock. */
    @Transactional
    public ProductResponse update(long id, UpdateProductRequest request) {
        Product p = repository.findById(id).orElseThrow(() -> new NotFoundException(ENTITY, id));
        Versions.check(request.version(), p.getVersion(), ENTITY + " " + p.getCode());
        BigDecimal price = checkPrices(request.sellingPrice(), request.costPrice());
        BigDecimal cost = cost(request.costPrice());
        String name = request.name().trim();

        List<String> changes = new ArrayList<>();
        if (!name.equals(p.getName())) {
            changes.add("name " + p.getName() + " → " + name);
        }
        if (price.compareTo(p.getSellingPrice()) != 0) {
            changes.add("price " + Money.format(p.getSellingPrice()) + " → " + Money.format(price));
        }
        if (!Objects.equals(cost, p.getCostPrice()) && (cost == null || p.getCostPrice() == null || cost.compareTo(p.getCostPrice()) != 0)) {
            changes.add("cost " + (p.getCostPrice() == null ? "–" : Money.format(p.getCostPrice())) + " → "
                    + (cost == null ? "–" : Money.format(cost)));
        }
        if (request.active() != p.isActive()) {
            changes.add(request.active() ? "Active" : "Inactive");
        }
        if (changes.isEmpty()) {
            return toResponse(p);
        }
        p.update(name, price, cost, request.active());
        repository.saveAndFlush(p);
        audit.log(AuditAction.UPDATE, ENTITY, p.getCode(), p.getName() + ": " + String.join("; ", changes));
        return toResponse(p);
    }

    private static BigDecimal checkPrices(BigDecimal selling, BigDecimal cost) {
        if (!Money.isPositive(selling)) {
            throw new ValidationException("sellingPrice", "Enter a selling price greater than zero.");
        }
        if (cost != null && cost.signum() < 0) {
            throw new ValidationException("costPrice", "Cost price cannot be negative.");
        }
        return Money.of(selling);
    }

    private static BigDecimal cost(BigDecimal cost) {
        return cost == null ? null : Money.of(cost);
    }

    private static ProductResponse toResponse(Product p) {
        return new ProductResponse(p.getId(), p.getCode(), p.getName(), p.getSellingPrice(), p.getCostPrice(),
                p.getStockQty(), p.isActive(), p.getVersion());
    }
}
