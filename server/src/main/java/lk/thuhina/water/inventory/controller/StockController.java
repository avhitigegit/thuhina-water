package lk.thuhina.water.inventory.controller;

import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lk.thuhina.water.common.PageResponse;
import lk.thuhina.water.inventory.dto.StockDtos.AdjustmentRequest;
import lk.thuhina.water.inventory.dto.StockDtos.AdjustmentResponse;
import lk.thuhina.water.inventory.dto.StockDtos.CompanyDamageRequest;
import lk.thuhina.water.inventory.dto.StockDtos.DamageListRow;
import lk.thuhina.water.inventory.dto.StockDtos.LowStockAlert;
import lk.thuhina.water.inventory.dto.StockDtos.MinLevelRequest;
import lk.thuhina.water.inventory.dto.StockDtos.MovementRow;
import lk.thuhina.water.inventory.dto.StockDtos.StockOverview;
import lk.thuhina.water.inventory.service.StockActionService;
import lk.thuhina.water.inventory.service.StockQueryService;
import lk.thuhina.water.security.Permissions;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Stock page (design 7.2 Inventory). Admin only (stock.view / stock.edit). */
@Tag(name = "Stock")
@RestController
@RequestMapping("/stock")
public class StockController {

    private static final String VIEW = "hasAuthority('" + Permissions.STOCK_VIEW + "')";
    private static final String EDIT = "hasAuthority('" + Permissions.STOCK_EDIT + "')";

    private final StockQueryService queries;
    private final StockActionService actions;

    public StockController(StockQueryService queries, StockActionService actions) {
        this.queries = queries;
        this.actions = actions;
    }

    @Operation(summary = "Stock by status per active bottle type, product stock and low-stock alerts")
    @GetMapping
    @PreAuthorize(VIEW)
    public StockOverview overview() {
        return queries.overview();
    }

    @Operation(summary = "Low-stock alerts: filled bottles below the minimum level")
    @GetMapping("/alerts")
    @PreAuthorize(VIEW)
    public List<LowStockAlert> alerts() {
        return queries.alerts();
    }

    @Operation(summary = "Stock movements per document and item, newest first; filter by item code and text")
    @GetMapping("/movements")
    @PreAuthorize(VIEW)
    public PageResponse<MovementRow> movements(@RequestParam(required = false) String item,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return queries.movements(item, q, page, size);
    }

    @Operation(summary = "Damaged bottles, lost bottles and count adjustments, newest first")
    @GetMapping("/damages")
    @PreAuthorize(VIEW)
    public List<DamageListRow> damages() {
        return queries.damagesAndAdjustments();
    }

    @Operation(summary = "Record company damage – the bottles are written off (never below zero)")
    @PostMapping("/damage")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public DamageListRow damage(@Valid @RequestBody CompanyDamageRequest request) {
        return actions.companyDamage(request);
    }

    @Operation(summary = "Stock adjustment: physical count (bottles or products) or lost bottles")
    @PostMapping("/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(EDIT)
    public AdjustmentResponse adjust(@Valid @RequestBody AdjustmentRequest request) {
        return actions.adjust(request);
    }

    @Operation(summary = "Set the minimum filled level of a bottle type (low-stock alert)")
    @PutMapping("/min-levels/{bottleTypeCode}")
    @PreAuthorize(EDIT)
    public Map<String, Integer> setMinLevel(@PathVariable String bottleTypeCode, @Valid @RequestBody MinLevelRequest request) {
        return Map.of("minFilled", actions.setMinLevel(bottleTypeCode, request.minFilled()));
    }
}
